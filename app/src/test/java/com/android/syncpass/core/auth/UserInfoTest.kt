package com.android.syncpass.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInfoTest {

    private fun usuario(vararg grupos: String) = UserInfo("sub-1", "a@example.com", "Ana", grupos.toList())

    @Test
    fun `un usuario sin grupos no tiene rol valido`() {
        assertFalse(usuario().tieneRolValido)
    }

    @Test
    fun `los grupos no distinguen mayusculas`() {
        assertTrue(usuario("asistente").esAsistente)
        assertTrue(usuario("monitor").esMonitor)
        assertTrue(usuario("coordinador").esCoordinador)
    }

    @Test
    fun `solo el asistente puro ve el portal del asistente`() {
        assertTrue(usuario("ASISTENTE").soloAsistente)
        assertFalse(usuario("ASISTENTE", "MONITOR").soloAsistente)
        assertFalse(usuario("COORDINADOR").soloAsistente)
    }
}