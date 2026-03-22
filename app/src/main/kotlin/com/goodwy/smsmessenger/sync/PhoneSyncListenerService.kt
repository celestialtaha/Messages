package com.goodwy.smsmessenger.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.markThreadMessagesRead
import com.goodwy.smsmessenger.extensions.messagesDB
import com.goodwy.smsmessenger.extensions.updateConversationArchivedStatus
import com.goodwy.smsmessenger.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import com.goodwy.smsmessenger.messaging.sendMessageCompat
import com.goodwy.smsmessenger.sync.contract.ConversationDeltaBatch
import com.goodwy.smsmessenger.sync.contract.MessageDeltaBatch
import com.goodwy.smsmessenger.sync.contract.MutationAck
import com.goodwy.smsmessenger.sync.contract.SyncJsonCodec
import com.goodwy.smsmessenger.sync.contract.SyncPaths
import com.goodwy.smsmessenger.sync.contract.WatchMutation
import com.goodwy.smsmessenger.sync.contract.WatchMutationType
import com.goodwy.smsmessenger.sync.contract.toSyncConversation
import com.goodwy.smsmessenger.sync.contract.toSyncMessage
import com.goodwy.smsmessenger.sync.security.SecureSyncCodec

class PhoneSyncListenerService : WearableListenerService() {
    private val publisher by lazy { PhoneSyncPublisher(this) }
    private val secureCodec by lazy { SecureSyncCodec(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            SyncPaths.KEY_EXCHANGE_REQUEST -> {
                val handled = secureCodec.handleKeyExchangePayload(messageEvent.sourceNodeId, messageEvent.data)
                sendKeyExchangeResponse(messageEvent.sourceNodeId)
                if (handled) {
                    publishBootstrapSnapshot(messageEvent.sourceNodeId)
                }
            }

            SyncPaths.KEY_EXCHANGE_RESPONSE -> {
                val handled = secureCodec.handleKeyExchangePayload(messageEvent.sourceNodeId, messageEvent.data)
                if (handled) {
                    publishBootstrapSnapshot(messageEvent.sourceNodeId)
                }
            }

            SyncPaths.BOOTSTRAP_REQUEST -> {
                sendKeyExchangeResponse(messageEvent.sourceNodeId)
                if (secureCodec.hasPeerKey(messageEvent.sourceNodeId)) {
                    publishBootstrapSnapshot(messageEvent.sourceNodeId)
                } else {
                    sendKeyExchangeRequest(messageEvent.sourceNodeId)
                }
            }

            SyncPaths.MUTATION -> {
                val plainPayload =
                    secureCodec.decrypt(
                        sourceNodeId = messageEvent.sourceNodeId,
                        path = messageEvent.path,
                        envelopePayload = messageEvent.data,
                    ) ?: messageEvent.data
                val mutation = SyncJsonCodec.decodeWatchMutation(plainPayload)
                if (mutation == null) {
                    sendAck(
                        nodeId = messageEvent.sourceNodeId,
                        ack =
                            MutationAck(
                                clientMutationId = "unknown",
                                accepted = false,
                                serverVersion = System.currentTimeMillis(),
                                errorCode = "bad_payload",
                            ),
                    )
                    return
                }

                val ack = handleMutation(mutation)
                sendAck(
                    nodeId = messageEvent.sourceNodeId,
                    ack = ack,
                )
                if (ack.accepted) {
                    publishBootstrapSnapshot(messageEvent.sourceNodeId)
                }
            }
        }
    }

    private fun publishBootstrapSnapshot(nodeId: String) {
        val now = System.currentTimeMillis()
        val mutedThreads = config.customNotifications
        val conversations =
            conversationsDB
                .getNonArchived()
                .sortedByDescending { it.date }
                .take(MAX_CONVERSATIONS)
        val conversationIds = conversations.map { it.threadId }

        val messages =
            conversationIds.flatMap { threadId ->
                messagesDB
                    .getNonRecycledThreadMessages(threadId)
                    .sortedByDescending { it.date }
                    .take(MAX_MESSAGES_PER_CONVERSATION)
            }

        publisher.publishConversations(
            nodeId = nodeId,
            batch = ConversationDeltaBatch(
                cursor = now,
                generatedAtEpochMillis = now,
                conversations =
                    conversations.map { conversation ->
                        conversation.toSyncConversation(
                            muted = mutedThreads.contains(conversation.threadId.toString()),
                        )
                    },
            )
        )
        publisher.publishMessages(
            nodeId = nodeId,
            batch = MessageDeltaBatch(
                cursor = now,
                generatedAtEpochMillis = now,
                messages = messages.map { it.toSyncMessage() },
            )
        )
        Log.d(TAG, "Published bootstrap snapshot")
    }

