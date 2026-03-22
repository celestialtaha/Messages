package com.goodwy.smsmessenger.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.goodwy.smsmessenger.sync.contract.ConversationDeltaBatch
import com.goodwy.smsmessenger.sync.contract.MessageDeltaBatch
import com.goodwy.smsmessenger.sync.contract.MutationAck
import com.goodwy.smsmessenger.sync.contract.SyncJsonCodec
import com.goodwy.smsmessenger.sync.contract.SyncPaths

class PhoneSyncListenerService : WearableListenerService() {
    private val publisher by lazy { PhoneSyncPublisher(this) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            SyncPaths.BOOTSTRAP_REQUEST -> {
                publishBootstrapSnapshot()
            }

            SyncPaths.MUTATION -> {
                val mutation = SyncJsonCodec.decodeWatchMutation(messageEvent.data)
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

                // TODO: wire mutation handling into real repositories / sender pipeline.
                sendAck(
                    nodeId = messageEvent.sourceNodeId,
                    ack =
                        MutationAck(
                            clientMutationId = mutation.clientMutationId,
                            accepted = true,
                            serverVersion = System.currentTimeMillis(),
                        ),
                )
            }
        }
    }

    private fun publishBootstrapSnapshot() {
        val now = System.currentTimeMillis()
        publisher.publishConversations(
            ConversationDeltaBatch(
                cursor = now,
                generatedAtEpochMillis = now,
                conversations = emptyList(),
            )
        )
        publisher.publishMessages(
            MessageDeltaBatch(
                cursor = now,
                generatedAtEpochMillis = now,
                messages = emptyList(),
            )
        )
        Log.d(TAG, "Published bootstrap snapshot")
    }

    private fun sendAck(
        nodeId: String,
        ack: MutationAck,
    ) {
        Wearable.getMessageClient(this)
            .sendMessage(nodeId, SyncPaths.ACK, SyncJsonCodec.encodeMutationAck(ack))
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed sending mutation ack", error)
            }
    }

    private companion object {
        private const val TAG = "PhoneSyncListener"
    }
}
