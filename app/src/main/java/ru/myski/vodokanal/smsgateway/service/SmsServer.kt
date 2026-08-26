package ru.myski.vodokanal.smsgateway.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONException
import org.json.JSONObject
import ru.myski.vodokanal.smsgateway.data.GatewayConfig
import ru.myski.vodokanal.smsgateway.data.MessageStatus
import ru.myski.vodokanal.smsgateway.data.MessageStatusStore
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class SmsServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    private val config = GatewayConfig(context)
    private val statusStore = MessageStatusStore(context)
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val headers = session.headers
        val authHeader = headers["authorization"] ?: headers["Authorization"] ?: headers.entries.find { 
            it.key.equals("authorization", ignoreCase = true) 
        }?.value

        val storedApiKey = config.getApiKey()

        if (authHeader?.trim() != storedApiKey.trim()) {
            Log.w("SmsServer", "Unauthorized access attempt. Received: $authHeader")
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\": \"Unauthorized\"}")
        }

        val uri = session.uri
        if (session.method == Method.GET && uri.startsWith("/status/")) {
            val messageId = uri.substringAfter("/status/")
            val status = statusStore.getStatus(messageId)
            return if (status != null) {
                newFixedLengthResponse(Response.Status.OK, "application/json", status.toJsonObject().toString())
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\": \"Message not found\"}")
            }
        }

        if (session.method != Method.POST) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
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

            val messageId = UUID.randomUUID().toString()
            val initialStatus = MessageStatus(
                messageId = messageId,
                recipient = to,
                status = "QUEUED",
                totalParts = smsManager.divideMessage(message).size
            )
            statusStore.saveStatus(initialStatus)

            sendSms(to, message, messageId)
            
            val responseJson = JSONObject().apply {
                put("success", true)
                put("messageId", messageId)
                put("status", "QUEUED")
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString())
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

    private fun sendSms(to: String, message: String, messageId: String) {
        val parts = smsManager.divideMessage(message)
        val totalParts = parts.size
        
        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent>()
        
        for (i in parts.indices) {
            val sentIntent = Intent(SmsStatusReceiver.ACTION_SMS_SENT).apply {
                `package` = context.packageName
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
            }
            sentIntents.add(PendingIntent.getBroadcast(
                context, 
                messageId.hashCode() + i, 
                sentIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            
            val deliveryIntent = Intent(SmsStatusReceiver.ACTION_SMS_DELIVERED).apply {
                `package` = context.packageName
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
            }
            deliveryIntents.add(PendingIntent.getBroadcast(
                context, 
                messageId.hashCode() + i + 1000000, 
                deliveryIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
        
        smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, deliveryIntents)
    }
}

