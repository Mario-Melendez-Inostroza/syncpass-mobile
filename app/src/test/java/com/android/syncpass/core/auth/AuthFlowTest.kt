package com.android.syncpass.core.auth

import com.android.syncpass.core.network.NetworkFactory
import com.android.syncpass.testing.FakeTokenStorage
import com.android.syncpass.testing.jwtDePrueba
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Inicio de sesión y renovación de token contra un servidor simulado (MockWebServer).
 * Los JSON de respuesta reproducen lo que envía el backend (fs2-api, rama syncpass-integrado).
 */
class AuthFlowTest {

    private lateinit var servidor: MockWebServer

    @Before
    fun abrir() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun cerrar() {
        servidor.shutdown()
    }

    private fun fabrica(storage: FakeTokenStorage) =
        NetworkFactory(servidor.url("/").toString(), storage, depurar = false)

    private fun ok(cuerpo: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(cuerpo)

    private fun error(codigo: Int, cuerpo: String = "") = MockResponse().setResponseCode(codigo)
        .setHeader("Content-Type", "application/json").setBody(cuerpo)

    private fun respuestaLogin(idToken: String, refresh: String = "refresh-1") =
        """{"success":true,"message":"Login exitoso","timestamp":"2026-10-28T15:00:00Z",
            "data":{"accessToken":"acceso-1","idToken":"$idToken","refreshToken":"$refresh","expiresIn":3600}}"""

    private fun repositorio(storage: FakeTokenStorage): AuthRepository {
        val f = fabrica(storage)
        return AuthRepository(f.authApi, f.sesionApi, storage)
    }

    // ------------------------------------------------------------------ login

    @Test
    fun `login correcto guarda los tokens y devuelve el usuario`() = runBlocking {
        val storage = FakeTokenStorage()
        servidor.enqueue(ok(respuestaLogin(jwtDePrueba(grupos = listOf("COORDINADOR")))))

        val resultado = repositorio(storage).iniciarSesion("  docente.demo@example.com ", "Clave-de-prueba-1!")

        assertTrue(resultado.isSuccess)
        assertTrue(resultado.getOrThrow().esCoordinador)
        assertEquals("acceso-1", storage.tokens()?.accessToken)
        assertEquals("refresh-1", storage.tokens()?.refreshToken)
        assertEquals("se guarda el email sin espacios", "docente.demo@example.com", storage.email())
    }

    @Test
    fun `login envia exactamente email y password, sin Authorization`() = runBlocking {
        // Aunque haya una sesión vieja guardada, el login no debe mandar su token.
        val storage = FakeTokenStorage(Tokens("token-viejo", null, null))
        servidor.enqueue(ok(respuestaLogin(jwtDePrueba())))

        repositorio(storage).iniciarSesion("monitor.demo@example.com", "Clave-de-prueba-1!")

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/auth/login", peticion.path)
        assertEquals("{\"email\":\"monitor.demo@example.com\",\"password\":\"Clave-de-prueba-1!\"}", peticion.body.readUtf8())
        assertNull(peticion.getHeader("Authorization"))
    }

    @Test
    fun `credenciales invalidas muestran el mensaje del backend`() = runBlocking {
        val storage = FakeTokenStorage()
        servidor.enqueue(
            error(401, """{"error":"UNAUTHORIZED","mensaje":"Credenciales inválidas","status":401,"detalles":{}}"""),
        )

        val resultado = repositorio(storage).iniciarSesion("x@example.com", "mala")

        assertTrue(resultado.isFailure)
        assertEquals("Credenciales inválidas", resultado.exceptionOrNull()?.message)
        assertNull(storage.tokens())
    }

    @Test
    fun `cuenta con contrasena temporal explica que hay que cambiarla`() = runBlocking {
        servidor.enqueue(
            error(403, """{"error":"CHALLENGE_NOT_SUPPORTED","mensaje":"x","status":403,"detalles":{}}"""),
        )

        val resultado = repositorio(FakeTokenStorage()).iniciarSesion("x@example.com", "Clave-de-prueba-1!")

        assertTrue(resultado.exceptionOrNull()?.message!!.contains("contraseña temporal"))
    }

    @Test
    fun `un usuario sin grupo del caso no entra y no se guarda su sesion`() = runBlocking {
        val storage = FakeTokenStorage()
        servidor.enqueue(ok(respuestaLogin(jwtDePrueba(grupos = listOf("USER", "profesor")))))

        val resultado = repositorio(storage).iniciarSesion("x@example.com", "Clave-de-prueba-1!")

        assertTrue(resultado.isFailure)
        assertTrue(resultado.exceptionOrNull()!!.message!!.contains("COORDINADOR o MONITOR"))
        assertNull("no debe quedar sesión guardada", storage.tokens())
    }

    @Test
    fun `una respuesta sin idToken se considera incompleta`() = runBlocking {
        val storage = FakeTokenStorage()
        servidor.enqueue(ok("""{"success":true,"data":{"accessToken":"a","refreshToken":"r"}}"""))

        val resultado = repositorio(storage).iniciarSesion("x@example.com", "Clave-de-prueba-1!")

        assertTrue(resultado.isFailure)
        assertNull(storage.tokens())
    }

    @Test
    fun `sin red el mensaje es claro y no se pierde nada`() = runBlocking {
        val storage = FakeTokenStorage()
        servidor.shutdown()

        val resultado = repositorio(storage).iniciarSesion("x@example.com", "Clave-de-prueba-1!")

        assertTrue(resultado.exceptionOrNull()!!.message!!.contains("Sin conexión"))
    }

    // ------------------------------------------------------------------ token en las peticiones

    @Test
    fun `las peticiones normales llevan el Bearer`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("acceso-vigente", null, "r"))
        servidor.enqueue(ok("""{"success":true,"data":{"servidorEn":"2026-10-28T15:00:00Z"}}"""))

