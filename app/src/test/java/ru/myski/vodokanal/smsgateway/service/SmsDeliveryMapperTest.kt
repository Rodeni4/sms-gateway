package ru.myski.vodokanal.smsgateway.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsDeliveryMapperTest {

    @Test
    fun testGsmSuccess() {
        assertEquals(DeliveryResult.SUCCESS, SmsDeliveryMapper.mapStatus("3gpp", 0x00))
    }

    @Test
    fun testGsmPending() {
        assertEquals(DeliveryResult.PENDING, SmsDeliveryMapper.mapStatus("3gpp", 0x20))
        assertEquals(DeliveryResult.PENDING, SmsDeliveryMapper.mapStatus("3gpp", 0x3F))
    }

    @Test
    fun testGsmFailure() {
        assertEquals(DeliveryResult.FAILED, SmsDeliveryMapper.mapStatus("3gpp", 0x40))
        assertEquals(DeliveryResult.FAILED, SmsDeliveryMapper.mapStatus("3gpp", 0x7F))
    }

    @Test
    fun testCdmaSuccess() {
        // errorClass 0 (bits 25-24), statusCode 2 (bits 21-16)
        // 0x00020000 -> status 2 in bits 16-21, 0 in bits 24-25
        val successStatus = (0 shl 24) or (0x02 shl 16)
        assertEquals(DeliveryResult.SUCCESS, SmsDeliveryMapper.mapStatus("3gpp2", successStatus))
    }

    @Test
    fun testCdmaPending() {
        // errorClass 2 (Temporary failure)
        val pendingStatus = (2 shl 24) or (0x01 shl 16)
        assertEquals(DeliveryResult.PENDING, SmsDeliveryMapper.mapStatus("3gpp2", pendingStatus))
    }

    @Test
    fun testCdmaFailure() {
        // errorClass 3 (Permanent failure)
        val failureStatus = (3 shl 24) or (0x01 shl 16)
        assertEquals(DeliveryResult.FAILED, SmsDeliveryMapper.mapStatus("3gpp2", failureStatus))
    }

    @Test
    fun testUnknownFormat() {
        assertEquals(DeliveryResult.UNKNOWN, SmsDeliveryMapper.mapStatus("unknown", 0))
        assertEquals(DeliveryResult.UNKNOWN, SmsDeliveryMapper.mapStatus(null, 0))
    }
}
