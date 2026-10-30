package com.android.syncpass.ui.gestion

import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.AsistenteAltaRequest
import com.android.syncpass.core.network.AsistenteEdicionRequest
import com.android.syncpass.core.network.AsistenteFilaDto
import com.android.syncpass.core.network.CatalogoDto
import com.android.syncpass.core.network.EventoDto
import com.android.syncpass.core.network.EventoRequest
import com.android.syncpass.core.network.GestionApi
import com.android.syncpass.core.network.ProyectoDto
import com.android.syncpass.core.network.ProyectoRequest
import com.android.syncpass.testing.FakeSyncApi
import com.android.syncpass.testing.MainDispatcherRule
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.ZoneId
import java.time.ZoneOffset

/** Escrituras que el servidor aceptaría; cada prueba cambia lo que necesita. */
private class FakeGestionApi : GestionApi {
    val llamadas = mutableListOf<String>()
    /** Una excepción nueva por llamada: el cuerpo de un error HTTP solo se puede leer una vez. */
    var falla: (() -> Throwable)? = null
    var filas = listOf(AsistenteFilaDto(1, "INS-000001", "VECINO", "INSCRITO", 0, false))

    private fun <T> ok(dato: T?): ApiResponse<T> {
        falla?.let { throw it() }
        return ApiResponse(success = true, data = dato)
    }

    override suspend fun crearProyecto(request: ProyectoRequest): ApiResponse<ProyectoDto> {
        llamadas += "crearProyecto:${request.titulo}"
        return ok(ProyectoDto(id = 9, titulo = request.titulo))
    }

    override suspend fun editarProyecto(id: Long, request: ProyectoRequest): ApiResponse<ProyectoDto> {
        llamadas += "editarProyecto:$id:${request.titulo}"
        return ok(ProyectoDto(id = id, titulo = request.titulo))
    }

    override suspend fun eliminarProyecto(id: Long): ApiResponse<Any> {
        llamadas += "eliminarProyecto:$id"
        return ok(null)
    }

    override suspend fun crearEvento(request: EventoRequest): ApiResponse<EventoDto> {
        llamadas += "crearEvento:${request.nombre}"
        return ok(EventoDto(id = 5, nombre = request.nombre))
    }

    override suspend fun editarEvento(id: Long, request: EventoRequest): ApiResponse<EventoDto> {
        llamadas += "editarEvento:$id"
        return ok(EventoDto(id = id, nombre = request.nombre))
    }

    override suspend fun eliminarEvento(id: Long): ApiResponse<Any> {
        llamadas += "eliminarEvento:$id"
        return ok(null)
    }

    override suspend fun asistentes(): ApiResponse<List<AsistenteFilaDto>> = ok(filas)

    override suspend fun crearAsistentes(request: AsistenteAltaRequest): ApiResponse<List<AsistenteFilaDto>> {
        llamadas += "crearAsistentes:${request.tipo}:${request.cantidad}"
        return ok(emptyList())
    }

    override suspend fun editarAsistente(id: Long, request: AsistenteEdicionRequest): ApiResponse<AsistenteFilaDto> {
        llamadas += "editarAsistente:$id:${request.tipo}:${request.estadoInscripcion}"
        return ok(AsistenteFilaDto(id = id))
    }

    override suspend fun eliminarAsistente(id: Long): ApiResponse<Any> {
        llamadas += "eliminarAsistente:$id"
        return ok(null)
    }
}

private fun catalogoCon(vararg proyectos: ProyectoDto, eventos: List<EventoDto> = emptyList()) =
    FakeSyncApi().apply { alCatalogo = { ApiResponse(true, CatalogoDto(proyectos = proyectos.toList(), eventos = eventos)) } }

private fun conflicto(mensaje: String) = HttpException(
    Response.error<Any>(409, """{"error":"CONFLICT","mensaje":"$mensaje","status":409}""".toResponseBody()),
)

class GestionFormulariosTest {

    private val proyectoValido = FormProyecto(
        titulo = " Alfabetización digital ", areaTematica = "Tecnología", unidadVecinal = "Unidad 4",
        fechaInicio = "2026-11-01", fechaTermino = "2026-12-01",
    )

