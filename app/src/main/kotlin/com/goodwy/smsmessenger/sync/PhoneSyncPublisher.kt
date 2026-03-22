package com.goodwy.smsmessenger.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.goodwy.smsmessenger.sync.contract.ConversationDeltaBatch
import com.goodwy.smsmessenger.sync.contract.MessageDeltaBatch
import com.goodwy.smsmessenger.sync.contract.SyncJsonCodec
import com.goodwy.smsmessenger.sync.contract.SyncPaths
import com.goodwy.smsmessenger.sync.security.SecureSyncCodec

class PhoneSyncPublisher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val secureCodec by lazy { SecureSyncCodec(appContext) }

    fun publishConversations(
        nodeId: String,
        batch: ConversationDeltaBatch,
    ) {
        putPayload(
            nodeId = nodeId,
            path = SyncPaths.CONVERSATIONS,
            plainPayload = SyncJsonCodec.encodeConversationDeltaBatch(batch),
        )
    }

    fun publishMessages(
        nodeId: String,
        batch: MessageDeltaBatch,
    ) {
        putPayload(
            nodeId = nodeId,
            path = SyncPaths.MESSAGES,
            plainPayload = SyncJsonCodec.encodeMessageDeltaBatch(batch),
        )
    }

    private fun putPayload(
        nodeId: String,
        path: String,
        plainPayload: ByteArray,
    ) {
        val payload =
            secureCodec.encrypt(
                nodeId = nodeId,
                path = path,
                plainPayload = plainPayload,
            ) ?: run {
                Log.d(TAG, "Skipping publish until key exchange completes path=$path")
                return
            }
        val request =
            PutDataMapRequest.create(path).run {
                dataMap.putByteArray(PAYLOAD_KEY, payload)
                dataMap.putLong(UPDATED_AT_KEY, System.currentTimeMillis())
                asPutDataRequest().setUrgent()
            }
        dataClient
            .putDataItem(request)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to publish data item path=$path", error)
            }
    }

    private companion object {
        private const val TAG = "PhoneSyncPublisher"
        private const val PAYLOAD_KEY = "payload"
        private const val UPDATED_AT_KEY = "updated_at"
    }
}
