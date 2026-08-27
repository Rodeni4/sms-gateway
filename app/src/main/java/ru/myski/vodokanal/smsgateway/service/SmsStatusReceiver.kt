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
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, -1)

        Log.d("SmsStatusReceiver", "Received action: $action for messageId: $messageId (Part $partIndex/$totalParts)")

        val store = MessageStatusStore(context)
        val status = store.getStatus(messageId) ?: run {
            Log.w("SmsStatusReceiver", "Status not found for messageId: $messageId")
            return
        }

        when (action) {
            ACTION_SMS_SENT -> {
                val rCode = resultCode
                status.androidResultCode = rCode
                
                Log.d("SmsStatusReceiver", "SENT Event: ResultCode=$rCode")
                
                // Only update to SENT if we haven't failed yet
                if ((status.status != SmsStatus.SEND_FAILED) && (status.status != SmsStatus.DELIVERY_FAILED)) {
                    if (rCode == Activity.RESULT_OK) {
                        status.sentParts++
                        if (status.sentParts >= status.totalParts) {
                            status.status = SmsStatus.SENT
                        }
                    } else {
                        status.status = SmsStatus.SEND_FAILED
                        status.errorMessage = "Result code: $rCode"
                    }
                }
                store.saveStatus(status)
            }
            ACTION_SMS_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu")
                val format = intent.getStringExtra("format")
                
                if (pdu != null) {
                    try {
                        val sms = if (format != null) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }
                        
                        val rawStatus = sms.status
                        status.rawStatus = rawStatus
                        
                        val result = SmsDeliveryMapper.mapStatus(format, rawStatus)
                        Log.d("SmsStatusReceiver", "DELIVERED Event: format=$format, pduSize=${pdu.size}, rawStatus=$rawStatus, result=$result")

                        when (result) {
                            DeliveryResult.SUCCESS -> {
                                if (partIndex != -1 && !status.deliveredIndices.contains(partIndex)) {
                                    status.deliveredIndices.add(partIndex)
                                    status.deliveredParts = status.deliveredIndices.size
                                    
                                    if (status.deliveredParts >= status.totalParts) {
                                        status.status = SmsStatus.DELIVERED
                                    }
                                }
                            }
                            DeliveryResult.PENDING -> {
                                // Only set PENDING if not already success/fail
                                if (status.status == SmsStatus.SENT || status.status == SmsStatus.QUEUED) {
                                    status.status = SmsStatus.DELIVERY_PENDING
                                }
                            }
                            DeliveryResult.FAILED -> {
                                status.status = SmsStatus.DELIVERY_FAILED
                                status.errorMessage = "Operator status code: $rawStatus"
                            }
                            DeliveryResult.UNKNOWN -> {
                                if (status.status != SmsStatus.DELIVERED) {
                                    status.status = SmsStatus.DELIVERY_UNKNOWN
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SmsStatusReceiver", "Error parsing PDU", e)
                        status.status = SmsStatus.DELIVERY_UNKNOWN
                    }
                } else {
                    Log.w("SmsStatusReceiver", "DELIVERED Event: PDU is NULL")
                    // Do not update status or parts if PDU is null
                }
                store.saveStatus(status)
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
