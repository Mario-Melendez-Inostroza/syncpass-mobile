package com.android.syncpass.ui.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.core.auth.UserInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface EstadoSesion {
    /** Todavía se está leyendo si hay una sesión guardada. */
    data object Cargando : EstadoSesion

    data object SinSesion : EstadoSesion

    data class Activa(val usuario: UserInfo) : EstadoSesion
}

/**
 * Sigue la sesión guardada. Cuando los tokens desaparecen (cierre de sesión, o el servidor rechazó la
 * renovación) el estado pasa a SinSesion y la interfaz vuelve sola al login, sin navegar a mano.
 */
class SesionViewModel(private val auth: AuthRepository) : ViewModel() {

    val estado: StateFlow<EstadoSesion> = auth.sesionActiva
        .map { activa ->
            if (!activa) {
                EstadoSesion.SinSesion
            } else {
                auth.usuarioActual()?.let { EstadoSesion.Activa(it) } ?: EstadoSesion.SinSesion
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.Cargando)
}
