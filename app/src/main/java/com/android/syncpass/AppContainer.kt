package com.android.syncpass

import android.app.Application
import android.content.Context
import com.android.syncpass.core.AndroidNotificador
import com.android.syncpass.core.FusedUbicacionProvider
import com.android.syncpass.core.GeneradorUuid
import com.android.syncpass.core.Notificador
import com.android.syncpass.core.Reloj
import com.android.syncpass.core.UbicacionProvider
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.core.auth.DataStoreTokenStorage
import com.android.syncpass.core.auth.KeystoreSecretCipher
import com.android.syncpass.core.auth.TokenStorage
import com.android.syncpass.core.network.NetworkFactory
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.RoomTransaccion
import com.android.syncpass.data.local.AppDatabase
import com.android.syncpass.sync.DataStoreSyncPrefs
import com.android.syncpass.sync.SyncEngine
import com.android.syncpass.sync.SyncEstado
import com.android.syncpass.sync.SyncPrefs
import com.android.syncpass.sync.SyncScheduler
import com.android.syncpass.sync.WorkManagerSyncScheduler

/**
 * Inyección de dependencias manual. El caso exige Kotlin, Compose, MVVM, Room y Retrofit; no exige un
 * framework de DI, y para esta app un contenedor explícito es más fácil de leer y de probar.
 *
 * Todo es perezoso: nada se construye hasta que alguien lo pide.
 */
class AppContainer(contexto: Context) {

    private val contextoApp = contexto.applicationContext

    val tokenStorage: TokenStorage by lazy { DataStoreTokenStorage(contextoApp, KeystoreSecretCipher()) }

    val red: NetworkFactory by lazy { NetworkFactory(BuildConfig.API_BASE_URL, tokenStorage, BuildConfig.DEBUG) }

    val baseDeDatos: AppDatabase by lazy { AppDatabase.crear(contextoApp) }

    val authRepository: AuthRepository by lazy { AuthRepository(red.authApi, red.sesionApi, tokenStorage) }

    val syncPrefs: SyncPrefs by lazy { DataStoreSyncPrefs(contextoApp) }

    val notificador: Notificador by lazy { AndroidNotificador(contextoApp) }

    val ubicacion: UbicacionProvider by lazy { FusedUbicacionProvider(contextoApp) }

    val syncScheduler: SyncScheduler by lazy { WorkManagerSyncScheduler(contextoApp) }

    val registroRepository: RegistroRepository by lazy {
        RegistroRepository(
            catalogoDao = baseDeDatos.catalogoDao(),
            asistenciaDao = baseDeDatos.asistenciaDao(),
            encuestaDao = baseDeDatos.encuestaDao(),
            reloj = Reloj.Sistema,
            uuid = GeneradorUuid.Aleatorio,
            transaccion = RoomTransaccion(baseDeDatos),
        )
    }

    val syncEstado: SyncEstado by lazy {
        SyncEstado(baseDeDatos.asistenciaDao(), baseDeDatos.encuestaDao(), syncPrefs)
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            api = red.syncPassApi,
            catalogoDao = baseDeDatos.catalogoDao(),
            asistenciaDao = baseDeDatos.asistenciaDao(),
            encuestaDao = baseDeDatos.encuestaDao(),
            prefs = syncPrefs,
            // Un asistente no sincroniza (el servidor se lo niega): sin usuario del equipo no hay nada que subir.
            subActual = { authRepository.usuarioActual()?.takeUnless { it.soloAsistente }?.sub },
            reloj = Reloj.Sistema,
            transaccion = RoomTransaccion(baseDeDatos),
        )
    }
}

class SyncPassApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        // Descarga periódica de catálogo y reenvío de pendientes. Si ya está programada, no se duplica.
        contenedor.syncScheduler.programarPeriodica()
    }
}
