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

## El caso que resolvemos

**SyncPass** nace del caso de Ticdgm (CITT Plaza Vespucio, DSY1105): los proyectos de vinculación comunitaria
toman la asistencia **en papel**, y eso provoca:

| Problema en terreno | Consecuencia | Cómo lo resuelve SyncPass |
|---|---|---|
| Hojas que se pierden o no se entienden | Sin evidencia de la participación | Cada asistencia queda guardada en el teléfono y luego en la base de datos, con hora y ubicación referencial |
| Filas de 15 a 20 minutos en la entrada | Se pierde tiempo del taller | El monitor escanea el **QR** del asistente: un toque |
| Encuesta días después, con menos de 15 % de respuesta | No se mide el impacto | Encuesta de 1 a 5 **en el momento** |
| Barrios con poca o ninguna señal | Una app web no sirve | **Offline-first**: todo funciona sin internet y se envía solo cuando vuelve la señal |
| Transcribir planillas a mano | Errores y semanas de trabajo | El panel web calcula quórum y promedios al instante, sin transcribir |

Metas del caso: registrar en **menos de 5 segundos**, **más de 65 %** de respuesta a la encuesta, reducir **80 %** el
tiempo de consolidación y **100 %** de registros con respaldo. Son objetivos, no mediciones: aún no se han medido.

### Quiénes lo usan (roles)

| Rol | Quién es | Qué hace en la app |
|---|---|---|
| **COORDINADOR** | Docente / Coordinador(a) de proyecto | Gestiona proyectos, eventos y asistentes (mantenedores), ve el quórum y imprime credenciales QR |
| **MONITOR** | Estudiante voluntario | Registra asistencia por QR o lista, da de alta a quien llega sin inscripción y levanta la encuesta |
| **ASISTENTE** | Vecino, estudiante o docente que participa | Crea su cuenta, ve los talleres, se inscribe y muestra su QR |

El rol sale del grupo de **Amazon Cognito** (`cognito:groups`) y decide qué pantallas se ven. La autorización real la
hace el servidor en cada petición: aunque alguien viera un botón que no le corresponde, el servidor responde 403.

## Cómo usamos la IA

Usamos **Claude Code** (Anthropic) como asistente de programación, y lo decimos con claridad:

- **Qué hizo la IA:** a partir del `caso.md` propuso la arquitectura, escribió gran parte del código, las pruebas y la documentación,
  y ayudó a diagnosticar errores (por ejemplo, por qué la EC2 no podía hablar con Cognito: le faltaba un rol IAM).
- **Qué hicimos nosotros:** definimos qué pedía el caso, decidimos los roles y las pantallas, corregimos a la IA cuando entendió mal
  (por ejemplo, que el **asistente también es un rol** y debe poder registrarse e inscribirse), instalamos y **probamos en un teléfono real**,
  desplegamos en AWS y revisamos cada cambio antes de subirlo a GitHub con su Pull Request.
- **Cómo evitamos confiar a ciegas:** pruebas automáticas (más abajo), pruebas "rompiendo la regla a propósito" para ver que fallan, y una
  sección honesta de *lo que no está comprobado*.
- **Límites de la IA que encontramos:** puede dar por hecho algo que no probó; por eso todo lo marcado como verificado se ejecutó de verdad.

## Mockup: las pantallas de la app

```
 ┌──────────── Ingreso ────────────┐   ┌──────── Eventos ────────┐   ┌──────── Evento ─────────┐
 │        [logo SyncPass]          │   │ Alfabetización digital  │   │ Taller de correo        │
 │  Correo      [______________]   │   │  ▸ Taller de correo     │   │ 05/11 15:00 · Sede 1    │
 │  Contraseña  [______________]   │   │  ▸ Seguridad en línea   │   │ [ Escanear QR       ]   │
 │  [      Iniciar sesión      ]   │   │                         │   │ [ Elegir de la lista ]  │
 │  Soy asistente · Crear cuenta   │   │ Eventos | Sync | Perfil │   │ [ Ver quórum (coord.) ] │
 └─────────────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘

 ┌──── Encuesta ─────┐  ┌──── Gestión (coordinador) ────┐  ┌──── Portal del asistente ────┐
 │ Satisfacción ★★★★☆ │  │ [ Proyectos ]                 │  │ Talleres · cupos disponibles │
 │ Utilidad     ★★★★★ │  │ [ Eventos (talleres) ]        │  │ [ Inscribirme ]              │
 │ [ Enviar ]        │  │ [ Asistentes ]                │  │ Mi credencial: [ QR grande ] │
 └───────────────────┘  └───────────────────────────────┘  └──────────────────────────────┘
```

## Mantenedores (CRUD) de la app

