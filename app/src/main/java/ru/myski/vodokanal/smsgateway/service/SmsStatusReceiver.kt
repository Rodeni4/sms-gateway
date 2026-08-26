package ru.myski.vodokanal.smsgateway.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import ru.myski.vodokanal.smsgateway.data.MessageStatusStore
import ru.myski.vodokanal.smsgateway.data.SmsStatus

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        // val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, -1) // Not strictly needed here if we use status.totalParts

        val store = MessageStatusStore(context)
        val status = store.getStatus(messageId) ?: return

        when (action) {
            ACTION_SMS_SENT -> {
                val resultCode = resultCode
                status.androidResultCode = resultCode
                
                if (resultCode == Activity.RESULT_OK) {
                    status.sentParts++
                    // Only update status to SENT if it wasn't already failed
                    if (status.status != SmsStatus.SEND_FAILED) {
                        if (status.sentParts >= status.totalParts) {
                            status.status = SmsStatus.SENT
                        }
                    }
                } else {
                    status.status = SmsStatus.SEND_FAILED
                    status.errorMessage = "Send failed with result code: $resultCode"
                }
                store.saveStatus(status)
                Log.d("SmsStatusReceiver", "Message $messageId part $partIndex sent result: $resultCode")
            }
            ACTION_SMS_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu")
                val format = intent.getStringExtra("format")
                val smsMessage = if (pdu != null) {
                    try {
                        if (format != null) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }
                    } catch (e: Exception) {
                        Log.e("SmsStatusReceiver", "Error parsing SMS PDU", e)
                        null
                    }
                } else null

                if (smsMessage != null) {
                    val rawStatus = smsMessage.status
                    status.rawStatus = rawStatus
                    val outcome = SmsStatusClassifier.classifyStatus(rawStatus, format == "3gpp2")
                    
                    when (outcome) {
                        DeliveryOutcome.SUCCESS -> {
                            status.deliveredParts++
                            // Only update to DELIVERED if all parts are successful
                            if (status.deliveredParts >= status.totalParts) {
                                status.status = SmsStatus.DELIVERED
                            } else {
                                status.status = SmsStatus.DELIVERY_PENDING
                            }
                        }
                        DeliveryOutcome.PENDING -> {
                            // Don't downgrade from DELIVERED or SEND_FAILED
                            if (status.status != SmsStatus.DELIVERED && status.status != SmsStatus.SEND_FAILED) {
                                status.status = SmsStatus.DELIVERY_PENDING
                            }
                        }
                        DeliveryOutcome.FAILURE -> {
                            status.status = SmsStatus.DELIVERY_FAILED
                            status.errorMessage = "Delivery failed with status $rawStatus"
                        }
                        DeliveryOutcome.UNKNOWN -> {
                            if (status.status != SmsStatus.DELIVERED && status.status != SmsStatus.SEND_FAILED) {
                                status.status = SmsStatus.DELIVERY_UNKNOWN
                            }
                        }
                    }
                } else {
                    // If we can't parse the PDU, we still know *something* happened
                    if (status.status != SmsStatus.DELIVERED && status.status != SmsStatus.SEND_FAILED) {
                        status.status = SmsStatus.DELIVERY_UNKNOWN
                        status.errorMessage = "Failed to parse delivery PDU"
                    }
                }

                store.saveStatus(status)
                Log.d("SmsStatusReceiver", "Message $messageId part $partIndex delivery report: ${status.status} (raw: ${status.rawStatus})")
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
