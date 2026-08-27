package ru.myski.vodokanal.smsgateway.service

import android.telephony.SmsMessage

enum class DeliveryResult {
    SUCCESS, PENDING, FAILED, UNKNOWN
}

object SmsDeliveryMapper {
    /**
     * Maps the rawStatus from SmsMessage to a classified DeliveryResult.
     * 
     * @param format The message format (e.g., "3gpp" for GSM, "3gpp2" for CDMA)
     * @param rawStatus The status code extracted from the delivery PDU
     */
    fun mapStatus(format: String?, rawStatus: Int): DeliveryResult {
        return when (format) {
            "3gpp" -> mapGsmStatus(rawStatus)
            "3gpp2" -> mapCdmaStatus(rawStatus)
            else -> DeliveryResult.UNKNOWN
        }
    }

    private fun mapGsmStatus(status: Int): DeliveryResult {
        return when (status) {
            0x00 -> DeliveryResult.SUCCESS
            in 0x20..0x3F -> DeliveryResult.PENDING
            in 0x40..0x7F -> DeliveryResult.FAILED
            else -> DeliveryResult.UNKNOWN
        }
    }

    private fun mapCdmaStatus(status: Int): DeliveryResult {
        // errorClass = (rawStatus ushr 24) and 0x03
        // statusCode = (rawStatus ushr 16) and 0x3F
        val errorClass = (status ushr 24) and 0x03
        val statusCode = (status ushr 16) and 0x3F

        return when (errorClass) {
            0 -> if (statusCode == 0x02) DeliveryResult.SUCCESS else DeliveryResult.UNKNOWN
            2 -> DeliveryResult.PENDING
            3 -> DeliveryResult.FAILED
            else -> DeliveryResult.UNKNOWN
        }
    }
}