    private fun handleMutation(mutation: WatchMutation): MutationAck =
        runCatching {
            val threadId = mutation.conversationId.toLong()
            when (mutation.type) {
                WatchMutationType.REPLY -> executeReply(threadId, mutation)
                WatchMutationType.MARK_READ -> markThreadMessagesRead(threadId)
                WatchMutationType.ARCHIVE -> updateConversationArchivedStatus(threadId, archived = true)
                WatchMutationType.MUTE -> setThreadMuted(threadId, muted = true)
                WatchMutationType.UNMUTE -> setThreadMuted(threadId, muted = false)
            }
            MutationAck(
                clientMutationId = mutation.clientMutationId,
                accepted = true,
                serverVersion = System.currentTimeMillis(),
            )
        }.getOrElse { error ->
            Log.w(TAG, "Mutation failed type=${mutation.type}", error)
            MutationAck(
                clientMutationId = mutation.clientMutationId,
                accepted = false,
                serverVersion = System.currentTimeMillis(),
                errorCode = error.javaClass.simpleName.ifBlank { "mutation_failed" },
            )
        }

    private fun executeReply(
        threadId: Long,
        mutation: WatchMutation,
    ) {
        val messageBody = mutation.messageBody?.trim().orEmpty()
        require(messageBody.isNotBlank()) { "empty_body" }
        val conversation =
            conversationsDB.getConversationWithThreadId(threadId)
                ?: error("conversation_not_found")
        val addresses =
            conversation.phoneNumber
                .split(ADDRESS_SEPARATOR)
                .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        require(addresses.isNotEmpty()) { "recipient_missing" }
        sendMessageCompat(
            text = messageBody,
            addresses = addresses,
            subId = null,
            attachments = emptyList(),
        )
    }

    private fun setThreadMuted(
        threadId: Long,
        muted: Boolean,
    ) {
        val channelId = threadId.toString()
        val manager = getSystemService(NotificationManager::class.java)
        if (muted) {
            config.addCustomNotificationsByThreadId(threadId)
            val channel =
                NotificationChannel(
                    channelId,
                    "Thread $threadId",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            manager.createNotificationChannel(channel)
        } else {
            config.removeCustomNotificationsByThreadId(threadId)
            manager.deleteNotificationChannel(channelId)
        }
    }

    private fun sendAck(
        nodeId: String,
        ack: MutationAck,
    ) {
        val plainPayload = SyncJsonCodec.encodeMutationAck(ack)
        val payload =
            secureCodec.encrypt(
                nodeId = nodeId,
                path = SyncPaths.ACK,
                plainPayload = plainPayload,
            ) ?: run {
                sendKeyExchangeResponse(nodeId)
                Log.d(TAG, "Skipping ack until key exchange completes")
                return
            }
        messageClient
            .sendMessage(nodeId, SyncPaths.ACK, payload)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed sending mutation ack", error)
            }
    }

    private fun sendKeyExchangeRequest(nodeId: String) {
        messageClient
            .sendMessage(
                nodeId,
                SyncPaths.KEY_EXCHANGE_REQUEST,
                secureCodec.createKeyExchangePayload(),
            ).addOnFailureListener { error ->
                Log.w(TAG, "Failed sending key exchange request", error)
            }
    }

    private fun sendKeyExchangeResponse(nodeId: String) {
        messageClient
            .sendMessage(
                nodeId,
                SyncPaths.KEY_EXCHANGE_RESPONSE,
                secureCodec.createKeyExchangePayload(),
            ).addOnFailureListener { error ->
                Log.w(TAG, "Failed sending key exchange response", error)
            }
    }

    private companion object {
        private const val TAG = "PhoneSyncListener"
        private const val MAX_CONVERSATIONS = 25
        private const val MAX_MESSAGES_PER_CONVERSATION = 30
    }
}
