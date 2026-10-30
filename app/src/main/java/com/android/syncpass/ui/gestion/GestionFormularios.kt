package com.android.syncpass.ui.gestion

import com.android.syncpass.core.network.EventoRequest
import com.android.syncpass.core.network.ProyectoRequest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Resultado de validar un formulario antes de enviarlo: la solicitud lista o el mensaje para el usuario. */
sealed interface Validacion<out T> {
    data class Ok<T>(val valor: T) : Validacion<T>
    data class Error(val mensaje: String) : Validacion<Nothing>
}

val ESTADOS_PROYECTO = listOf("PLANIFICADO", "EN_CURSO", "CERRADO")
val TIPOS_PARTICIPANTE = listOf("ESTUDIANTE", "VECINO", "DOCENTE")
val ESTADOS_INSCRIPCION = listOf("INSCRITO", "SIN_INSCRIPCION")

/** Formato con el que el coordinador escribe la hora de un evento: la del teléfono, no UTC. */
val FORMATO_FECHA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

data class FormProyecto(
    val titulo: String = "",
    val descripcion: String = "",
    val areaTematica: String = "",
    val unidadVecinal: String = "",
    val fechaInicio: String = "",
    val fechaTermino: String = "",
    val estado: String = "PLANIFICADO",
)

data class FormEvento(
    val proyectoId: Long? = null,
    val nombre: String = "",
    val fechaHora: String = "",
    val cuposMaximos: String = "",
    val quorumMinimo: String = "",
    val recinto: String = "",
    val latitud: String = "",
    val longitud: String = "",
    val radioMetros: String = "",
)

private fun fecha(texto: String): LocalDate? = runCatching { LocalDate.parse(texto.trim()) }.getOrNull()

private fun entero(texto: String): Result<Int?> {
    val limpio = texto.trim()
    if (limpio.isEmpty()) return Result.success(null)
    return limpio.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(NumberFormatException(limpio))
}

private fun decimal(texto: String): Result<Double?> {
    val limpio = texto.trim().replace(',', '.')
    if (limpio.isEmpty()) return Result.success(null)
    return limpio.toDoubleOrNull()?.let { Result.success(it) } ?: Result.failure(NumberFormatException(limpio))
}

/** Mismas reglas que el servidor (ProyectoRequest), para avisar antes de gastar una llamada. */
fun validarProyecto(f: FormProyecto): Validacion<ProyectoRequest> {
    if (f.titulo.isBlank()) return Validacion.Error("El título es obligatorio.")
    if (f.titulo.trim().length > 150) return Validacion.Error("El título admite hasta 150 caracteres.")
    if (f.descripcion.trim().length > 1000) return Validacion.Error("La descripción admite hasta 1000 caracteres.")
    if (f.areaTematica.isBlank()) return Validacion.Error("El área temática es obligatoria.")
    if (f.unidadVecinal.isBlank()) return Validacion.Error("La unidad vecinal es obligatoria.")
    val inicio = fecha(f.fechaInicio) ?: return Validacion.Error("La fecha de inicio debe tener el formato AAAA-MM-DD.")
    val termino = if (f.fechaTermino.isBlank()) null
    else fecha(f.fechaTermino) ?: return Validacion.Error("La fecha de término debe tener el formato AAAA-MM-DD.")
    if (termino != null && termino.isBefore(inicio)) {
        return Validacion.Error("La fecha de término no puede ser anterior a la de inicio.")
    }
    return Validacion.Ok(
        ProyectoRequest(
            titulo = f.titulo.trim(),
            descripcion = f.descripcion.trim().ifEmpty { null },
            areaTematica = f.areaTematica.trim(),
            unidadVecinal = f.unidadVecinal.trim(),
            fechaInicio = inicio.toString(),
            fechaTermino = termino?.toString(),
            estado = f.estado,
        ),
    )
}

/** Mismas reglas que el servidor (EventoRequest). [zona] se inyecta para poder probar la conversión a UTC. */
fun validarEvento(f: FormEvento, zona: ZoneId = ZoneId.systemDefault()): Validacion<EventoRequest> {
    val proyectoId = f.proyectoId ?: return Validacion.Error("Elige el proyecto del evento.")
    if (f.nombre.isBlank()) return Validacion.Error("El nombre es obligatorio.")
    if (f.nombre.trim().length > 150) return Validacion.Error("El nombre admite hasta 150 caracteres.")
    if (f.recinto.trim().length > 200) return Validacion.Error("El recinto admite hasta 200 caracteres.")
    val instante = try {
        LocalDateTime.parse(f.fechaHora.trim(), FORMATO_FECHA_HORA).atZone(zona).toInstant()
    } catch (_: DateTimeException) {
        return Validacion.Error("La fecha y hora deben tener el formato DD/MM/AAAA HH:MM.")
    }
    val cupos = entero(f.cuposMaximos).getOrElse { return Validacion.Error("Los cupos deben ser un número entero.") }
    val quorum = entero(f.quorumMinimo).getOrElse { return Validacion.Error("El quórum mínimo debe ser un número entero.") }
    val radio = entero(f.radioMetros).getOrElse { return Validacion.Error("El radio debe ser un número entero de metros.") }
    val latitud = decimal(f.latitud).getOrElse { return Validacion.Error("La latitud debe ser un número.") }
    val longitud = decimal(f.longitud).getOrElse { return Validacion.Error("La longitud debe ser un número.") }
    if (cupos != null && cupos < 1) return Validacion.Error("Los cupos deben ser al menos 1.")
    if (quorum != null && quorum < 1) return Validacion.Error("El quórum mínimo debe ser al menos 1.")
    if (quorum != null && cupos != null && quorum > cupos) {
        return Validacion.Error("El quórum mínimo no puede superar los cupos.")
    }
    if (radio != null && radio < 1) return Validacion.Error("El radio debe ser al menos 1 metro.")
    if ((latitud == null) != (longitud == null)) return Validacion.Error("Indica latitud y longitud juntas, o ninguna.")
    if (latitud != null && latitud !in -90.0..90.0) return Validacion.Error("La latitud va entre -90 y 90.")
    if (longitud != null && longitud !in -180.0..180.0) return Validacion.Error("La longitud va entre -180 y 180.")
    return Validacion.Ok(
        EventoRequest(
            proyectoId = proyectoId,
            nombre = f.nombre.trim(),
            fechaHoraProgramada = instante.toString(),
            cuposMaximos = cupos,
            quorumMinimo = quorum,
            recinto = f.recinto.trim().ifEmpty { null },
            latitud = latitud,
            longitud = longitud,
            radioMetros = radio,
        ),
    )
}

/** Un instante UTC del servidor como lo escribe el coordinador en el formulario (hora del teléfono). */
fun fechaHoraParaFormulario(iso: String?, zona: ZoneId = ZoneId.systemDefault()): String =
    runCatching { FORMATO_FECHA_HORA.format(java.time.Instant.parse(iso).atZone(zona)) }.getOrDefault("")
