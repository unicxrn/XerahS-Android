package com.xerahs.android.core.common.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A square QR grid as plain booleans - no ZXing types leak out of core:common. */
data class QrMatrix(val size: Int, val cells: List<Boolean>) {
    fun isDark(x: Int, y: Int): Boolean = cells[y * size + x]
}

object QrGenerator {
    /** Encode [text] to a QR matrix. Margin 1 module, medium error correction. */
    fun generate(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bits = QRCodeWriter().encode(text.ifEmpty { " " }, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        val cells = ArrayList<Boolean>(size * size)
        for (y in 0 until size) for (x in 0 until size) cells.add(bits.get(x, y))
        return QrMatrix(size, cells)
    }
}
