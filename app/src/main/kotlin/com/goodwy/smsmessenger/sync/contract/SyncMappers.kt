package com.goodwy.smsmessenger.sync.contract

import android.provider.Telephony
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Message

fun Conversation.toSyncConversation(
    muted: Boolean = false,
): SyncConversation =
    SyncConversation(
        id = threadId.toString(),
        participants = listOfNotNull(phoneNumber.takeIf { it.isNotBlank() }),
        lastMessage = snippet,
        lastUpdatedAtEpochMillis = date.toLong() * 1000L,
        unreadCount = unreadCount,
        muted = muted,
    )

fun Message.toSyncMessage(): SyncMessage =
    SyncMessage(
        id = if (isMMS) "mms:$id" else "sms:$id",
        conversationId = threadId.toString(),
        senderId =
            if (isReceivedMessage()) {
                senderPhoneNumber.ifBlank { "unknown" }
            } else {
                "self"
            },
        body = body,
        timestampEpochMillis = date.toLong() * 1000L,
        status = toSyncStatus(),
        // Local version increments can be replaced by a dedicated mutation version later.
        localVersion = date,
        outgoing = !isReceivedMessage(),
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
