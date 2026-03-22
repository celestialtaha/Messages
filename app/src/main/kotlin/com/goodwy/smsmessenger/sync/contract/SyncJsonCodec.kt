package com.goodwy.smsmessenger.sync.contract

import org.json.JSONArray
import org.json.JSONObject

object SyncJsonCodec {
    fun encodeConversationDeltaBatch(batch: ConversationDeltaBatch): ByteArray =
        JSONObject()
            .put("schemaVersion", batch.schemaVersion)
            .put("cursor", batch.cursor)
            .put("generatedAtEpochMillis", batch.generatedAtEpochMillis)
            .put("conversations", batch.conversations.toJsonArray { it.toJson() })
            .put("deletedConversationIds", batch.deletedConversationIds.toJsonArray())
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun encodeMessageDeltaBatch(batch: MessageDeltaBatch): ByteArray =
        JSONObject()
            .put("schemaVersion", batch.schemaVersion)
            .put("cursor", batch.cursor)
            .put("generatedAtEpochMillis", batch.generatedAtEpochMillis)
            .put("messages", batch.messages.toJsonArray { it.toJson() })
            .put("deletedMessageIds", batch.deletedMessageIds.toJsonArray())
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun decodeWatchMutation(bytes: ByteArray): WatchMutation? =
        runCatching {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            WatchMutation(
                schemaVersion = json.optInt("schemaVersion", SYNC_SCHEMA_VERSION),
                clientMutationId = json.getString("clientMutationId"),
                type = WatchMutationType.valueOf(json.getString("type")),
                conversationId = json.getString("conversationId"),
                messageBody = json.optString("messageBody").takeIf { it.isNotBlank() },
                createdAtEpochMillis = json.getLong("createdAtEpochMillis"),
            )
        }.getOrNull()

    fun encodeMutationAck(ack: MutationAck): ByteArray =
        JSONObject()
            .put("schemaVersion", ack.schemaVersion)
            .put("clientMutationId", ack.clientMutationId)
            .put("accepted", ack.accepted)
            .put("serverVersion", ack.serverVersion)
            .put("errorCode", ack.errorCode)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun SyncConversation.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("participants", participants.toJsonArray())
            .put("lastMessage", lastMessage)
            .put("lastUpdatedAtEpochMillis", lastUpdatedAtEpochMillis)
            .put("unreadCount", unreadCount)
            .put("muted", muted)

    private fun SyncMessage.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("conversationId", conversationId)
            .put("senderId", senderId)
            .put("body", body)
            .put("timestampEpochMillis", timestampEpochMillis)
            .put("status", status.name)
            .put("localVersion", localVersion)

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(transform(it)) } }

    private fun List<String>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it) } }
}
