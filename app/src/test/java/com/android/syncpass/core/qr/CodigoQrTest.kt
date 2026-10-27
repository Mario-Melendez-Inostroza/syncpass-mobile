package com.android.syncpass.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CodigoQrTest {

    @Test
    fun `genera el formato ASISTENTE_id`() {
        assertEquals("ASISTENTE_340", CodigoQr.generarAsistente(340))
    }

    @Test
    fun `lee de vuelta lo que genera`() {
        assertEquals(340L, CodigoQr.leerAsistente(CodigoQr.generarAsistente(340)))
        assertEquals(1L, CodigoQr.leerAsistente("ASISTENTE_1"))
    }

    @Test
    fun `tolera espacios y saltos de linea alrededor`() {
        assertEquals(7L, CodigoQr.leerAsistente("  ASISTENTE_7\n"))
    }

    @Test
    fun `rechaza contenido que no es un asistente`() {
        assertNull(CodigoQr.leerAsistente(null))
        assertNull(CodigoQr.leerAsistente(""))
        assertNull(CodigoQr.leerAsistente("EVENTO_5"))
        assertNull(CodigoQr.leerAsistente("https://ejemplo.cl/qr"))
        assertNull(CodigoQr.leerAsistente("asistente_5"), "el prefijo distingue mayúsculas")
    }

    @Test
    fun `rechaza ids que no son un numero positivo`() {
        assertNull(CodigoQr.leerAsistente("ASISTENTE_"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_abc"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_-1"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_+5"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_0"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_12 34"))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_12abc"))
    }

    @Test
    fun `rechaza numeros demasiado largos en vez de desbordar`() {
        assertNull(CodigoQr.leerAsistente("ASISTENTE_" + "9".repeat(19)))
        assertNull(CodigoQr.leerAsistente("ASISTENTE_" + "9".repeat(40)))
    }

    @Test
    fun `no genera un QR para un id no positivo`() {
        assertThrows(IllegalArgumentException::class.java) { CodigoQr.generarAsistente(0) }
        assertThrows(IllegalArgumentException::class.java) { CodigoQr.generarAsistente(-3) }
    }

    private fun assertNull(valor: Any?, mensaje: String) = org.junit.Assert.assertNull(mensaje, valor)
}
