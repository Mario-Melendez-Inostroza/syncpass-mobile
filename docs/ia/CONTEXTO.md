# Contexto del caso (resumen para la IA)

Fuente de verdad: el caso **DSY1105 · SyncPass** (Duoc UC, CITT Plaza Vespucio). Este archivo lo resume para que la IA
arranque cada sesión con el mismo contexto; ante cualquier duda manda el caso original.

## Problema

Los proyectos de vinculación comunitaria registran la asistencia **en papel** y consolidan todo a mano: hojas que se
pierden, filas de 15 a 20 minutos en la entrada, encuestas con menos de 15 % de respuesta (llegan semanas después) y
barrios con poca o ninguna señal móvil.

## Solución (tres piezas)

| Pieza | Repositorio | Función |
|---|---|---|
| App Android | `syncpass-mobile` | Registro de asistencia por QR o lista, **sin conexión**, encuesta inmediata, mantenedores del coordinador |
| Panel web | `syncpass-web` | Página de inicio, panel del coordinador (proyectos, eventos, asistentes, quórum, informes) y portal del asistente |
| API REST | `syncpass-api` | Autenticación con Cognito, catálogo, sincronización por lotes, quórum e informes; base PostgreSQL |

## Roles

| Rol | Quién es | Qué hace |
|---|---|---|
| **COORDINADOR** | Docente / Coordinador(a) de proyecto | Crea y revisa proyectos, eventos y asistentes; supervisa el quórum; emite informes |
| **MONITOR** | Estudiante voluntario | Registra asistencia en terreno (QR o lista), da de alta a quien llega sin inscripción, levanta la encuesta |
| **ASISTENTE** | Vecino, estudiante o docente que participa | Se registra solo, ve los talleres, se inscribe y muestra su QR |

## Datos del dominio

Proyecto (título, área temática, unidad vecinal, fechas, estado) · Evento (proyecto, nombre, fecha y hora, cupos, quórum
mínimo, recinto, coordenadas, radio) · Asistente (**código sintético**, tipo, estado de inscripción) · Asistencia (evento,
asistente, marca de tiempo, validación GPS, estado de sincronización) · Encuesta (satisfacción 1 a 5, utilidad 1 a 5,
comentarios).

## Metas (objetivos, no mediciones)

Registrar en **menos de 5 segundos** · **más de 65 %** de respuesta a la encuesta · **80 %** menos horas de digitación ·
**100 %** de operación sin conexión · cero pérdida de registros.

## Restricciones del caso (§5.1)

- MVP académico: no se publica un producto definitivo.
- **Prohibido usar datos personales reales** (RUT, nombres, correos, teléfonos, direcciones, biometría): todo es ficticio.
- Sin credenciales ni sistemas internos del socio formador.
- Cámara y GPS se prueban sin almacenar ni identificar personas.
- **Stack obligatorio:** Kotlin, Android Studio, Jetpack Compose, Material 3, MVVM, Room/SQLite y API REST en Spring Boot con Retrofit.
- Android 8.0 (API 26) o superior; áreas táctiles de al menos 48 dp.
- Repositorios y capturas solo con información simulada.
