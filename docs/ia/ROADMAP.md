# Roadmap

Estado real del proyecto. La IA debe actualizar este archivo al cerrar cada fase y no marcar como hecho lo que no se probó.

Leyenda: ✅ hecho y verificado con pruebas · 🟡 hecho, falta probar en dispositivo o entorno real · ⬜ pendiente

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Análisis del caso: roles, datos, restricciones y arquitectura | ✅ |
| 1 | Backend base: contrato `{success, data, message}`, login y renovación de token contra Cognito | ✅ |
| 2 | Dominio y sincronización: catálogo, asistencias y encuestas por lotes, idempotencia, quórum | ✅ |
| 3 | App Android offline-first: Room, WorkManager, QR, GPS referencial, encuesta | ✅ pruebas · 🟡 teléfono |
| 4 | Panel web del coordinador: login, eventos, quórum en vivo | ✅ |
| 5 | Gestión: CRUD de proyectos, eventos y asistentes, CSV, informes de impacto, quórum mínimo | ✅ |
| 6 | Alta de asistentes en terreno (sin inscripción previa) | ✅ pruebas · 🟡 teléfono |
| 7 | Despliegue en AWS: EC2, API Gateway con authorizer JWT, Cognito, Supabase | ✅ |
| 8 | Rol ASISTENTE: registro, talleres, inscripción con cupos y credencial QR | ✅ (probado contra el API Gateway) · 🟡 app en teléfono |
| 9 | Mantenedores del coordinador en la app (proyectos, eventos, asistentes) | ✅ pruebas · 🟡 teléfono |
| 10 | Identidad visual de la app (paleta de marca, modo oscuro opcional) | 🟡 falta verla en teléfono |
| 11 | Integración continua con GitHub Actions (API y web) | ✅ corriendo en verde en cada push y Pull Request |
| 12 | Documentación para la defensa y archivos de contexto para la IA | ✅ |

## Pendiente

- ⬜ Probar en teléfono real: asistente, mantenedores, ícono, migración de Room v1 → v2 y apariencia.
- ⬜ Medir las metas del caso (tiempo de registro, tasa de respuesta); hoy son objetivos.
- ⬜ Horas de participación por estudiante y lista de quién asistió a cada evento (hoy solo conteos).
- ⬜ Recuperar contraseña desde la web.
- ⬜ Quórum mínimo visible en la app.
- ⬜ Validar `aud` y `token_use` del token en el backend (hoy un *id token* también se acepta).
- ⬜ Cerrar `/api/auth/registro` (crearía cuentas MONITOR sin control).
- ⬜ Propagar borrados del catálogo a los teléfonos ya sincronizados.
