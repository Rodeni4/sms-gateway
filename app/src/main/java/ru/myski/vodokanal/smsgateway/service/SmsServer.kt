package ru.myski.vodokanal.smsgateway.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONException
import org.json.JSONObject
import ru.myski.vodokanal.smsgateway.data.GatewayConfig
import java.io.PrintWriter
import java.io.StringWriter

class SmsServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    private val config = GatewayConfig(context)
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
        }

        val headers = session.headers
        val authHeader = headers["authorization"] ?: headers["Authorization"] ?: headers.entries.find { 
            it.key.equals("authorization", ignoreCase = true) 
        }?.value

        val storedApiKey = config.getApiKey()

        if (authHeader?.trim() != storedApiKey.trim()) {
            Log.w("SmsServer", "Unauthorized access attempt. Received: $authHeader")
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\": \"Unauthorized\"}")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("SmsServer", "SEND_SMS permission denied")
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\"error\": \"SEND_SMS permission denied\"}")
        }

        return try {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
            } catch (e: Exception) {
                Log.e("SmsServer", "Error parsing body", e)
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Malformed request body\"}")
            }

            val postData = files["postData"]
            if (postData.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Missing or empty body\"}")
            }

            val json = try {
                JSONObject(postData)
            } catch (_: JSONException) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Invalid JSON format\"}")
            }

            val to = json.optString("to")
            val message = json.optString("message")

            if (to.isNullOrBlank() || message.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Missing 'to' or 'message' fields\"}")
            }

            sendSms(to, message)
            newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}")
        } catch (e: SecurityException) {
            Log.e("SmsServer", "Security exception occurred", e)
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val errorJson = JSONObject().apply {
                put("error", e.message ?: "Security permission error")
                put("type", e.javaClass.simpleName)
                put("stackTrace", sw.toString())
            }
            newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", errorJson.toString())
        } catch (e: Exception) {
            Log.e("SmsServer", "Error handling request", e)
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val errorJson = JSONObject().apply {
                put("error", e.message ?: "Internal server error")
                put("type", e.javaClass.simpleName)
                put("stackTrace", sw.toString())
            }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", errorJson.toString())
        }
    }

    private fun sendSms(to: String, message: String) {
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(to, null, parts, null, null)
    }
}