        fabrica(storage).syncPassApi.catalogo(null)

        assertEquals("Bearer acceso-vigente", servidor.takeRequest().getHeader("Authorization"))
    }

    // ------------------------------------------------------------------ renovación de token

    private val catalogoVacio = """{"success":true,"data":{"servidorEn":"2026-10-28T15:00:00Z"}}"""

    @Test
    fun `un 401 renueva el token y repite la peticion con el nuevo`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("acceso-caducado", "id-viejo", "refresh-1"), "docente.demo@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(
            ok("""{"success":true,"data":{"accessToken":"acceso-nuevo","idToken":"id-nuevo","refreshToken":"refresh-1","expiresIn":3600}}"""),
        )
        servidor.enqueue(ok(catalogoVacio))

        val respuesta = fabrica(storage).syncPassApi.catalogo(null)

        assertTrue(respuesta.success)
        assertEquals(3, servidor.requestCount)

        val primera = servidor.takeRequest()
        assertEquals("Bearer acceso-caducado", primera.getHeader("Authorization"))

        val renovacion = servidor.takeRequest()
        assertEquals("/api/auth/refresh", renovacion.path)
        assertNull("el refresh no lleva Bearer", renovacion.getHeader("Authorization"))
        assertEquals(
            "{\"refreshToken\":\"refresh-1\",\"email\":\"docente.demo@example.com\"}",
            renovacion.body.readUtf8(),
        )

        assertEquals("Bearer acceso-nuevo", servidor.takeRequest().getHeader("Authorization"))
        assertEquals("acceso-nuevo", storage.tokens()?.accessToken)
        assertEquals("id-nuevo", storage.tokens()?.idToken)
        assertEquals("el refresh token se conserva", "refresh-1", storage.tokens()?.refreshToken)
    }

    @Test
    fun `si el refresh no devuelve refreshToken se conserva el que ya habia`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", "refresh-original"), "x@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(ok("""{"success":true,"data":{"accessToken":"nuevo","idToken":"id2"}}"""))
        servidor.enqueue(ok(catalogoVacio))

        fabrica(storage).syncPassApi.catalogo(null)

        assertEquals("refresh-original", storage.tokens()?.refreshToken)
    }

    @Test
    fun `si el servidor rechaza el refresh token la sesion termina y no hay bucle`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", "refresh-vencido"), "x@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(error(401, """{"error":"UNAUTHORIZED","mensaje":"Credenciales inválidas","status":401}"""))

        val fallo = assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.catalogo(null) }
        }

        assertEquals(401, fallo.code())
        assertEquals("una petición y un intento de refresh, nada más", 2, servidor.requestCount)
        assertNull("la sesión se borra", storage.tokens())
        assertFalse(storage.sesionActiva().first())
    }

    @Test
    fun `sin red al renovar se CONSERVA la sesion`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", "refresh-1"), "x@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.catalogo(null) }
        }

        assertNotNull("estar sin cobertura no debe cerrar la sesión", storage.tokens())
        assertEquals("refresh-1", storage.tokens()?.refreshToken)
    }

    @Test
    fun `un error 500 al renovar tambien conserva la sesion`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", "refresh-1"), "x@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(error(500))

        assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.catalogo(null) }
        }

        assertNotNull(storage.tokens())
    }

    @Test
    fun `sin refresh token un 401 cierra la sesion`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", null), "x@example.com")
        servidor.enqueue(error(401))

        assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.catalogo(null) }
        }

        assertNull(storage.tokens())
        assertEquals("ni siquiera intenta renovar", 1, servidor.requestCount)
    }

    @Test
    fun `un 401 repetido tras renovar no entra en bucle`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("viejo", "id", "refresh-1"), "x@example.com")
        servidor.enqueue(error(401))
        servidor.enqueue(ok("""{"success":true,"data":{"accessToken":"nuevo","idToken":"id2","refreshToken":"refresh-1"}}"""))
        servidor.enqueue(error(401))

        assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.catalogo(null) }
        }

        assertEquals(3, servidor.requestCount)
    }

    @Test
    fun `un 403 NO intenta renovar (es falta de permiso, no token caducado)`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("acceso", "id", "refresh-1"), "x@example.com")
        servidor.enqueue(error(403, """{"error":"FORBIDDEN","mensaje":"Forbidden","status":403}"""))

        assertThrows(HttpException::class.java) {
            runBlocking { fabrica(storage).syncPassApi.resumenEvento(1) }
        }

        assertEquals(1, servidor.requestCount)
        assertNotNull(storage.tokens())
    }

    // ------------------------------------------------------------------ cerrar sesión

    @Test
    fun `cerrar sesion avisa al servidor y borra los tokens`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("acceso", "id", "refresh"), "x@example.com")
        servidor.enqueue(ok("""{"success":true,"message":"Logout exitoso"}"""))

        repositorio(storage).cerrarSesion()

        val peticion = servidor.takeRequest()
        assertEquals("/api/auth/logout", peticion.path)
        assertEquals("Bearer acceso", peticion.getHeader("Authorization"))
        assertNull(storage.tokens())
    }

    @Test
    fun `cerrar sesion sin red igualmente cierra en el telefono`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("acceso", "id", "refresh"), "x@example.com")
        servidor.shutdown()

        repositorio(storage).cerrarSesion()

        assertNull(storage.tokens())
    }

    @Test
    fun `usuarioActual lee el rol del id token guardado`() = runBlocking {
        val storage = FakeTokenStorage(Tokens("a", jwtDePrueba(grupos = listOf("COORDINADOR")), "r"))

        assertTrue(repositorio(storage).usuarioActual()!!.esCoordinador)
        assertNull(repositorio(FakeTokenStorage()).usuarioActual())
    }

}
