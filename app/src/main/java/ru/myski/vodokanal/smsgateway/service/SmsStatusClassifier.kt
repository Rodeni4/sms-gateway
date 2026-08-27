package ru.myski.vodokanal.smsgateway.service

enum class DeliveryOutcome {
    SUCCESS,
    PENDING,
    FAILURE,
    UNKNOWN
}

object SmsStatusClassifier {
    /**
     * Classifies the raw SMS status code into a DeliveryOutcome.
     * 
     * GSM status codes (3GPP TS 23.040):
     * - 0x00: SUCCESS
     * - 0x01..0x1F: PENDING
     * - 0x20..0x7F: FAILURE
     * 
     * CDMA status codes (3GPP2 C.S0015-B):
     * - Per instructions: 0x20000 (error class 2) is SUCCESS.
     * - Standard Error Classes: 0 (No error), 2 (Temporary), 3 (Permanent).
     */
    fun classifyStatus(rawStatus: Int, isCdma: Boolean = false): DeliveryOutcome {
        return if (isCdma) {
            classifyCdmaStatus(rawStatus)
        } else {
            classifyGsmStatus(rawStatus)
        }
    }

    private fun classifyGsmStatus(status: Int): DeliveryOutcome {
        return when (status) {
            0x00 -> DeliveryOutcome.SUCCESS
            in 0x01..0x1F -> DeliveryOutcome.PENDING
            in 0x20..0x7F -> DeliveryOutcome.FAILURE
            else -> DeliveryOutcome.UNKNOWN
        }
    }

    private fun classifyCdmaStatus(status: Int): DeliveryOutcome {
        // Special case from instructions
        if (status == 0x20000) return DeliveryOutcome.SUCCESS

        val errorClass = (status shr 16) and 0x03
        return when (errorClass) {
            0 -> DeliveryOutcome.SUCCESS // Standard success
            2 -> DeliveryOutcome.PENDING // Standard temporary error
            3 -> DeliveryOutcome.FAILURE // Standard permanent error
            else -> DeliveryOutcome.UNKNOWN
        }
    }
}
