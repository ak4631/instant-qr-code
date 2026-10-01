package com.akash.qrdrawer.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** On-device QR detection with ZXing (pure Java, no native code or model files). */
object QrImageDecoder {
    private val imageHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.ALSO_INVERTED to true,
    )
    private val cameraHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )

    /** Returns the distinct QR payloads found in the image (may be empty). */
    suspend fun decode(context: Context, uri: Uri): List<String> = withContext(Dispatchers.Default) {
        // Big photos decode poorly at full size and small QRs vanish when shrunk too far, so try a few scales.
        for (maxEdge in intArrayOf(1600, 2800, 900)) {
            val bitmap = loadBitmap(context, uri, maxEdge) ?: return@withContext emptyList()
            val found = decodeBitmap(bitmap)
            bitmap.recycle()
            if (found.isNotEmpty()) return@withContext found
        }
        emptyList()
    }

    /** Single-QR decode of a camera frame's Y (luminance) plane; null when nothing is found. */
    fun decodeLuminance(yPlane: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(yPlane, rowStride, height, 0, 0, width, height, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), cameraHints).text?.takeIf(String::isNotBlank)
        } catch (_: ReaderException) {
            null
        }
    }

    private fun decodeBitmap(bitmap: Bitmap): List<String> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        for (binary in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)))) {
            val payloads = try {
                QRCodeMultiReader().decodeMultiple(binary, imageHints).mapNotNull { it.text?.takeIf(String::isNotBlank) }
            } catch (_: ReaderException) {
                emptyList()
            }
            if (payloads.isNotEmpty()) return payloads.distinct()
        }
        return emptyList()
    }

    private fun loadBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
