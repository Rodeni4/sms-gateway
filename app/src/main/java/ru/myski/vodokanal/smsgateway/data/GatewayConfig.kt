package ru.myski.vodokanal.smsgateway.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

class GatewayConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val port: Int = 8082

    fun getApiKey(): String {
        var apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey == null) {
            apiKey = UUID.randomUUID().toString()
            prefs.edit {
                putString(KEY_API_KEY, apiKey)
            }
        }
        return apiKey
    }
    
    val apiKey: String
        get() = getApiKey()

    companion object {
        private const val PREFS_NAME = "gateway_prefs"
        private const val KEY_API_KEY = "api_key"
    }
}
