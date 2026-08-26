package ru.myski.vodokanal.smsgateway.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import ru.myski.vodokanal.smsgateway.data.MessageStatusStore

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, -1)

        val store = MessageStatusStore(context)
        val status = store.getStatus(messageId) ?: return

        when (action) {
            ACTION_SMS_SENT -> {
                val resultCode = resultCode
                status.androidResultCode = resultCode
                
                if (resultCode == Activity.RESULT_OK) {
                    status.sentParts++
                    if (status.sentParts >= status.totalParts) {
                        status.status = "SENT"
                    }
                } else {
                    status.status = "SEND_FAILED"
                    status.errorMessage = "Result code: $resultCode"
                }
                store.saveStatus(status)
                Log.d("SmsStatusReceiver", "Message $messageId part $partIndex sent: $resultCode")
            }
            ACTION_SMS_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu")
                if (pdu != null) {
                    val format = intent.getStringExtra("format")
                    val sms = if (format != null) {
                        SmsMessage.createFromPdu(pdu, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu)
                    }
                    status.rawStatus = sms.status
                }

                status.deliveredParts++
                if (status.deliveredParts >= status.totalParts) {
                    status.status = "DELIVERED"
                }
                store.saveStatus(status)
                Log.d("SmsStatusReceiver", "Message $messageId part $partIndex delivered")
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "ru.myski.vodokanal.smsgateway.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "ru.myski.vodokanal.smsgateway.ACTION_SMS_DELIVERED"
        
        const val EXTRA_MESSAGE_ID = "messageId"
        const val EXTRA_PART_INDEX = "partIndex"
        const val EXTRA_TOTAL_PARTS = "totalParts"
    }
}
