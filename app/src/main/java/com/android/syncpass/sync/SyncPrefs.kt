package com.android.syncpass.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Marcas de la última sincronización. */
interface SyncPrefs {
    /** `servidorEn` de la última descarga de catálogo; se reenvía como ?desde= para bajar solo lo nuevo. */
    suspend fun ultimoCatalogoEn(): String?

    suspend fun guardarCatalogoEn(valor: String)

    suspend fun registrarSincronizacion(ahoraMillis: Long)

    /** Cuándo terminó la última sincronización completa, para mostrarlo en pantalla. */
    fun ultimaSincronizacion(): Flow<Long?>
}

private val Context.syncDataStore by preferencesDataStore(name = "syncpass_sync")

class DataStoreSyncPrefs(context: Context) : SyncPrefs {
    private val store = context.applicationContext.syncDataStore

    private companion object {
        val CATALOGO_EN = stringPreferencesKey("catalogo_en")
        val ULTIMA_SYNC = longPreferencesKey("ultima_sync")
    }

    override suspend fun ultimoCatalogoEn(): String? = store.data.first()[CATALOGO_EN]

    override suspend fun guardarCatalogoEn(valor: String) {
        store.edit { it[CATALOGO_EN] = valor }
    }

    override suspend fun registrarSincronizacion(ahoraMillis: Long) {
        store.edit { it[ULTIMA_SYNC] = ahoraMillis }
    }

    override fun ultimaSincronizacion(): Flow<Long?> = store.data.map { it[ULTIMA_SYNC] }
}
