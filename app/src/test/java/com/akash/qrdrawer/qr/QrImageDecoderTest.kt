package com.akash.qrdrawer.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrImageDecoderTest {
    @Test
    fun decodesQrFromCameraLuminancePlaneWithRowPadding() {
        val payload = "upi://pay?pa=akash@okhdfcbank&pn=Akash"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 400, 400)
        // Camera planes often have rowStride > width; fill the padding with noise-free grey.
        val rowStride = matrix.width + 32
        val plane = ByteArray(rowStride * matrix.height) { 0x80.toByte() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                plane[y * rowStride + x] = if (matrix.get(x, y)) 0 else 0xFF.toByte()
            }
        }
        assertEquals(payload, QrImageDecoder.decodeLuminance(plane, rowStride, matrix.width, matrix.height))
    }

    @Test
    fun returnsNullForBlankFrame() {
        val plane = ByteArray(320 * 240) { 0xFF.toByte() }
        assertNull(QrImageDecoder.decodeLuminance(plane, 320, 320, 240))
    }
}
