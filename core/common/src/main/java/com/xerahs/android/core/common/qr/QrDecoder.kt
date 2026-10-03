package com.xerahs.android.core.common.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrDecoder {
    // Decodes the first QR code in ARGB pixels, or null when none is found.
    fun decode(pixels: IntArray, width: Int, height: Int): String? = try {
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        MultiFormatReader().decode(
            bitmap,
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            )
        ).text
    } catch (e: Exception) {
        null
    }
}
