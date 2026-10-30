package com.android.syncpass.core.network

import com.android.syncpass.core.auth.TokenStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Construye los clientes HTTP. Hay DOS clientes a propósito:
 *  - uno sin autenticación, para iniciar sesión y renovar el token (si el autenticador usara su propio
 *    cliente, un 401 al renovar provocaría un bucle);
 *  - uno con TokenInterceptor y TokenAuthenticator para todo lo demás.
 *
 * @param baseUrl debe terminar en "/".
 * @param depurar activa un log BÁSICO (método, URL y código). Nunca registra cuerpos ni cabeceras con
 *                tokens, tampoco en release.
 */
class NetworkFactory(
    private val baseUrl: String,
    private val storage: TokenStorage,
    private val depurar: Boolean,
) {
    val gson: Gson = GsonBuilder().create()

    private fun clienteBase(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (depurar) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        },
                    )
                }
            }

    private fun retrofit(cliente: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    val authApi: AuthApi by lazy { retrofit(clienteBase().build()).create(AuthApi::class.java) }

    private val retrofitAutenticado: Retrofit by lazy {
        retrofit(
            clienteBase()
                .addInterceptor(TokenInterceptor(storage))
                .authenticator(TokenAuthenticator(storage, authApi))
                .build(),
        )
    }

    val sesionApi: SesionApi by lazy { retrofitAutenticado.create(SesionApi::class.java) }
    val miCuentaApi: MiCuentaApi by lazy { retrofitAutenticado.create(MiCuentaApi::class.java) }
    val gestionApi: GestionApi by lazy { retrofitAutenticado.create(GestionApi::class.java) }
    val syncPassApi: SyncPassApi by lazy { retrofitAutenticado.create(SyncPassApi::class.java) }
}
