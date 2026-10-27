package com.android.syncpass.core.auth

import com.android.syncpass.testing.jwtDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtClaimsTest {

    @Test
    fun `lee sub, email, nombre y grupos del id token`() {
        val usuario = JwtClaims.usuario(jwtDePrueba(grupos = listOf("COORDINADOR")))!!

        assertEquals("sub-de-prueba", usuario.sub)
        assertEquals("monitor.demo@example.com", usuario.email)
        assertEquals("Monitor Demo", usuario.nombre)
        assertEquals(listOf("COORDINADOR"), usuario.grupos)
    }

    @Test
    fun `COORDINADOR y MONITOR son los roles validos`() {
        assertTrue(JwtClaims.usuario(jwtDePrueba(grupos = listOf("COORDINADOR")))!!.esCoordinador)
        assertTrue(JwtClaims.usuario(jwtDePrueba(grupos = listOf("MONITOR")))!!.esMonitor)
        assertTrue(JwtClaims.usuario(jwtDePrueba(grupos = listOf("MONITOR")))!!.tieneRolValido)
    }

    @Test
    fun `ASISTENTE es un rol valido y solo el asistente puro ve su portal`() {
        val asistente = JwtClaims.usuario(jwtDePrueba(grupos = listOf("ASISTENTE")))!!
        assertTrue(asistente.esAsistente)
        assertTrue(asistente.tieneRolValido)
        assertTrue(asistente.soloAsistente)
        assertFalse(JwtClaims.usuario(jwtDePrueba(grupos = listOf("ASISTENTE", "MONITOR")))!!.soloAsistente)
    }

    @Test
    fun `el rol no distingue mayusculas`() {
        assertTrue(JwtClaims.usuario(jwtDePrueba(grupos = listOf("coordinador")))!!.esCoordinador)
    }

    @Test
    fun `un usuario puede tener los dos grupos`() {
        val usuario = JwtClaims.usuario(jwtDePrueba(grupos = listOf("MONITOR", "COORDINADOR")))!!
        assertTrue(usuario.esCoordinador)
        assertTrue(usuario.esMonitor)
    }

    @Test
    fun `los roles de la app de ejemplo del profesor no son validos`() {
        // profesor / estudiante / administrador vienen de otro proyecto; el caso de SyncPass no los define.
        val usuario = JwtClaims.usuario(jwtDePrueba(grupos = listOf("profesor", "estudiante", "administrador", "USER")))!!
        assertFalse(usuario.tieneRolValido)
    }

    @Test
    fun `sin el claim de grupos no hay rol`() {
        val usuario = JwtClaims.usuario(jwtDePrueba(grupos = null))!!
        assertTrue(usuario.grupos.isEmpty())
        assertFalse(usuario.tieneRolValido)
    }

    @Test
    fun `un claim ausente da null y no rompe`() {
        val usuario = JwtClaims.usuario(jwtDePrueba(email = null, nombre = null))!!
        assertNull(usuario.email)
        assertNull(usuario.nombre)
    }

    @Test
    fun `un texto que no es un JWT da null`() {
        assertNull(JwtClaims.usuario(null))
        assertNull(JwtClaims.usuario(""))
        assertNull(JwtClaims.usuario("undefined"))
        assertNull(JwtClaims.usuario("a.b"))
        assertNull(JwtClaims.usuario("a.%%%.c"))
    }

    @Test
    fun `expiracion en milisegundos`() {
        assertEquals(1_000_000L, JwtClaims.expiracionMillis(jwtDePrueba(exp = 1000)))
        assertNull(JwtClaims.expiracionMillis("basura"))
    }
}
