package ru.myski.vodokanal.smsgateway.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsStatusClassifierTest {

    @Test
    fun testGsmStatusSuccess() {
        assertEquals(DeliveryOutcome.SUCCESS, SmsStatusClassifier.classifyStatus(0x00, false))
    }

    @Test
    fun testGsmStatusPending() {
        assertEquals(DeliveryOutcome.PENDING, SmsStatusClassifier.classifyStatus(0x01, false))
        assertEquals(DeliveryOutcome.PENDING, SmsStatusClassifier.classifyStatus(0x10, false))
        assertEquals(DeliveryOutcome.PENDING, SmsStatusClassifier.classifyStatus(0x1F, false))
    }

    @Test
    fun testGsmStatusFailure() {
        assertEquals(DeliveryOutcome.FAILURE, SmsStatusClassifier.classifyStatus(0x20, false))
        assertEquals(DeliveryOutcome.FAILURE, SmsStatusClassifier.classifyStatus(0x40, false))
        assertEquals(DeliveryOutcome.FAILURE, SmsStatusClassifier.classifyStatus(0x7F, false))
    }

    @Test
    fun testGsmStatusUnknown() {
        assertEquals(DeliveryOutcome.UNKNOWN, SmsStatusClassifier.classifyStatus(0x80, false))
        assertEquals(DeliveryOutcome.UNKNOWN, SmsStatusClassifier.classifyStatus(-1, false))
    }

    @Test
    fun testCdmaStatusSuccess() {
        // Special case from instructions
        assertEquals(DeliveryOutcome.SUCCESS, SmsStatusClassifier.classifyStatus(0x20000, true))
        // Standard case: Error Class 0 (No error)
        assertEquals(DeliveryOutcome.SUCCESS, SmsStatusClassifier.classifyStatus(0x00000, true))
        assertEquals(DeliveryOutcome.SUCCESS, SmsStatusClassifier.classifyStatus(0x00100, true))
    }

    @Test
    fun testCdmaStatusPending() {
        // Standard case: Error Class 2 (Temporary error)
        // Note: 0x20000 is SUCCESS per special instruction, but other class 2 should be PENDING
        assertEquals(DeliveryOutcome.PENDING, SmsStatusClassifier.classifyStatus(0x20100, true))
        assertEquals(DeliveryOutcome.PENDING, SmsStatusClassifier.classifyStatus(0x20001, true))
    }

    @Test
    fun testCdmaStatusFailure() {
        // Standard case: Error Class 3 (Permanent error)
        assertEquals(DeliveryOutcome.FAILURE, SmsStatusClassifier.classifyStatus(0x30000, true))
        assertEquals(DeliveryOutcome.FAILURE, SmsStatusClassifier.classifyStatus(0x30100, true))
    }

    @Test
    fun testCdmaStatusUnknown() {
        // Error Class 1 is reserved/unknown in some specs
        assertEquals(DeliveryOutcome.UNKNOWN, SmsStatusClassifier.classifyStatus(0x10000, true))
    }
}
