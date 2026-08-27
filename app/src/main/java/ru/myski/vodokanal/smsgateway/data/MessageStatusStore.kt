package ru.myski.vodokanal.smsgateway.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

class MessageStatusStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveStatus(status: MessageStatus) {
        status.updatedAt = System.currentTimeMillis()
        prefs.edit {
            putString(status.messageId, status.toJsonObject().toString())
        }
    }

    fun getStatus(messageId: String): MessageStatus? {
        val jsonStr = prefs.getString(messageId, null) ?: return null
        return try {
            MessageStatus.fromJsonObject(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "message_status_prefs"
    }
}
