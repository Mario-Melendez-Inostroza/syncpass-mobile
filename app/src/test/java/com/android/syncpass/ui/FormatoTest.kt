package com.android.syncpass.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoTest {

    @Test
    fun `decimal usa coma y un decimal`() {
        assertEquals("4,4", Formato.decimal(4.44))
        assertEquals("5,0", Formato.decimal(5.0))
    }

    @Test
    fun `sin valor se muestra un guion largo`() {
        assertEquals("—", Formato.decimal(null))
        assertEquals("—", Formato.fechaHoraMillis(null))
    }

    @Test
    fun `un texto que no es fecha se devuelve tal cual en vez de romper`() {
        assertEquals("no-es-fecha", Formato.fechaHoraIso("no-es-fecha"))
        assertEquals("—", Formato.fechaHoraIso(null))
    }
}
