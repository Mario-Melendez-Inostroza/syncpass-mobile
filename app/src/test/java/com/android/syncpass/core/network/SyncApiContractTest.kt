package com.android.syncpass.core.network

import com.android.syncpass.core.auth.Tokens
import com.android.syncpass.testing.FakeTokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contrato con fs2-api (rama syncpass-integrado): los JSON de respuesta son los que genera el backend, con
 * sus null explícitos y el campo `timestamp`, y se comprueba el JSON que la app ENVÍA.
 * Si el backend cambia un nombre de campo, esta prueba es la que debe fallar.
 */
class SyncApiContractTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: SyncPassApi

    @Before
    fun abrir() {
        servidor = MockWebServer().apply { start() }
        val red = NetworkFactory(
            servidor.url("/").toString(),
            FakeTokenStorage(Tokens("acceso", "id", "refresh"), "x@example.com"),
            depurar = false,
        )
        api = red.syncPassApi
    }

    @After
    fun cerrar() {
        servidor.shutdown()
    }

    private fun json(cuerpo: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(cuerpo)

    // ------------------------------------------------------------------ catálogo

    @Test
    fun `GET catalogo - ruta, parametro desde y todos los campos de la respuesta`() = runBlocking {
        servidor.enqueue(
            json(
                """
                {"success":true,"data":{
                  "servidorEn":"2026-10-26T18:00:00.123456Z",
                  "proyectos":[{"id":1,"titulo":"Alfabetización digital","descripcion":null,"areaTematica":"tecnologia",
                     "unidadVecinal":"UV-12","fechaInicio":"2026-08-01","fechaTermino":"2026-12-15","estado":"EN_CURSO",
                     "actualizadoEn":"2026-10-20T12:00:00Z"}],
                  "eventos":[{"id":12,"proyectoId":1,"nombre":"Taller de correo","fechaHoraProgramada":"2026-10-28T15:00:00Z",
                     "cuposMaximos":30,"recinto":"Sede UV-12","latitud":-33.5121,"longitud":-70.6003,"radioMetros":200,
                     "actualizadoEn":"2026-10-20T12:00:00Z"}],
                  "asistentes":[{"id":340,"codigo":"ASI-0001","tipo":"VECINO","estadoInscripcion":"INSCRITO",
                     "actualizadoEn":"2026-10-20T12:00:00Z"}]},
                 "message":"Catálogo sincronizado","timestamp":"2026-10-26T18:00:00Z"}
                """.trimIndent(),
            ),
        )

        val respuesta = api.catalogo("2026-10-20T00:00:00Z")

        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        // Retrofit codifica los ":" como %3A en la URL; el servidor los decodifica, así que se compara el valor decodificado.
        assertEquals("/api/sync/catalogo", peticion.requestUrl!!.encodedPath)
        assertEquals("2026-10-20T00:00:00Z", peticion.requestUrl!!.queryParameter("desde"))

        val datos = respuesta.data!!
        assertEquals("2026-10-26T18:00:00.123456Z", datos.servidorEn)
        val proyecto = datos.proyectos!!.single()
        assertEquals(1L, proyecto.id)
        assertEquals("Alfabetización digital", proyecto.titulo)
        assertNull(proyecto.descripcion)
        assertEquals("2026-08-01", proyecto.fechaInicio)
        assertEquals("EN_CURSO", proyecto.estado)
        val evento = datos.eventos!!.single()
        assertEquals(12L, evento.id)
        assertEquals(1L, evento.proyectoId)
        assertEquals(-33.5121, evento.latitud!!, 0.0)
        assertEquals(200, evento.radioMetros)
        val asistente = datos.asistentes!!.single()
        assertEquals("ASI-0001", asistente.codigo)
        assertEquals("VECINO", asistente.tipo)
    }

    @Test
    fun `GET catalogo sin desde no manda el parametro`() = runBlocking {
        servidor.enqueue(json("""{"success":true,"data":{"servidorEn":"2026-10-26T18:00:00Z","proyectos":[],"eventos":[],"asistentes":[]}}"""))

        api.catalogo(null)

        val sinDesde = servidor.takeRequest().requestUrl!!
        assertEquals("/api/sync/catalogo", sinDesde.encodedPath)
        assertNull(sinDesde.queryParameter("desde"))
    }

    // ------------------------------------------------------------------ asistencias

    @Test
    fun `POST asistencias - cuerpo exacto y respuesta con aceptados, duplicados y rechazados`() = runBlocking {
        servidor.enqueue(
            json(
                """
                {"success":true,"data":{"aceptados":1,"duplicados":1,"rechazados":1,"resultados":[
                  {"indice":0,"uuid":"9f1c","estado":"ACEPTADO","motivo":null,"id":881,"estadoValidacion":"VALIDA","distanciaMetros":37,"detalle":null},
                  {"indice":1,"uuid":"a7b2","estado":"DUPLICADO","motivo":"UUID_YA_REGISTRADO","id":455,"estadoValidacion":null,"distanciaMetros":null,"detalle":null},
                  {"indice":2,"uuid":"c3d4","estado":"RECHAZADO","motivo":"DATOS_INVALIDOS","id":null,"estadoValidacion":null,"distanciaMetros":null,"detalle":"latitud"}]},
                 "message":"Lote procesado","timestamp":"2026-10-28T15:30:00Z"}
                """.trimIndent(),
            ),
        )

        val respuesta = api.subirAsistencias(
            SincronizarAsistenciasRequest(
                listOf(
                    AsistenciaSyncDto("9f1c", 12, 340, "2026-10-28T15:04:11Z", -33.5122, -70.6001),
                    AsistenciaSyncDto("a7b2", 12, 341, "2026-10-28T15:05:02Z"),
                ),
            ),
        )

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/sync/asistencias", peticion.path)
        assertEquals("Bearer acceso", peticion.getHeader("Authorization"))
        // Es un OBJETO con la lista dentro (no una lista suelta), y sin coordenadas el campo no se envía.
        assertEquals(
            "{\"registros\":[" +
                "{\"uuid\":\"9f1c\",\"eventoId\":12,\"asistenteId\":340,\"marcadoEn\":\"2026-10-28T15:04:11Z\",\"latitud\":-33.5122,\"longitud\":-70.6001}," +
                "{\"uuid\":\"a7b2\",\"eventoId\":12,\"asistenteId\":341,\"marcadoEn\":\"2026-10-28T15:05:02Z\"}]}",
            peticion.body.readUtf8(),
        )

        val lote = respuesta.data!!
        assertEquals(1, lote.aceptados)
        assertEquals(1, lote.duplicados)
        assertEquals(1, lote.rechazados)
        val (aceptado, duplicado, rechazado) = lote.resultados!!
        assertEquals("ACEPTADO", aceptado.estado)
        assertEquals(881L, aceptado.id)
        assertEquals("VALIDA", aceptado.estadoValidacion)
        assertEquals(37, aceptado.distanciaMetros)
        assertEquals("DUPLICADO", duplicado.estado)
        assertEquals("UUID_YA_REGISTRADO", duplicado.motivo)
        assertEquals("RECHAZADO", rechazado.estado)
        assertEquals("DATOS_INVALIDOS", rechazado.motivo)
        assertEquals("latitud", rechazado.detalle)
        assertEquals(2, rechazado.indice)
    }

    @Test
    fun `la peticion de asistencias no incluye ningun campo del usuario que registra`() = runBlocking {
        servidor.enqueue(json("""{"success":true,"data":{"resultados":[]}}"""))

        api.subirAsistencias(SincronizarAsistenciasRequest(listOf(AsistenciaSyncDto("u", 1, 2, "2026-10-28T15:04:11Z"))))

        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue("el servidor lo toma del token: $cuerpo", !cuerpo.contains("registradoPor"))
        assertTrue("el servidor lo toma del token: $cuerpo", !cuerpo.contains("sub"))
        assertTrue("el servidor lo toma del token: $cuerpo", !cuerpo.contains("usuario"))
    }

    // ------------------------------------------------------------------ encuestas

    @Test
    fun `POST encuestas - cuerpo exacto y respuesta`() = runBlocking {
        servidor.enqueue(
            json(
                """
                {"success":true,"data":{"aceptados":0,"duplicados":0,"rechazados":1,"resultados":[
                  {"indice":0,"uuid":"e51a","estado":"RECHAZADO","motivo":"ASISTENCIA_NO_ENCONTRADA","id":null,
                   "estadoValidacion":null,"distanciaMetros":null,"detalle":null}]},"message":"Lote procesado"}
                """.trimIndent(),
            ),
        )

        val respuesta = api.subirEncuestas(
            SincronizarEncuestasRequest(
                listOf(EncuestaSyncDto("e51a", "9f1c", 5, 4, "Muy útil", "2026-10-28T16:10:00Z")),
            ),
        )

        val peticion = servidor.takeRequest()
        assertEquals("/api/sync/encuestas", peticion.path)
        assertEquals(
            "{\"registros\":[{\"uuid\":\"e51a\",\"asistenciaUuid\":\"9f1c\",\"satisfaccion\":5,\"utilidad\":4," +
                "\"comentarios\":\"Muy útil\",\"enviadaEn\":\"2026-10-28T16:10:00Z\"}]}",
            peticion.body.readUtf8(),
        )
        assertEquals("ASISTENCIA_NO_ENCONTRADA", respuesta.data!!.resultados!!.single().motivo)
    }

    // ------------------------------------------------------------------ quórum

    @Test
    fun `GET resumen - ruta y campos`() = runBlocking {
        servidor.enqueue(
            json(
                """
                {"success":true,"data":{"eventoId":12,"nombre":"Taller","cuposMaximos":30,"totalMarcados":18,
                 "validas":16,"fueraDeRadio":1,"sinUbicacion":1,"encuestasRecibidas":14,
                 "satisfaccionPromedio":4.4,"utilidadPromedio":4.1,
                 "primeraMarcacion":"2026-10-28T15:01:00Z","ultimaMarcacion":"2026-10-28T15:42:00Z"},
                 "message":"Resumen del evento"}
                """.trimIndent(),
            ),
        )

        val resumen = api.resumenEvento(12).data!!

        assertEquals("/api/eventos/12/resumen", servidor.takeRequest().path)
        assertEquals(18L, resumen.totalMarcados)
        assertEquals(16L, resumen.validas)
        assertEquals(1L, resumen.fueraDeRadio)
        assertEquals(1L, resumen.sinUbicacion)
        assertEquals(14L, resumen.encuestasRecibidas)
        assertEquals(4.4, resumen.satisfaccionPromedio!!, 0.0)
        assertEquals("2026-10-28T15:42:00Z", resumen.ultimaMarcacion)
    }

    @Test
    fun `un evento sin encuestas trae promedios null y no rompe`() = runBlocking {
        servidor.enqueue(
            json("""{"success":true,"data":{"eventoId":1,"nombre":"X","totalMarcados":0,"encuestasRecibidas":0,"satisfaccionPromedio":null,"utilidadPromedio":null,"primeraMarcacion":null,"ultimaMarcacion":null}}"""),
        )

        val resumen = api.resumenEvento(1).data!!

        assertNull(resumen.satisfaccionPromedio)
        assertNull(resumen.primeraMarcacion)
        assertEquals(0L, resumen.totalMarcados)
    }
}
