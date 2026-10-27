package com.android.syncpass.core.qr

/**
 * Formato del QR de un asistente: `ASISTENTE_{id}`, donde id es el identificador del servidor.
 *
 * El caso (§3.2) habla de un "identificador sintético/ficticio": el QR no lleva nombre ni ningún dato
 * personal, solo el id del catálogo. Los beneficiarios no tienen cuenta en la app: el monitor escanea su
 * credencial (que el coordinador puede mostrar o imprimir desde la app) y la marcación queda registrada.
 */
object CodigoQr {

    const val PREFIJO_ASISTENTE = "ASISTENTE_"

    /** Un Long positivo tiene como máximo 19 cifras; se limitan a 18 para descartar basura sin desbordar. */
    private const val MAX_DIGITOS = 18

    fun generarAsistente(id: Long): String {
        require(id > 0) { "El id de un asistente es positivo" }
        return "$PREFIJO_ASISTENTE$id"
    }

    /** Devuelve el id si [contenido] tiene exactamente el formato `ASISTENTE_{id}`; si no, null. */
    fun leerAsistente(contenido: String?): Long? {
        val texto = contenido?.trim() ?: return null
        if (!texto.startsWith(PREFIJO_ASISTENTE)) return null
        val digitos = texto.removePrefix(PREFIJO_ASISTENTE)
        if (digitos.isEmpty() || digitos.length > MAX_DIGITOS || !digitos.all { it in '0'..'9' }) return null
        return digitos.toLongOrNull()?.takeIf { it > 0 }
    }
}
