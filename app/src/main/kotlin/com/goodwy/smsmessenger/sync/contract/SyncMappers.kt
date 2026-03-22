package com.goodwy.smsmessenger.sync.contract

import android.provider.Telephony
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Message

fun Conversation.toSyncConversation(): SyncConversation =
    SyncConversation(
        id = threadId.toString(),
        participants = listOfNotNull(phoneNumber.takeIf { it.isNotBlank() }),
        lastMessage = snippet,
        lastUpdatedAtEpochMillis = date.toLong() * 1000L,
        unreadCount = unreadCount,
        // TODO: map real mute state once the phone model stores it explicitly.
        muted = false,
    )

fun Message.toSyncMessage(): SyncMessage =
    SyncMessage(
        id = id.toString(),
        conversationId = threadId.toString(),
        senderId = senderPhoneNumber.ifBlank { "self" },
        body = body,
        timestampEpochMillis = date.toLong() * 1000L,
        status = toSyncStatus(),
        // Local version increments can be replaced by a dedicated mutation version later.
        localVersion = date,
    )

fun Message.toSyncStatus(): SyncMessageStatus {
    if (read) {
        return SyncMessageStatus.READ
    }
    if (isReceivedMessage()) {
        return SyncMessageStatus.DELIVERED
    }

    return when (status) {
        Telephony.Sms.STATUS_PENDING -> SyncMessageStatus.PENDING
        Telephony.Sms.STATUS_FAILED -> SyncMessageStatus.FAILED
        Telephony.Sms.STATUS_COMPLETE -> SyncMessageStatus.SENT
        else -> SyncMessageStatus.SENT
    }
}
