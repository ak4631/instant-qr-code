package com.akash.qrdrawer.qr

import android.graphics.Bitmap
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders stored payloads as crisp black-on-white QR bitmaps, one pixel per module
 * plus a 4-module quiet zone. Callers scale them up with nearest-neighbour filtering.
 * Results are cached so the drawer and viewer never re-encode on the main path.
 */
object QrRenderer {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun render(payload: String): Bitmap? {
        if (payload.isBlank()) return null
        cache.get(payload)?.let { return it }
        val matrix = runCatching {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }.getOrNull() ?: return null
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix.get(index % matrix.width, index / matrix.width)) BLACK else WHITE
        }
        val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        cache.put(payload, bitmap)
        return bitmap
    }

    private const val QUIET_ZONE_MODULES = 4
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val CACHE_BYTES = 2 * 1024 * 1024
}
