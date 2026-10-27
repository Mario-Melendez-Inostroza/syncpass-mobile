# SyncPass (app Android)

Registro de asistencia en terreno **con o sin conexión**, para proyectos de vinculación comunitaria
(caso DSY1105 · Duoc UC). Kotlin · Jetpack Compose · Material 3 · MVVM · Room · Retrofit · WorkManager.

El backend es `fs2-api` (Spring Boot, rama `syncpass-integrado`). Esta app **nunca habla con Cognito**: inicia
sesión contra el backend, que es quien se entiende con Cognito.

## Qué hace

| Requisito del caso | Cómo está resuelto |
|---|---|
| QR para registrar asistencia (§2.4, §3.3) | `CamaraEscaner` (CameraX + ML Kit) lee `ASISTENTE_{id}`; el coordinador ve y puede imprimir las credenciales (`CredencialesScreen`, ZXing). Sin cámara se elige de una lista |
| Modo **offline-first** (§2.4, §3.3) | Todo se guarda primero en Room. Ninguna acción de registro necesita red |
| **Batch sync** de asistencias y encuestas (§3.4) | `SyncEngine` sube en lotes de 100, con `uuid` idempotente; WorkManager lo ejecuta al volver la red |
| Descarga periódica de proyectos y eventos (§3.4) | `GET /api/sync/catalogo?desde=` incremental, al abrir y cada 15 min |
| Bandera de sincronización y marcas de tiempo (§3.2, §3.4) | `estadoSync` (PENDIENTE / SINCRONIZADO / FALLIDO) y `marcadoEnMillis` del reloj del dispositivo |
| GPS **referencial** (§3.3) | `FusedUbicacionProvider`; si no hay permiso o señal, se registra igual (el servidor lo clasifica `SIN_UBICACION`) |
| Encuesta inmediata, escala 1 a 5 (§2.4, §3.2) | `EncuestaScreen`: satisfacción, utilidad y comentarios |
| Avisos visuales inmediatos (§3.3) | Aviso en pantalla tras cada acción, banner de pendientes y notificación al terminar de sincronizar |
| Autenticación por roles (§3.4) | Grupos de Cognito **COORDINADOR** y **MONITOR** (`cognito:groups`) |
| Quórum en vivo (§2.4, §4.1) | `ResumenScreen` (solo COORDINADOR; el servidor lo hace cumplir con un 403) |
| Claro/oscuro, botones grandes, una mano (§3.3) | Material 3; botones principales de 56 dp; mínimo táctil de 48 dp |
| Android 8.0 (API 26) o superior (§5.1) | `minSdk = 26` |
| Sin datos personales reales (§5.1) | El asistente es un código sintético; el QR solo lleva `ASISTENTE_{id}` |

## Roles

Salen de `caso.md` §3.1, no de las apps de ejemplo del profesor:

- **COORDINADOR** — Docente / Coordinador(a) de Proyecto: registra y además ve el quórum y imprime credenciales.
- **MONITOR** — Estudiante voluntario: registra asistencia y encuestas en terreno.
- Los beneficiarios **no son usuarios de la app**: son registros del catálogo que el monitor marca con el QR.

Un usuario sin ninguno de esos dos grupos no puede entrar (la app lo explica). Los grupos que usa la app para
decidir qué pantallas mostrar vienen del token **sin verificar su firma**: es solo presentación. La autorización
real la hace el servidor en cada petición.

## Arquitectura

```
ui/            Compose + ViewModels (una carpeta por pantalla). Navegación en Navegacion.kt
sync/          SyncEngine (lógica pura), SyncWork (WorkManager), SyncEstado, SyncPrefs
data/          RegistroRepository (reglas de marcación/encuesta), Mappers, local/ (Room)
core/          auth/ (tokens, JWT, login), network/ (Retrofit, DTOs), qr/, Plataforma.kt (GPS, notificaciones)
AppContainer   inyección de dependencias manual (sin Hilt: el caso no lo exige)
```

Decisiones que conviene conocer:

- **El `uuid` lo genera el teléfono** al registrar. Es la clave de idempotencia: reenviar un lote es siempre seguro,
  y el servidor responde `DUPLICADO` a lo que ya recibió. `ACEPTADO` y `DUPLICADO` significan lo mismo para la app.
- **Cada registro guarda el `sub` de quien lo creó**, y solo se sincroniza con la sesión de ese usuario. El servidor
  atribuye lo que recibe al dueño del token; sin esto, lo registrado por A y subido tras iniciar sesión B quedaría
  a nombre de B.
- **El catálogo usa `@Upsert`, no `REPLACE`**, y no hay claves foráneas hacia las asistencias: actualizar el
  catálogo nunca arrastra registros ya hechos.
- **Asistencias antes que encuestas** al sincronizar, porque cada encuesta cuelga de una asistencia. Una encuesta
  rechazada con `ASISTENCIA_NO_ENCONTRADA` queda PENDIENTE (es transitorio), salvo que su asistencia haya fallado.
- **Un fallo de red no cierra la sesión.** Solo se cierra si el servidor *rechaza* el refresh token.
- **Tokens cifrados** con AES-256-GCM en Android Keystore, `allowBackup=false` y sin log de cuerpos ni tokens.
- **Cleartext solo en debug y solo hacia `10.0.2.2`**. El release exige HTTPS.

