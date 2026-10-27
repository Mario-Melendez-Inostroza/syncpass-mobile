package com.android.syncpass.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Dibuja un código QR (ZXing). Se usa para las credenciales de los asistentes: ASISTENTE_{id}. */
object QrBitmap {

    fun generar(contenido: String, tamanoPx: Int = 512): Bitmap {
        val matriz = QRCodeWriter().encode(
            contenido,
            BarcodeFormat.QR_CODE,
            tamanoPx,
            tamanoPx,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
        )
        val ancho = matriz.width
        val alto = matriz.height
        val pixeles = IntArray(ancho * alto) { i ->
            if (matriz[i % ancho, i / ancho]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
    }
}