El coordinador tiene la pestaña **Gestión** con tres mantenedores completos: **crear, editar, eliminar y listar**,
con validación en el formulario antes de enviar y el mensaje del servidor cuando algo no se puede (HTTP 409).

| Mantenedor | Qué administra | Reglas que muestra la app |
|---|---|---|
| **Proyectos** | Título, descripción, área, unidad vecinal, fechas y estado (la "creación de curso") | La fecha de término no puede ser anterior a la de inicio; no se elimina un proyecto con eventos |
| **Eventos (talleres)** | Proyecto, nombre, fecha y hora, cupos, quórum mínimo, recinto y GPS | El quórum no supera los cupos; latitud y longitud van juntas; no se elimina un evento con asistencias |
| **Asistentes** | Tipo e inscripción; alta de 1 a 200 de una vez ("edición de estudiantes del curso") | El código lo genera el servidor; no se elimina a quien tiene asistencias |

Además, el **asistente** tiene su propio flujo de inscripción (inscribirse y cancelar). Código en `ui/gestion/`
(`GestionFormularios.kt` validaciones, `GestionViewModels.kt` lógica, `GestionScreens.kt` pantallas).

## Arquitectura de la app (MVVM)

```
 Pantalla (Compose)  ──►  ViewModel  ──►  Repositorio / API  ──►  Room (local)  o  Retrofit (servidor)
   solo dibuja            estado y reglas   un solo lugar para       base en el teléfono   backend Spring
   y avisa eventos        de la pantalla    los datos
```

| Capa | Qué hace | Dónde |
|---|---|---|
| **View** | Dibuja el estado y avisa los toques. No toma decisiones | `ui/**/…Screen.kt` |
| **ViewModel** | Guarda el estado de la pantalla (`StateFlow`), ejecuta las acciones, sobrevive a girar la pantalla | `ui/**/…ViewModel.kt` |
| **Model / datos** | `RegistroRepository` (reglas de marcación), `SyncEngine` (sincronización), Room (`data/local`), DTO de red (`core/network`) | `data/`, `sync/`, `core/` |

La capa de red separa también **interfaz → implementación**: `GestionApi`, `SyncPassApi`… son interfaces y Retrofit
genera la implementación; en las pruebas se sustituyen por dobles, sin servidor.

### Conceptos que usamos, explicados

- **Jetpack Compose**: forma moderna de construir pantallas en Android **escribiendo funciones `@Composable`** en Kotlin en vez de
  archivos XML. La pantalla es una función del estado: cuando el estado cambia, Compose vuelve a dibujar solo lo que cambió.
- **Retrofit**: librería que convierte una **interfaz** con anotaciones (`@GET("api/asistentes")`) en llamadas HTTP.
  Convierte el JSON a objetos Kotlin con **Gson**; **OkHttp** pone el token en cada petición (`TokenInterceptors.kt`) y lo
  renueva si caduca.
- **Corrutinas (`suspend`)**: permiten hacer trabajo lento (red, base de datos) **sin bloquear la pantalla** y sin
  callbacks anidados. Una función `suspend` se pausa y continúa después. Los ViewModels las lanzan en `viewModelScope`, que se
  cancela solo cuando la pantalla muere.
- **Flow / StateFlow**: flujo de valores en el tiempo; Compose lo observa (`collectAsStateWithLifecycle`) y se redibuja.
- **Room**: base SQLite del teléfono con consultas verificadas al compilar. Es lo que hace posible el modo sin conexión.
- **WorkManager**: ejecuta trabajo diferido y fiable (enviar lo pendiente cuando vuelve la red, aunque la app esté cerrada).
- **Inyección de dependencias manual** (`AppContainer`): un objeto que construye y entrega las piezas; más fácil de leer que Hilt para este tamaño.

### Librerías: las que trae un proyecto nuevo y las que agregamos

| Origen | Librería | Para qué |
|---|---|---|
| **Plantilla de Android Studio** | `core-ktx`, `lifecycle-runtime-ktx` | Extensiones de Kotlin y ciclo de vida |
| | `activity-compose` | Abrir Compose desde la `Activity` |
| | Compose BOM, `ui`, `material3`, `ui-tooling-preview` | Interfaz y Material 3 |
| | JUnit, Espresso, `ui-test-junit4` | Pruebas |
| **Agregadas por el equipo** | `navigation-compose` | Moverse entre pantallas |
| | `lifecycle-viewmodel-compose` | ViewModels en Compose |
| | Retrofit + Gson + OkHttp | Conexión con el backend |
| | Room + KSP | Base local offline |
| | WorkManager | Sincronización en segundo plano |
| | DataStore + Android Keystore | Guardar tokens cifrados |
| | CameraX + ML Kit | Leer QR con la cámara |
| | ZXing | Dibujar el QR del asistente |
| | Play Services Location | GPS referencial |
| | `kotlinx-coroutines-test`, MockWebServer | Pruebas de ViewModels y de la red |

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

