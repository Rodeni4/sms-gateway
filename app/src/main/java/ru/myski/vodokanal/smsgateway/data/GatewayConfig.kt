package ru.myski.vodokanal.smsgateway.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class GatewayConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val port: Int = 8082

    fun getStoredApiKey(): String {
        var apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey == null) {
            apiKey = generateShortApiKey()
            prefs.edit {
                putString(KEY_API_KEY, apiKey)
            }
        }
        return apiKey
    }

    private fun generateShortApiKey(): String {
        val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .asSequence()
            .map { charPool.random() }
            .joinToString("")
    }

    val apiKey: String
        get() = getStoredApiKey()

    companion object {
        private const val PREFS_NAME = "gateway_prefs"
        private const val KEY_API_KEY = "api_key"
    }
}
