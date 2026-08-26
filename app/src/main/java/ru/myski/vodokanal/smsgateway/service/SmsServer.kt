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
    private val config: GatewayConfig
) : NanoHTTPD(config.port) {

    private val statusStore = MessageStatusStore(context)
    private val smsManager: SmsManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json", "{\"error\": \"Method not allowed\"}")
        }

        if (session.uri != "/send") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\": \"Not found\"}")
        }

        if (config.apiKey.isNotEmpty()) {
            val apiKey = session.headers["x-api-key"]
            if (apiKey != config.apiKey) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{\"error\": \"Unauthorized\"}")
            }
        }

        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val postData = map["postData"]
            if (postData == null) {
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
                totalParts = parts.size
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

    private fun sendSms(to: String, message: String, messageId: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val status = statusStore.getStatus(messageId)
            status?.let {
                it.status = SmsStatus.SEND_FAILED
                it.errorMessage = "Permission denied"
                statusStore.saveStatus(it)
            }
            return
        }

        val parts = smsManager.divideMessage(message)
        val sentIntents = ArrayList<PendingIntent>()
        val deliveryIntents = ArrayList<PendingIntent>()

        for (i in parts.indices) {
            val sentIntent = Intent(SmsStatusReceiver.ACTION_SMS_SENT).apply {
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
                `package` = context.packageName
            }
            sentIntents.add(PendingIntent.getBroadcast(context, messageId.hashCode() + i, sentIntent, PendingIntent.FLAG_IMMUTABLE))

            val deliveryIntent = Intent(SmsStatusReceiver.ACTION_SMS_DELIVERED).apply {
                putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
                `package` = context.packageName
            }
            deliveryIntents.add(PendingIntent.getBroadcast(context, messageId.hashCode() + i + 1000, deliveryIntent, PendingIntent.FLAG_IMMUTABLE))
        }

        smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, deliveryIntents)
    }
}
