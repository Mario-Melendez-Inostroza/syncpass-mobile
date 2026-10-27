# SyncPass (app Android)

Registro de asistencia en terreno **con o sin conexión**, para proyectos de vinculación comunitaria
(caso DSY1105 · Duoc UC). Kotlin · Jetpack Compose · Material 3 · MVVM · Room · Retrofit · WorkManager.

## El sistema completo

```
Usuario ─► Panel web (React)  ─┐
                                ├─► API Gateway ─► API Spring Boot (EC2) ─┬─► Amazon Cognito
Usuario ─► App Android (esta)  ┘                                          └─► Supabase PostgreSQL
```

| Repositorio | Qué es |
|---|---|
| **syncpass-mobile** (este) | App Android del monitor: registra asistencia por QR o lista, da de alta a quien llega sin inscripción, encuestas, todo sin conexión |
| **syncpass-api** | Backend: autenticación, catálogo, sincronización por lotes, quórum y reportes |
| **syncpass-web** | Página de inicio pública y panel del coordinador |

Esta app **nunca habla con Cognito**: inicia sesión contra el backend, que es quien se entiende con Cognito.

## Qué hace

| Requisito del caso | Cómo está resuelto |
|---|---|
| QR para registrar asistencia (§2.4, §3.3) | `CamaraEscaner` (CameraX + ML Kit) lee `ASISTENTE_{id}`; el coordinador ve y puede imprimir las credenciales (`CredencialesScreen`, ZXing). Sin cámara se elige de una lista |
| Modo **offline-first** (§2.4, §3.3) | Todo se guarda primero en Room. Ninguna acción de registro necesita red |
| **Batch sync** de asistencias y encuestas (§3.4) | `SyncEngine` sube en lotes de 100, con `uuid` idempotente; WorkManager lo ejecuta al volver la red |
| Descarga periódica de proyectos y eventos (§3.4) | `GET /api/sync/catalogo?desde=` incremental, al abrir y cada 15 min |
| Bandera de sincronización y marcas de tiempo (§3.2, §3.4) | `estadoSync` (PENDIENTE / SINCRONIZADO / FALLIDO) y `marcadoEnMillis` del reloj del dispositivo |
| GPS **referencial** (§3.3) | `FusedUbicacionProvider`; si no hay permiso o señal, se registra igual (el servidor lo clasifica `SIN_UBICACION`) |
| Alta de quien llega **sin inscripción previa** (§3.1) | "Agregar persona sin inscripción" en la lista: eligiendo Estudiante, Vecino o Docente, la persona queda creada y marcada en un solo paso, también sin conexión. Nace con un id provisional negativo y se reemplaza por el real al sincronizar (`SyncEngine`); sin ningún dato personal |
| Encuesta inmediata, escala 1 a 5 (§2.4, §3.2) | `EncuestaScreen`: satisfacción, utilidad y comentarios |
| Avisos visuales inmediatos (§3.3) | Aviso en pantalla tras cada acción, banner de pendientes y notificación al terminar de sincronizar |
| Autenticación por roles (§3.4) | Grupos de Cognito **COORDINADOR**, **MONITOR** y **ASISTENTE** (`cognito:groups`) |
| Quórum en vivo (§2.4, §4.1) | `ResumenScreen` (solo COORDINADOR; el servidor lo hace cumplir con un 403) |
| Claro/oscuro, botones grandes, una mano (§3.3) | Material 3; botones principales de 56 dp; mínimo táctil de 48 dp |
| Android 8.0 (API 26) o superior (§5.1) | `minSdk = 26` |
| Sin datos personales reales (§5.1) | El asistente es un código sintético; el QR solo lleva `ASISTENTE_{id}` |

## Roles

Salen de `caso.md` §3.1, no de las apps de ejemplo del profesor:

- **COORDINADOR** — Docente / Coordinador(a) de Proyecto: registra y además ve el quórum y imprime credenciales.
- **MONITOR** — Estudiante voluntario: registra asistencia y encuestas en terreno.
- **ASISTENTE** — Beneficiario comunitario: se registra desde la pantalla de ingreso ("Soy asistente · Crear mi cuenta"), ve los
  talleres próximos con sus cupos, se inscribe y muestra su código QR (`ASISTENTE_{id}`) al monitor. Necesita conexión para
  registrarse e inscribirse y **no sincroniza** (el servidor se lo niega). No se guardan nombre ni correo en la base: solo un código sintético.
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
4. Inicia sesión con un usuario del pool que pertenezca a `COORDINADOR`, `MONITOR` o `ASISTENTE` (este último se crea solo desde la app).

