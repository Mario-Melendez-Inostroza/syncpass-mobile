package com.android.syncpass.ui.asistencia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.Reloj
import com.android.syncpass.core.UbicacionProvider
import com.android.syncpass.core.qr.CodigoQr
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.ResultadoMarcacion
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AsistenteItem(val asistente: AsistenteEntity, val marcado: Boolean)

/**
 * Lo que se muestra tras intentar marcar a alguien.
 *
 * @property asistenciaUuid si es una marcación nueva, permite abrir su encuesta de inmediato.
 */
data class ResultadoVisible(
    val texto: String,
    val esError: Boolean,
    val asistenciaUuid: String? = null,
)

/** Marcación de asistencia de un evento, por QR o eligiendo de la lista. Funciona sin conexión. */
class MarcarViewModel(
    private val eventoId: Long,
    private val sub: String,
    private val registro: RegistroRepository,
    catalogoDao: CatalogoDao,
    asistenciaDao: AsistenciaDao,
    private val ubicacion: UbicacionProvider,
    private val scheduler: SyncScheduler,
    private val reloj: Reloj,
) : ViewModel() {

    /** Todo el catálogo de asistentes, indicando quiénes ya están marcados en ESTE evento. */
    val asistentes: StateFlow<List<AsistenteItem>> = combine(
        catalogoDao.observarAsistentes(),
        asistenciaDao.observarPorEvento(eventoId),
    ) { lista, marcadas ->
        val ids = marcadas.map { it.asistencia.asistenteId }.toSet()
        lista.map { AsistenteItem(it, it.id in ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado.asStateFlow()

    private val _ultimo = MutableStateFlow<ResultadoVisible?>(null)
    val ultimo: StateFlow<ResultadoVisible?> = _ultimo.asStateFlow()

    /** Cuántas marcaciones nuevas se hicieron en esta visita a la pantalla (para el contador del escáner). */
    private val _sesionMarcadas = MutableStateFlow(0)
    val sesionMarcadas: StateFlow<Int> = _sesionMarcadas.asStateFlow()

    private var ultimoTexto: String? = null
    private var ultimoInstante = 0L

    /**
     * Cada QR se lee varias veces por segundo. Se ignora el mismo texto durante unos segundos y mientras haya
     * una marcación en curso, para no repetir el aviso ni la consulta de ubicación.
     */
    fun alLeerQr(texto: String) {
        val ahora = reloj.ahoraMillis()
        if (_ocupado.value) return
        if (texto == ultimoTexto && ahora - ultimoInstante < REPETICION_MS) return
        ultimoTexto = texto
        ultimoInstante = ahora

        val id = CodigoQr.leerAsistente(texto)
        if (id == null) {
            _ultimo.value = ResultadoVisible("Ese QR no es una credencial de asistente de SyncPass.", esError = true)
            return
        }
        marcar(id)
    }

    fun marcar(asistenteId: Long) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            try {
                // La ubicación es referencial: si no hay permiso o señal se sigue sin ella.
                val coordenadas = ubicacion.actual()
                val codigo = asistentes.value.firstOrNull { it.asistente.id == asistenteId }?.asistente?.codigo
                    ?: "#$asistenteId"
                _ultimo.value = when (val r = registro.marcarAsistencia(eventoId, asistenteId, sub, coordenadas)) {
                    is ResultadoMarcacion.Registrada -> {
                        _sesionMarcadas.update { it + 1 }
                        scheduler.solicitarAhora()
                        ResultadoVisible(
                            "Asistencia registrada: $codigo. Se enviará cuando haya conexión.",
                            esError = false,
                            asistenciaUuid = r.asistencia.uuid,
                        )
                    }
                    is ResultadoMarcacion.YaMarcada ->
                        ResultadoVisible("$codigo ya estaba marcado en este evento.", esError = true)
                    ResultadoMarcacion.AsistenteDesconocido -> ResultadoVisible(
                        "Ese asistente no está en el catálogo de este teléfono. Sincroniza y vuelve a intentar.",
                        esError = true,
                    )
                    ResultadoMarcacion.EventoDesconocido ->
                        ResultadoVisible("El evento ya no está en el catálogo.", esError = true)
                }
            } finally {
                _ocupado.value = false
            }
        }
    }

    /**
     * Alguien llegó sin inscripción previa (caso §3.1): se le da de alta y se le marca presente en un solo paso.
     * Funciona sin conexión; el alta y la marcación viajan juntas cuando vuelva la red. Sin datos personales:
     * solo el [tipo] de participante.
     */
    fun altaYMarcar(tipo: String) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            try {
                val coordenadas = ubicacion.actual()
                _ultimo.value = when (val r = registro.altaYMarcar(eventoId, tipo, sub, coordenadas)) {
                    is ResultadoMarcacion.Registrada -> {
                        _sesionMarcadas.update { it + 1 }
                        scheduler.solicitarAhora()
                        ResultadoVisible(
                            "Persona nueva registrada y marcada. Se enviará cuando haya conexión.",
                            esError = false,
                            asistenciaUuid = r.asistencia.uuid,
                        )
                    }
                    ResultadoMarcacion.EventoDesconocido ->
                        ResultadoVisible("El evento ya no está en el catálogo.", esError = true)
                    // Una persona recién creada no puede estar marcada ni faltar del catálogo: no ocurre.
                    is ResultadoMarcacion.YaMarcada, ResultadoMarcacion.AsistenteDesconocido ->
                        ResultadoVisible("No se pudo registrar a la persona. Inténtalo de nuevo.", esError = true)
                }
            } finally {
                _ocupado.value = false
            }
        }
    }

    fun limpiarResultado() {
        _ultimo.value = null
    }

    private companion object {
        const val REPETICION_MS = 4_000L
    }
}
