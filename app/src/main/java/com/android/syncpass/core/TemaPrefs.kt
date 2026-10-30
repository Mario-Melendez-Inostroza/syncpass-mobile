package com.android.syncpass.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencia de apariencia: claro por defecto; el modo oscuro se activa a mano en Perfil y se recuerda. */
class TemaPrefs(contexto: Context) {

    private val prefs = contexto.applicationContext.getSharedPreferences("syncpass_ui", Context.MODE_PRIVATE)

    private val _oscuro = MutableStateFlow(prefs.getBoolean(CLAVE_OSCURO, false))
    val oscuro: StateFlow<Boolean> = _oscuro.asStateFlow()

    fun cambiar(oscuro: Boolean) {
        prefs.edit().putBoolean(CLAVE_OSCURO, oscuro).apply()
        _oscuro.value = oscuro
    }

    private companion object {
        const val CLAVE_OSCURO = "modo_oscuro"
    }
}
