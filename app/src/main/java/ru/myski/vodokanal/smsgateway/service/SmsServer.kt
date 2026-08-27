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
import ru.myski.vodokanal.smsgateway.data.SmsStatus
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class SmsServer(
    private val context: Context,
    private val config: GatewayConfig,
) : NanoHTTPD(config.port) {

    private val statusStore = MessageStatusStore(context)
    private val smsManager: SmsManager by lazy {
        context.getSystemService(SmsManager::class.java)!!
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // API Key validation (Supports both Authorization and x-api-key headers)
        val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
        val xApiKey = session.headers["x-api-key"]
        val providedKey = authHeader?.trim() ?: xApiKey?.trim()

        if (config.apiKey.isNotEmpty() && (providedKey != config.apiKey)) {
            Log.w("SmsServer", "Unauthorized access attempt to $uri")
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\": \"Unauthorized\"}")
        }

        // GET /status/{messageId}
        if ((method == Method.GET) && (uri.startsWith("/status/"))) {
            val messageId = uri.substringAfter("/status/")
            val status = statusStore.getStatus(messageId)
            return if (status != null) {
                newFixedLengthResponse(Response.Status.OK, "application/json", status.toJsonObject().toString())
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\": \"Message not found\"}")
            }
        }

        // POST / or POST /send
        if ((method == Method.POST) && (uri == "/" || uri == "/send")) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("SmsServer", "SEND_SMS permission denied")
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\"error\": \"SEND_SMS permission denied\"}")
            }

            return try {
                val map = HashMap<String, String>()
                session.parseBody(map)
                val postData = map["postData"]
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
                val parts = smsManager.divideMessage(message)
                val initialStatus = MessageStatus(
                    messageId = messageId,
                    recipient = to,
                    status = SmsStatus.QUEUED,
                    totalParts = parts.size,
                )
                statusStore.saveStatus(initialStatus)

                sendSms(to, message, messageId)
                
                val responseJson = JSONObject().apply {
                    put("success", true)
                    put("messageId", messageId)
                    put("status", SmsStatus.QUEUED.name)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", responseJson.toString())
            } catch (e: SecurityException) {
                Log.e("SmsServer", "Security exception occurred", e)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"Security exception: ${e.message}\"}")
            } catch (e: Exception) {
                Log.e("SmsServer", "Error serving request", e)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"Internal server error: ${e.message}\"}")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\": \"Not found\"}")
    }

    private fun sendSms(to: String, message: String, messageId: String) {
        val parts = smsManager.divideMessage(message)
        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent>()
        
        val deliveryFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        for (i in parts.indices) {
            // 1. Explicit intent for SENT (IMMUTABLE)
            val sentIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_SENT
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
            }
            sentIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    messageId.hashCode() + i,
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            
            // 2. Explicit intent for DELIVERED (MUTABLE)
            val deliveryIntent = Intent(context, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
            }
            deliveryIntents.add(
                PendingIntent.getBroadcast(
                    context,
                    messageId.hashCode() + i + 1000000,
                    deliveryIntent,
                    deliveryFlags,
                )
            )
        }

        smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, deliveryIntents)
    }
}