- **COORDINADOR** — Docente / Coordinador(a) de Proyecto: registra, ve el quórum, imprime credenciales y administra proyectos, eventos y asistentes en la pestaña **Gestión**.
- **MONITOR** — Estudiante voluntario: registra asistencia y encuestas en terreno.
- **ASISTENTE** — Beneficiario comunitario: se registra desde la pantalla de ingreso ("Soy asistente · Crear mi cuenta"), ve los
  talleres próximos con sus cupos, se inscribe y muestra su código QR (`ASISTENTE_{id}`) al monitor. Necesita conexión para
  registrarse e inscribirse y **no sincroniza** (el servidor se lo niega). No se guardan nombre ni correo en la base: solo un código sintético.
- Los beneficiarios **no son usuarios de la app**: son registros del catálogo que el monitor marca con el QR.

Un usuario sin ninguno de esos dos grupos no puede entrar (la app lo explica). Los grupos que usa la app para
decidir qué pantallas mostrar vienen del token **sin verificar su firma**: es solo presentación. La autorización
real la hace el servidor en cada petición.

## Arquitectura: carpetas y decisiones de diseño

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
| `POST/PUT/DELETE api/proyectos[/{id}]` | mantenedor de proyectos (solo COORDINADOR) |
| `POST/PUT/DELETE api/eventos[/{id}]` | mantenedor de eventos (solo COORDINADOR) |
| `GET/POST api/asistentes`, `PUT/DELETE api/asistentes/{id}` | mantenedor de asistentes (solo COORDINADOR) |
| `GET api/mi/perfil`, `GET api/mi/talleres`, `POST/DELETE api/mi/talleres/{id}/inscripcion` | portal del asistente (solo ASISTENTE) |

Cada resultado de un lote es `ACEPTADO`, `DUPLICADO` o `RECHAZADO` (con `motivo`). Un registro rechazado no frena al
resto. Los nombres de campo se comprobaron mecánicamente contra los DTO del backend (`Dto.kt`).

## Pruebas

153 pruebas unitarias, 0 fallos. Cubren, sin emulador:

- **Autenticación** (`AuthFlowTest`): login, errores del backend, rol inválido, y los cinco escenarios de renovación
  de token contra un servidor simulado (renueva y repite, sesión terminada, sin red, error 500, sin bucle).
- **Contrato** (`SyncApiContractTest`): el JSON exacto que la app envía y recibe.
- **Sincronización** (`SyncEngineTest`, 30 casos): idempotencia, lotes parciales, cortes de red a mitad, orden
  asistencias→encuestas, usuario con sesión, reapertura de fallidos tras un catálogo nuevo.
- **Alta en terreno** (27 casos): alta y marcación atómicas (si falla una, no queda la otra), ids provisionales
  distintos, reemplazo por el id real con sus asistencias, reenvío tras perder la respuesta, alta rechazada, y el
  JSON exacto del endpoint nuevo.
- **Mantenedores** (`GestionTest`, 15 casos): validación de los formularios (incluida la conversión de la hora local a UTC),
  crear/editar/eliminar con su recarga, y el mensaje del servidor ante un 409 o un 403.
- **Reglas de registro**, **ViewModels** (antirrebote del QR, login, sesión, quórum), **QR** y **JWT**.

Se comprobó, rompiendo la regla a propósito, que las pruebas del motor detectan que `DUPLICADO` deje de contar
como sincronizado.

## Lo que NO está comprobado

Las pruebas unitarias son verificación **sin dispositivo**. Además, la versión anterior a la alta en terreno se
probó a mano en un teléfono físico (Xiaomi 23090RA98G) contra el backend y Cognito reales: inicio de sesión como
monitor, descarga del catálogo, marcación en modo avión, envío automático con notificación al volver el Wi-Fi, y la
marcación visible en el panel web (clasificada "fuera del radio", lo que confirma que el GPS envió coordenadas).

**Sin probar todavía en un teléfono**:

- Las pantallas de **Gestión** (los tres mantenedores): compilan y tienen pruebas unitarias de su lógica, pero no se han abierto en un teléfono.
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
| _(nombre completo)_ | [bx1423](https://github.com/bx1423) | Pruebas de los roles del usuario (`UserInfoTest`) |
| _(nombre completo)_ | [Monnacohu](https://github.com/Monnacohu) | Pruebas del formateo de fechas y decimales (`FormatoTest`) |
