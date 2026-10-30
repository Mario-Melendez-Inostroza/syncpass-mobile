package com.android.syncpass.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.network.ApiErrors
import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.AsistenteAltaRequest
import com.android.syncpass.core.network.AsistenteEdicionRequest
import com.android.syncpass.core.network.AsistenteFilaDto
import com.android.syncpass.core.network.EventoDto
import com.android.syncpass.core.network.EventoRequest
import com.android.syncpass.core.network.GestionApi
import com.android.syncpass.core.network.ProyectoDto
import com.android.syncpass.core.network.ProyectoRequest
import com.android.syncpass.core.network.SyncPassApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado de un mantenedor: la lista, si hay una operación en curso y el último aviso o error. */
data class GestionUi<T>(
    val cargando: Boolean = true,
    val items: List<T> = emptyList(),
    val trabajando: Boolean = false,
    val error: String? = null,
    val aviso: String? = null,
)

/** El servidor explica sus 400 y 409 ("no se elimina un proyecto con eventos"): se muestra tal cual. */
internal fun motivoDeError(e: Throwable): String {
    if (e is HttpException && e.code() == 403) return "Esta acción es solo del grupo COORDINADOR."
    return (e as? HttpException)?.let { ApiErrors.cuerpo(it)?.mensaje } ?: ApiErrors.describir(e)
}

/**
 * Base de los tres mantenedores (MVVM): carga la lista, y ejecuta crear, editar y eliminar recargando después.
 * Necesitan conexión, a diferencia del registro de asistencia: son tareas de oficina del coordinador.
 */
abstract class MantenedorViewModel<T> : ViewModel() {

    protected val _ui = MutableStateFlow(GestionUi<T>())
    val ui: StateFlow<GestionUi<T>> = _ui.asStateFlow()

    protected abstract suspend fun traer(): List<T>

    fun cargar() {
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val lista = traer()
                _ui.update { it.copy(cargando = false, items = lista) }
            } catch (e: Exception) {
                _ui.update { it.copy(cargando = false, error = motivoDeError(e)) }
            }
        }
    }

    /** Ejecuta una escritura; si sale bien recarga y llama a [alOk] (para cerrar el formulario). */
    protected fun escribir(exito: String, alOk: () -> Unit, operacion: suspend () -> ApiResponse<*>) {
        if (_ui.value.trabajando) return
        _ui.update { it.copy(trabajando = true, aviso = null, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = operacion()
                if (!respuesta.success) {
                    _ui.update { it.copy(trabajando = false, aviso = respuesta.message ?: "No se pudo completar la acción.") }
                    return@launch
                }
                val lista = traer()
                _ui.update { it.copy(trabajando = false, items = lista, aviso = exito) }
                alOk()
            } catch (e: Exception) {
                _ui.update { it.copy(trabajando = false, aviso = motivoDeError(e)) }
            }
        }
    }

    fun avisoMostrado() = _ui.update { it.copy(aviso = null) }
}

/** Mantenedor de proyectos. La lista viene del catálogo del servidor (no de Room) para ver lo vigente. */
class ProyectosViewModel(
    private val sync: SyncPassApi,
    private val api: GestionApi,
) : MantenedorViewModel<ProyectoDto>() {

    init {
        cargar()
    }

    override suspend fun traer(): List<ProyectoDto> =
        sync.catalogo(null).data?.proyectos.orEmpty().sortedByDescending { it.fechaInicio }

    fun crear(req: ProyectoRequest, alOk: () -> Unit) = escribir("Proyecto creado.", alOk) { api.crearProyecto(req) }

    fun editar(id: Long, req: ProyectoRequest, alOk: () -> Unit) =
        escribir("Proyecto actualizado.", alOk) { api.editarProyecto(id, req) }

    fun eliminar(id: Long) = escribir("Proyecto eliminado.", {}) { api.eliminarProyecto(id) }
}

/** Mantenedor de eventos (talleres): además de la lista guarda los proyectos para elegir a cuál pertenece. */
class EventosGestionViewModel(
    private val sync: SyncPassApi,
    private val api: GestionApi,
) : MantenedorViewModel<EventoDto>() {

    private val _proyectos = MutableStateFlow<List<ProyectoDto>>(emptyList())
    val proyectos: StateFlow<List<ProyectoDto>> = _proyectos.asStateFlow()

    init {
        cargar()
    }

    override suspend fun traer(): List<EventoDto> {
        val catalogo = sync.catalogo(null).data
        _proyectos.value = catalogo?.proyectos.orEmpty()
        return catalogo?.eventos.orEmpty().sortedByDescending { it.fechaHoraProgramada }
    }

    fun crear(req: EventoRequest, alOk: () -> Unit) = escribir("Evento creado.", alOk) { api.crearEvento(req) }

    fun editar(id: Long, req: EventoRequest, alOk: () -> Unit) =
        escribir("Evento actualizado.", alOk) { api.editarEvento(id, req) }

    fun eliminar(id: Long) = escribir("Evento eliminado.", {}) { api.eliminarEvento(id) }
}

/** Mantenedor de asistentes: el código lo genera el servidor, el coordinador elige tipo e inscripción. */
class AsistentesGestionViewModel(private val api: GestionApi) : MantenedorViewModel<AsistenteFilaDto>() {

    init {
        cargar()
    }

    override suspend fun traer(): List<AsistenteFilaDto> = api.asistentes().data.orEmpty()

    fun crear(tipo: String, estado: String, cantidad: Int, alOk: () -> Unit) =
        escribir(if (cantidad == 1) "Asistente creado." else "$cantidad asistentes creados.", alOk) {
            api.crearAsistentes(AsistenteAltaRequest(tipo, estado, cantidad))
        }

    fun editar(id: Long, tipo: String, estado: String, alOk: () -> Unit) =
        escribir("Asistente actualizado.", alOk) { api.editarAsistente(id, AsistenteEdicionRequest(tipo, estado)) }

    fun eliminar(id: Long) = escribir("Asistente eliminado.", {}) { api.eliminarAsistente(id) }
}
