package com.android.syncpass.data

import com.android.syncpass.core.network.AsistenciaSyncDto
import com.android.syncpass.core.network.AsistenteDto
import com.android.syncpass.core.network.EncuestaSyncDto
import com.android.syncpass.core.network.EventoDto
import com.android.syncpass.core.network.ProyectoDto
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EncuestaEntity
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.data.local.ProyectoEntity
import java.time.Instant

/** Radio por defecto, igual que el del servidor, si un evento llegara sin él. */
private const val RADIO_POR_DEFECTO_METROS = 200

// ------------------------------------------------------------------ servidor -> teléfono
// Devuelven null si falta un campo obligatorio: esa fila se descarta, no se rompe la descarga completa.

fun ProyectoDto.aEntidad(): ProyectoEntity? {
    return ProyectoEntity(
        id = id ?: return null,
        titulo = titulo ?: return null,
        descripcion = descripcion,
        areaTematica = areaTematica ?: return null,
        unidadVecinal = unidadVecinal ?: return null,
        fechaInicio = fechaInicio ?: return null,
        fechaTermino = fechaTermino,
        estado = estado ?: return null,
    )
}

fun EventoDto.aEntidad(): EventoEntity? {
    return EventoEntity(
        id = id ?: return null,
        proyectoId = proyectoId ?: return null,
        nombre = nombre ?: return null,
        fechaHoraProgramada = fechaHoraProgramada ?: return null,
        cuposMaximos = cuposMaximos,
        recinto = recinto,
        latitud = latitud,
        longitud = longitud,
        radioMetros = radioMetros ?: RADIO_POR_DEFECTO_METROS,
    )
}

fun AsistenteDto.aEntidad(): AsistenteEntity? {
    return AsistenteEntity(
        id = id ?: return null,
        codigo = codigo ?: return null,
        tipo = tipo ?: return null,
        estadoInscripcion = estadoInscripcion ?: return null,
    )
}

// ------------------------------------------------------------------ teléfono -> servidor

/** Instante en ISO-8601 UTC, como lo espera el backend (java.time.Instant). */
fun Long.aIso(): String = Instant.ofEpochMilli(this).toString()

/** No incluye el usuario que registra: el servidor lo toma del token. */
fun AsistenciaEntity.aDto(): AsistenciaSyncDto = AsistenciaSyncDto(
    uuid = uuid,
    eventoId = eventoId,
    asistenteId = asistenteId,
    marcadoEn = marcadoEnMillis.aIso(),
    latitud = latitud,
    longitud = longitud,
)

fun EncuestaEntity.aDto(): EncuestaSyncDto = EncuestaSyncDto(
    uuid = uuid,
    asistenciaUuid = asistenciaUuid,
    satisfaccion = satisfaccion,
    utilidad = utilidad,
    comentarios = comentarios,
    enviadaEn = enviadaEnMillis.aIso(),
)
