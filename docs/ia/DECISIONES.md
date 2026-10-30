# Decisiones de diseño

Cada decisión se registra con su porqué, para que la IA y el equipo no la reabran sin motivo. Si una cambia, se agrega una
nueva fila indicando cuál reemplaza.

| # | Decisión | Por qué |
|---|---|---|
| D1 | **Offline-first:** todo se guarda primero en Room y se envía después | El caso exige operar sin señal en barrios periféricos |
| D2 | El **`uuid` lo genera el teléfono** y es la clave de idempotencia | Reenviar un lote es siempre seguro: el servidor responde `DUPLICADO` a lo ya recibido |
| D3 | Sincronización **por lotes** con resultado por registro (`ACEPTADO`, `DUPLICADO`, `RECHAZADO`) | Un registro malo no frena al resto |
| D4 | Ni la app ni la web hablan con Cognito: solo con el **backend** | Un único punto de entrada donde aplicar seguridad y reglas |
| D5 | Roles = **grupos de Cognito** (`cognito:groups`), autorizados en el servidor | La interfaz solo presenta; no se confía en lo que el cliente muestre |
| D6 | **Sin datos personales:** el asistente es un código sintético y un tipo; lo genera el servidor | Restricción §5.1 del caso; el cliente no puede imponer el código |
| D7 | El **asistente también es un rol** que se registra, se inscribe y muestra su QR | Corrección de requisito hecha por el equipo: es un perfil de usuario del caso |
| D8 | El GPS **clasifica** (válida, fuera de radio, sin ubicación) pero **nunca rechaza** | Es referencial; sin señal de GPS igual hay que registrar |
| D9 | Alta de quien llega sin inscripción con **id provisional negativo** que el servidor reemplaza | Permite marcar sin conexión y reasignar al sincronizar |
| D10 | Base versionada con **Flyway** y `ddl-auto: none` | El esquema es reproducible y revisable; Hibernate no lo modifica |
| D11 | **API Gateway** con authorizer JWT delante de la EC2 | El token se valida antes de llegar a nuestro código |
| D12 | Solo se borra lo que **no tiene asistencias** (409 en otro caso) | No se pierde evidencia; el catálogo incremental no propaga borrados |
| D13 | Web con rutas por `#` y sesión en `sessionStorage` | Sirve en hosting estático sin reglas; la sesión muere al cerrar la pestaña |
| D14 | Mantenedores de la app **requieren conexión** | Son tareas de oficina del coordinador; el registro en terreno sigue siendo offline |
| D15 | Identidad visual azul de la marca, **claro por defecto** y modo oscuro opcional | Coherencia con la web y buena legibilidad en terreno |
