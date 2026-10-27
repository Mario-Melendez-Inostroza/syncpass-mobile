package com.android.syncpass.core.network

import com.android.syncpass.core.auth.Tokens
import com.android.syncpass.testing.FakeTokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Contrato con `POST /api/sync/asistentes` de fs2-api: el JSON que la app ENVÍA (solo uuid y tipo) y el que
 * recibe, con el id y el código reales. Si el backend renombra un campo, esta prueba es la que debe fallar.
 */
class AsistentesApiContractTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: SyncPassApi

    @Before
    fun abrir() {
        servidor = MockWebServer().apply { start() }
        api = NetworkFactory(
            servidor.url("/").toString(),
            FakeTokenStorage(Tokens("acceso", "id", "refresh"), "x@example.com"),
            depurar = false,
        ).syncPassApi
    }

    @After
    fun cerrar() {
        servidor.shutdown()
    }

    private fun json(cuerpo: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `POST asistentes - cuerpo exacto sin codigo ni inscripcion y respuesta con id y codigo reales`() = runBlocking {
        servidor.enqueue(
            json(
                """
                {"success":true,"data":{"aceptados":1,"duplicados":1,"rechazados":1,"resultados":[
                  {"indice":0,"uuid":"9f1c7e62-aaaa-4bbb-8ccc-0123456789ab","estado":"ACEPTADO","motivo":null,"id":700,"codigo":"TER-9F1C7E62AAAA","detalle":null},
                  {"indice":1,"uuid":"a7b2c3d4-1111-4222-8333-ba9876543210","estado":"DUPLICADO","motivo":"UUID_YA_REGISTRADO","id":701,"codigo":"TER-A7B2C3D41111","detalle":null},
                  {"indice":2,"uuid":"no-es-un-uuid","estado":"RECHAZADO","motivo":"DATOS_INVALIDOS","id":null,"codigo":null,"detalle":"uuid"}]},
                 "message":"Lote procesado","timestamp":"2026-10-28T15:30:00Z"}
                """.trimIndent(),
            ),
        )

        val respuesta = api.subirAsistentes(
            SincronizarAsistentesRequest(
                listOf(
                    AsistenteSyncDto("9f1c7e62-aaaa-4bbb-8ccc-0123456789ab", "VECINO"),
                    AsistenteSyncDto("a7b2c3d4-1111-4222-8333-ba9876543210", "ESTUDIANTE"),
                ),
            ),
        )

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/sync/asistentes", peticion.path)
        assertEquals("Bearer acceso", peticion.getHeader("Authorization"))
        // Un OBJETO con la lista, y solo uuid y tipo: nada que identifique a una persona.
        assertEquals(
            "{\"registros\":[" +
                "{\"uuid\":\"9f1c7e62-aaaa-4bbb-8ccc-0123456789ab\",\"tipo\":\"VECINO\"}," +
                "{\"uuid\":\"a7b2c3d4-1111-4222-8333-ba9876543210\",\"tipo\":\"ESTUDIANTE\"}]}",
            peticion.body.readUtf8(),
        )

        val lote = respuesta.data!!
        assertEquals(1, lote.aceptados)
        assertEquals(1, lote.duplicados)
        assertEquals(1, lote.rechazados)
        val (aceptado, duplicado, rechazado) = lote.resultados!!
        assertEquals("ACEPTADO", aceptado.estado)
        assertEquals(700L, aceptado.id)
        assertEquals("TER-9F1C7E62AAAA", aceptado.codigo)
        assertEquals("DUPLICADO", duplicado.estado)
        assertEquals("UUID_YA_REGISTRADO", duplicado.motivo)
        assertEquals(701L, duplicado.id)
        assertEquals("RECHAZADO", rechazado.estado)
        assertEquals("DATOS_INVALIDOS", rechazado.motivo)
        assertEquals("uuid", rechazado.detalle)
        assertNull(rechazado.id)
        assertNull(rechazado.codigo)
    }
}