Desde la terminal:

```
.\gradlew.bat :app:assembleDebug          # APK de debug
.\gradlew.bat :app:testDebugUnitTest      # pruebas unitarias (JVM, sin emulador)
.\gradlew.bat :app:lintDebug              # análisis estático
.\gradlew.bat :app:assembleRelease -PSYNCPASS_API_URL=https://xxxx.execute-api.us-east-1.amazonaws.com/
```

Sin `-PSYNCPASS_API_URL`, el release apunta a `https://api-gateway.syncpass.invalid/` a propósito: falla de forma
evidente en lugar de apuntar a un dominio que parezca real.

En un teléfono físico la URL de debug por defecto no sirve (`10.0.2.2` es solo del emulador). Para apuntar al API
Gateway sin tocar el código, define `SYNCPASS_API_URL=https://<api-gateway>/` en `~/.gradle/gradle.properties`
(o pásalo con `-PSYNCPASS_API_URL`). Lo respetan las compilaciones debug y release.

## Instalarla en un teléfono real

Es la forma de probar de verdad la cámara, el GPS y el modo avión.

1. **Apunta la app al backend.** Crea `~/.gradle/gradle.properties` (en Windows, `C:\Users\<tu usuario>\.gradle\`) con una
   línea: `SYNCPASS_API_URL=https://<api-gateway>/` (con la barra final). No se sube a Git ni lleva secretos. Luego, en
   Android Studio, *Sync Project with Gradle Files*.
2. **Activa la depuración USB**: Ajustes → Acerca del teléfono → toca 7 veces *Número de compilación*; después Ajustes →
   Opciones de desarrollador → *Depuración por USB*. Conecta el cable (de datos, no solo de carga) y acepta el aviso.
3. Elige tu teléfono arriba y pulsa **Run**. La app queda instalada y se puede desconectar el cable.
4. **Sin cable**: *Depuración inalámbrica* (Android 11 o superior) y *Pair Devices Using Wi-Fi* en Android Studio, con el PC
   y el teléfono en la misma red. Otra opción: *Build → Build APK(s)* y pasar el archivo `app-debug.apk` al teléfono.

Permisos que pide: **cámara** (leer QR), **ubicación aproximada** (referencial, nunca bloquea) y **notificaciones**
(avisar cuando se envía lo pendiente).

### Cuentas de prueba

Las crea el equipo docente en el User Pool de Cognito (no hay auto-registro en la app). En el entorno de demostración:
`monitor.demo@example.com` (grupo MONITOR) y `docente.demo@example.com` (grupo COORDINADOR). Son cuentas ficticias.

### Probar el modo sin conexión

1. Con internet, inicia sesión y deja que se descargue el catálogo (la primera vez es obligatoria).
2. Activa el **modo avión** y marca asistencia (por lista o QR) o agrega una persona sin inscripción: queda
   *pendiente de enviar*.
3. Desactiva el modo avión: en unos segundos llega una notificación y el registro pasa a *Enviado*.

### Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| Android Studio: `jlink executable ... does not exist` | Gradle usa un JRE recortado (p. ej. el de la extensión de Java de VS Code). En *Settings → Build Tools → Gradle → Gradle JDK* y en *Project Structure → SDK* elige un **JDK 21 completo** (`temurin-21` o el *jbr* de Android Studio) y haz el Sync |
| "Servidor no disponible" al iniciar sesión | El backend o la EC2 están apagados (en un Learner Lab se apagan al terminar la sesión) o `SYNCPASS_API_URL` está mal. Comprueba `<url>/api/health` |
| La app se cierra al abrir tras actualizarla | Falló la migración de Room. Desinstala y reinstala (son datos de prueba) y avisa del error |
| El teléfono no aparece en Android Studio | Cable solo de carga, depuración USB sin activar o aviso sin aceptar |

## Contrato con el backend

Todas las respuestas correctas vienen en `{success, data, message, timestamp}`; los errores, en
`{error, mensaje, status, detalles}`.

| Método y ruta | Uso |
|---|---|
| `POST api/auth/login` | `{email, password}` → `{accessToken, idToken, refreshToken, expiresIn}` |
| `POST api/auth/refresh` | `{refreshToken, email}`; el backend reenvía el refresh token (Cognito no lo rota) |
| `POST api/auth/logout` | revoca el refresh token |
| `GET api/sync/catalogo?desde=` | proyectos, eventos y asistentes; `desde` es el `servidorEn` anterior |
| `POST api/sync/asistentes` | altas en terreno: `{registros:[{uuid, tipo}]}` → por cada una `id` y `codigo` reales (`TER-…`). Se sube **antes** que las asistencias |
| `POST api/sync/asistencias` | `{registros:[{uuid, eventoId, asistenteId, marcadoEn, latitud?, longitud?}]}` |
| `POST api/sync/encuestas` | `{registros:[{uuid, asistenciaUuid, satisfaccion, utilidad, comentarios?, enviadaEn}]}` |
| `GET api/eventos/{id}/resumen` | quórum (solo COORDINADOR) |

Cada resultado de un lote es `ACEPTADO`, `DUPLICADO` o `RECHAZADO` (con `motivo`). Un registro rechazado no frena al
resto. Los nombres de campo se comprobaron mecánicamente contra los DTO del backend (`Dto.kt`).

## Pruebas

131 pruebas unitarias, 0 fallos. Cubren, sin emulador:

- **Autenticación** (`AuthFlowTest`): login, errores del backend, rol inválido, y los cinco escenarios de renovación
  de token contra un servidor simulado (renueva y repite, sesión terminada, sin red, error 500, sin bucle).
- **Contrato** (`SyncApiContractTest`): el JSON exacto que la app envía y recibe.
- **Sincronización** (`SyncEngineTest`, 30 casos): idempotencia, lotes parciales, cortes de red a mitad, orden
  asistencias→encuestas, usuario con sesión, reapertura de fallidos tras un catálogo nuevo.
- **Alta en terreno** (27 casos): alta y marcación atómicas (si falla una, no queda la otra), ids provisionales
  distintos, reemplazo por el id real con sus asistencias, reenvío tras perder la respuesta, alta rechazada, y el
  JSON exacto del endpoint nuevo.
- **Reglas de registro**, **ViewModels** (antirrebote del QR, login, sesión, quórum), **QR** y **JWT**.

Se comprobó, rompiendo la regla a propósito, que las pruebas del motor detectan que `DUPLICADO` deje de contar
como sincronizado.

## Lo que NO está comprobado

Las pruebas unitarias son verificación **sin dispositivo**. Además, la versión anterior a la alta en terreno se
probó a mano en un teléfono físico (Xiaomi 23090RA98G) contra el backend y Cognito reales: inicio de sesión como
monitor, descarga del catálogo, marcación en modo avión, envío automático con notificación al volver el Wi-Fi, y la
marcación visible en el panel web (clasificada "fuera del radio", lo que confirma que el GPS envió coordenadas).

**Sin probar todavía en un teléfono**:

- La **alta en terreno** ("Agregar persona sin inscripción") y su reemplazo por el id real: solo hay pruebas unitarias.
- La **migración de Room v1 → v2**: `MigracionRoomTest` está escrita y compila, pero es instrumentada y necesita un
  teléfono o emulador conectado (`./gradlew :app:connectedDebugAndroidTest`); no se ha ejecutado.
- La **cámara y la lectura real del QR** (CameraX + ML Kit) y su permiso.
- **Android Keystore** (en la JVM se sustituye por un doble).
- **La apariencia**: diseño, modo oscuro, tamaños en pantallas pequeñas.

Prueba mínima recomendada para lo nuevo: sincronizar → lista de asistentes → "Agregar persona sin inscripción" →
elegir un tipo → comprobar que queda marcada y "pendiente de enviar" → con red, comprobar que su código pasa de
`NUEVO-…` a `TER-…` y que aparece en el panel web.

## Pendiente

- Probar lo nuevo en dispositivo (arriba) y ejecutar `MigracionRoomTest`.
- Un alta rechazada por el servidor deja sus asistencias esperando sin avisar al usuario (no debería ocurrir: el
  tipo y el uuid los controla la app).
- Un `network_security_config` y la URL de release reales cuando exista API Gateway.
- Aviso de notificación cuando haya registros rechazados.
- Los textos están escritos en el código; para internacionalizar habría que moverlos a `strings.xml`.

## Equipo

Proyecto del curso DSY1105 · Desarrollo de Aplicaciones Móviles · Duoc UC.

| Integrante | GitHub | Aporte en este repositorio |
|---|---|---|
| Mario Meléndez | [Mario-Melendez-Inostroza](https://github.com/Mario-Melendez-Inostroza) | Desarrollo e integración del sistema |
| _(nombre completo)_ | [bx1423](https://github.com/bx1423) | _(completar: qué aportó o revisó)_ |
| _(nombre completo)_ | [Monnacohu](https://github.com/Monnacohu) | _(completar: qué aportó o revisó)_ |
