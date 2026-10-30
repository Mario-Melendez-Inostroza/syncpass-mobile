# SyncPass (app Android) — guía para la IA

App del monitor y del coordinador, y portal del asistente. Registra asistencia con o sin conexión y sincroniza por lotes con el backend.

**Stack:** Kotlin · Jetpack Compose · Material 3 · MVVM · Room · Retrofit + OkHttp + Gson · WorkManager · CameraX + ML Kit · minSdk 26

Antes de tocar nada lee `docs/ia/REGLAS.md` y `docs/ia/CONTEXTO.md`; las decisiones ya tomadas están en `docs/ia/DECISIONES.md` y
el estado del proyecto en `docs/ia/ROADMAP.md`.

## Comandos

```
.\gradlew.bat :app:testDebugUnitTest    # pruebas unitarias (JVM, sin emulador)
.\gradlew.bat :app:assembleDebug        # APK de debug
.\gradlew.bat :app:lintDebug            # análisis estático
```

## Estructura

```
app/src/main/java/com/android/syncpass/
  ui/          Compose + ViewModels, una carpeta por pantalla (asistencia, asistente, gestion, eventos, perfil, theme …)
  sync/        SyncEngine (lógica pura), SyncWork (WorkManager)
  data/        RegistroRepository, Mappers, local/ (Room)
  core/        auth/, network/ (Retrofit, DTO), qr/, TemaPrefs
  AppContainer inyección de dependencias manual
```

## Convenciones

- **MVVM:** la pantalla (Compose) solo dibuja y avisa eventos; el ViewModel guarda el estado (`StateFlow`) y ejecuta acciones; los
  repositorios y las APIs de Retrofit son la capa de datos.
- **Offline-first:** toda acción de registro se guarda primero en Room y se envía después; ninguna acción de terreno exige red.
- El `uuid` de cada registro lo genera el teléfono.
- Cambiar el esquema de Room exige una `Migration` y su prueba; no se usa `fallbackToDestructiveMigration`.
- Botones principales de 56 dp, áreas táctiles de al menos 48 dp, textos en español simple.
- Los colores y formas salen del tema (`ui/theme`), no se escriben colores sueltos en las pantallas.

## Antes de dar algo por terminado

1. `:app:testDebugUnitTest`, `:app:assembleDebug` y `:app:lintDebug` sin errores.
2. Hay pruebas para lo nuevo y fallan si se rompe la regla a propósito.
3. No se versionó ningún secreto, dato personal ni archivo de IDE.
4. El README y `docs/ia/ROADMAP.md` reflejan lo hecho, y lo no comprobado está declarado.