## Cómo ejecutarla

Requisitos: Android Studio reciente, JDK 21 o superior (el que trae Android Studio sirve).

1. Abre la carpeta `SyncPass` en Android Studio y espera al *Gradle sync*.
2. Levanta el backend (carpeta `fs2-api`) en el puerto 8080. Para tener catálogo de prueba, arráncalo con el perfil
   `demo` (`SPRING_PROFILES_ACTIVE=demo`), que carga datos **ficticios**. Nunca lo uses en producción.
3. Ejecuta la app en el **emulador**. El emulador ve el equipo anfitrión como `10.0.2.2`, que es la URL de debug.
4. Inicia sesión con un usuario del pool que pertenezca a `COORDINADOR` o `MONITOR`.

Desde la terminal:

```
.\gradlew.bat :app:assembleDebug          # APK de debug
.\gradlew.bat :app:testDebugUnitTest      # pruebas unitarias (JVM, sin emulador)
.\gradlew.bat :app:lintDebug              # análisis estático
.\gradlew.bat :app:assembleRelease -PSYNCPASS_API_URL=https://xxxx.execute-api.us-east-1.amazonaws.com/
```

Sin `-PSYNCPASS_API_URL`, el release apunta a `https://api-gateway.syncpass.invalid/` a propósito: falla de forma
evidente en lugar de apuntar a un dominio que parezca real.

En un teléfono físico la URL de debug no sirve (`10.0.2.2` es solo del emulador). Hace falta el backend desplegado
y compilar con `-PSYNCPASS_API_URL`.

## Contrato con el backend

Todas las respuestas correctas vienen en `{success, data, message, timestamp}`; los errores, en
`{error, mensaje, status, detalles}`.

| Método y ruta | Uso |
|---|---|
| `POST api/auth/login` | `{email, password}` → `{accessToken, idToken, refreshToken, expiresIn}` |
| `POST api/auth/refresh` | `{refreshToken, email}`; el backend reenvía el refresh token (Cognito no lo rota) |
| `POST api/auth/logout` | revoca el refresh token |
| `GET api/sync/catalogo?desde=` | proyectos, eventos y asistentes; `desde` es el `servidorEn` anterior |
| `POST api/sync/asistencias` | `{registros:[{uuid, eventoId, asistenteId, marcadoEn, latitud?, longitud?}]}` |
| `POST api/sync/encuestas` | `{registros:[{uuid, asistenciaUuid, satisfaccion, utilidad, comentarios?, enviadaEn}]}` |
| `GET api/eventos/{id}/resumen` | quórum (solo COORDINADOR) |

Cada resultado de un lote es `ACEPTADO`, `DUPLICADO` o `RECHAZADO` (con `motivo`). Un registro rechazado no frena al
resto. Los nombres de campo se comprobaron mecánicamente contra los DTO del backend (`Dto.kt`).

## Pruebas

104 pruebas unitarias, 0 fallos. Cubren, sin emulador:

- **Autenticación** (`AuthFlowTest`): login, errores del backend, rol inválido, y los cinco escenarios de renovación
  de token contra un servidor simulado (renueva y repite, sesión terminada, sin red, error 500, sin bucle).
- **Contrato** (`SyncApiContractTest`): el JSON exacto que la app envía y recibe.
- **Sincronización** (`SyncEngineTest`, 30 casos): idempotencia, lotes parciales, cortes de red a mitad, orden
  asistencias→encuestas, usuario con sesión, reapertura de fallidos tras un catálogo nuevo.
- **Reglas de registro**, **ViewModels** (antirrebote del QR, login, sesión, quórum), **QR** y **JWT**.

Se comprobó, rompiendo la regla a propósito, que las pruebas del motor detectan que `DUPLICADO` deje de contar
como sincronizado.

## Lo que NO está comprobado

Todo lo anterior es verificación **sin dispositivo**. Compila, empaqueta (debug y release), pasa lint sin errores
y la lógica está probada, pero **nada se ha ejecutado todavía en un emulador ni en un teléfono**. Sin probar
queda, por lo tanto, lo que solo existe en ejecución:

- La **cámara y la lectura real del QR** (CameraX + ML Kit) y su permiso.
- El **GPS** y los permisos de ubicación y de notificaciones.
- **WorkManager** reaccionando al volver la red, y el modo avión de punta a punta.
- **Android Keystore** (en la JVM se sustituye por un doble).
- **La apariencia**: diseño, modo oscuro, tamaños en pantallas pequeñas.
- **Room con SQLite real**: las consultas se validan al compilar (KSP), pero no se ejecutaron.
- El flujo completo contra el **backend y Cognito reales**.

Prueba mínima recomendada en el emulador: iniciar sesión → sincronizar → marcar un asistente (por lista y por QR) →
activar modo avión → marcar otro → comprobar "pendiente" → desactivar modo avión → comprobar que pasa a "Enviado".

## Pendiente

- Probar en emulador y dispositivo (arriba).
- Un `network_security_config` y la URL de release reales cuando exista API Gateway.
- Aviso de notificación cuando haya registros rechazados.
- Los textos están escritos en el código; para internacionalizar habría que moverlos a `strings.xml`.