    @Test
    fun `un proyecto valido se limpia y se convierte en solicitud`() {
        val req = (validarProyecto(proyectoValido) as Validacion.Ok).valor
        assertEquals("Alfabetización digital", req.titulo)
        assertNull(req.descripcion)
        assertEquals("2026-12-01", req.fechaTermino)
        assertEquals("PLANIFICADO", req.estado)
    }

    @Test
    fun `un proyecto sin titulo o con fechas incoherentes se rechaza antes de enviarlo`() {
        assertTrue((validarProyecto(proyectoValido.copy(titulo = " ")) as Validacion.Error).mensaje.contains("título"))
        assertTrue((validarProyecto(proyectoValido.copy(fechaInicio = "01/11/2026")) as Validacion.Error).mensaje.contains("AAAA-MM-DD"))
        assertTrue(
            (validarProyecto(proyectoValido.copy(fechaTermino = "2026-10-01")) as Validacion.Error).mensaje.contains("anterior"),
        )
    }

    @Test
    fun `la fecha de termino es opcional`() {
        val req = (validarProyecto(proyectoValido.copy(fechaTermino = "")) as Validacion.Ok).valor
        assertNull(req.fechaTermino)
    }

    private val eventoValido = FormEvento(
        proyectoId = 3, nombre = "Taller de correo", fechaHora = "05/11/2026 15:00", cuposMaximos = "30",
        quorumMinimo = "10", latitud = "-33,45", longitud = "-70.66", radioMetros = "200",
    )

    @Test
    fun `la hora del evento se convierte de la zona del telefono a UTC`() {
        val req = (validarEvento(eventoValido, ZoneId.of("America/Santiago")) as Validacion.Ok).valor
        // En noviembre Chile está en horario de verano (UTC-3): 15:00 locales son las 18:00 UTC.
        assertEquals("2026-11-05T18:00:00Z", req.fechaHoraProgramada)
        assertEquals(-33.45, req.latitud!!, 0.0)
        assertEquals(30, req.cuposMaximos)
    }

    @Test
    fun `un evento con datos incoherentes se rechaza con el motivo`() {
        fun motivo(f: FormEvento) = (validarEvento(f, ZoneOffset.UTC) as Validacion.Error).mensaje
        assertTrue(motivo(eventoValido.copy(proyectoId = null)).contains("proyecto"))
        assertTrue(motivo(eventoValido.copy(fechaHora = "2026-11-05")).contains("DD/MM/AAAA"))
        assertTrue(motivo(eventoValido.copy(quorumMinimo = "40")).contains("quórum"))
        assertTrue(motivo(eventoValido.copy(longitud = "")).contains("juntas"))
        assertTrue(motivo(eventoValido.copy(latitud = "95")).contains("-90"))
        assertTrue(motivo(eventoValido.copy(cuposMaximos = "cero")).contains("entero"))
        assertTrue(motivo(eventoValido.copy(radioMetros = "0")).contains("1 metro"))
    }

    @Test
    fun `al editar, la hora del servidor vuelve al formato del formulario`() {
        assertEquals("05/11/2026 15:00", fechaHoraParaFormulario("2026-11-05T18:00:00Z", ZoneId.of("America/Santiago")))
        assertEquals("", fechaHoraParaFormulario(null))
    }
}

class MantenedoresViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val reqProyecto = ProyectoRequest("Nuevo", null, "Tecnología", "U1", "2026-11-01", null, "PLANIFICADO")

    @Test
    fun `los proyectos se cargan del catalogo del servidor, el mas reciente primero`() {
        val sync = catalogoCon(
            ProyectoDto(id = 1, titulo = "Viejo", fechaInicio = "2026-01-01"),
            ProyectoDto(id = 2, titulo = "Nuevo", fechaInicio = "2026-09-01"),
        )

        val vm = ProyectosViewModel(sync, FakeGestionApi())

        assertFalse(vm.ui.value.cargando)
        assertEquals(listOf("Nuevo", "Viejo"), vm.ui.value.items.map { it.titulo })
        assertEquals(listOf<String?>(null), sync.desdeRecibidos)
    }

    @Test
    fun `crear un proyecto lo envia, recarga la lista y cierra el formulario`() {
        val sync = catalogoCon(ProyectoDto(id = 1, titulo = "Uno"))
        val api = FakeGestionApi()
        val vm = ProyectosViewModel(sync, api)
        var cerrado = false

        vm.crear(reqProyecto) { cerrado = true }

        assertEquals(listOf("crearProyecto:Nuevo"), api.llamadas)
        assertEquals(2, sync.llamadas.count { it == "catalogo" })
        assertEquals("Proyecto creado.", vm.ui.value.aviso)
        assertFalse(vm.ui.value.trabajando)
        assertTrue(cerrado)
    }

    @Test
    fun `editar y eliminar un proyecto llaman a su id`() {
        val api = FakeGestionApi()
        val vm = ProyectosViewModel(catalogoCon(), api)

        vm.editar(4, reqProyecto) {}
        vm.eliminar(4)

        assertEquals(listOf("editarProyecto:4:Nuevo", "eliminarProyecto:4"), api.llamadas)
        assertEquals("Proyecto eliminado.", vm.ui.value.aviso)
    }

    @Test
    fun `un 409 muestra el motivo del servidor y no cierra el formulario`() {
        val api = FakeGestionApi().apply { falla = { conflicto("No se puede eliminar un proyecto con eventos.") } }
        val vm = ProyectosViewModel(catalogoCon(ProyectoDto(id = 1, titulo = "Uno")), api)
        var cerrado = false

        vm.crear(reqProyecto) { cerrado = true }
        vm.eliminar(1)

        assertEquals("No se puede eliminar un proyecto con eventos.", vm.ui.value.aviso)
        assertFalse(cerrado)
        assertFalse(vm.ui.value.trabajando)
        assertEquals(1, vm.ui.value.items.size)
    }

    @Test
    fun `los eventos guardan tambien los proyectos para elegir`() {
        val sync = catalogoCon(
            ProyectoDto(id = 1, titulo = "Alfabetización"),
            eventos = listOf(EventoDto(id = 7, proyectoId = 1, nombre = "Taller", fechaHoraProgramada = "2026-11-05T18:00:00Z")),
        )
        val api = FakeGestionApi()

        val vm = EventosGestionViewModel(sync, api)
        vm.crear(
            EventoRequest(1, "Otro", "2026-11-06T18:00:00Z", 30, null, null, null, null, 200),
        ) {}

        assertEquals(listOf("Alfabetización"), vm.proyectos.value.map { it.titulo })
        assertEquals(listOf("Taller"), vm.ui.value.items.map { it.nombre })
        assertEquals(listOf("crearEvento:Otro"), api.llamadas)
    }

    @Test
    fun `los asistentes se listan y se crean varios de una vez`() {
        val api = FakeGestionApi()
        val vm = AsistentesGestionViewModel(api)
        assertEquals("INS-000001", vm.ui.value.items.single().codigo)

        vm.crear("DOCENTE", "INSCRITO", 5) {}
        vm.editar(1, "ESTUDIANTE", "SIN_INSCRIPCION") {}
        vm.eliminar(1)

        assertEquals(
            listOf("crearAsistentes:DOCENTE:5", "editarAsistente:1:ESTUDIANTE:SIN_INSCRIPCION", "eliminarAsistente:1"),
            api.llamadas,
        )
        assertEquals("Asistente eliminado.", vm.ui.value.aviso)
    }

    @Test
    fun `un 403 al cargar explica que falta el grupo COORDINADOR`() {
        val api = FakeGestionApi().apply { falla = { HttpException(Response.error<Any>(403, "".toResponseBody())) } }

        val vm = AsistentesGestionViewModel(api)

        assertTrue(vm.ui.value.error!!.contains("COORDINADOR"))
        assertTrue(vm.ui.value.items.isEmpty())
    }
}
