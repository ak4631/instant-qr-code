package com.akash.qrdrawer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiTest {
    @Test
    fun parsesPayeeAddressNameAndBank() {
        val details = parseUpi("upi://pay?pa=akash.tiwari@okhdfcbank&pn=Akash%20Tiwari&cu=INR")
        assertEquals("akash.tiwari@okhdfcbank", details.upiId)
        assertEquals("Akash Tiwari", details.payeeName)
        assertEquals("HDFC Bank", details.bank)
        assertTrue(details.isUpi)
    }

    @Test
    fun handlesUppercaseSchemeKeysAndPlusEncodedSpaces() {
        val details = parseUpi("UPI://pay?PA=shop@oksbi&PN=My+Shop")
        assertEquals("shop@oksbi", details.upiId)
        assertEquals("My Shop", details.payeeName)
        assertEquals("SBI", details.bank)
    }

    @Test
    fun nonUpiPayloadHasNoFields() {
        val details = parseUpi("https://example.com/pay/123")
        assertFalse(details.isUpi)
        assertEquals(UpiDetails(), details)
    }

    @Test
    fun upiWithoutPayeeAddressIsNotUpi() {
        assertFalse(parseUpi("upi://pay?pn=Nobody").isUpi)
        assertFalse(parseUpi("upi://pay").isUpi)
    }

    @Test
    fun malformedPercentEncodingFallsBackToRawValue() {
        assertEquals("100%", parseUpi("upi://pay?pa=a@ybl&pn=100%").payeeName)
    }

    @Test
    fun unknownHandleHasNoBank() {
        assertEquals("", bankForHandle("me@somethingnew"))
        assertEquals("", bankForHandle("no-handle"))
    }

    @Test
    fun masksUserPartOfUpiId() {
        assertEquals("akash•••@okhdfc", maskUpiId("akashtiwari@okhdfc"))
        assertEquals("98•••@ybl", maskUpiId("9876@ybl"))
        assertEquals("a•••@ybl", maskUpiId("ab@ybl"))
        assertEquals("not-a-vpa", maskUpiId("not-a-vpa"))
        assertEquals("", maskUpiId(""))
    }
}
