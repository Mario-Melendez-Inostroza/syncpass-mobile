# Flujo de trabajo con IA

Usamos **Claude Code** (Anthropic) como asistente de programación dentro del repositorio. No le pedimos "hazme la app":
trabajamos con un ciclo corto en el que **una persona decide y verifica** y la IA propone, escribe y comprueba.

## 1. Contexto persistente (lo que la IA lee siempre)

La IA no recuerda conversaciones anteriores, así que el contexto vive en archivos del repositorio:

| Archivo | Para qué sirve |
|---|---|
| `AGENTS.md` / `CLAUDE.md` (raíz) | Qué es el proyecto, comandos, estructura y convenciones. Es lo primero que lee |
| `docs/ia/CONTEXTO.md` | Resumen del caso: problema, roles, datos, metas y restricciones |
| `docs/ia/REGLAS.md` | Instrucciones y **restricciones** que la IA debe respetar |
| `docs/ia/ROADMAP.md` | Fases del proyecto, qué está hecho y qué falta |
| `docs/ia/DECISIONES.md` | Decisiones de diseño con su porqué, para no reabrirlas |
| `docs/ia/PROMPTS.md` | Plantillas de instrucciones que usamos por tipo de tarea |

## 2. Ciclo de cada tarea

```
 Especificar ──► Planificar ──► Implementar ──► Verificar ──► Revisar ──► Integrar ──► Documentar
 (persona)       (IA propone,    (IA, en pasos   (comandos    (persona    (rama + PR  (README y
                  persona        pequeños)       y pruebas)   lee el      + CI)       "no comprobado")
                  aprueba)                                    cambio)
```

1. **Especificar:** la persona dice el objetivo, lo que no se debe tocar y cómo se sabe que está terminado (plantilla en `PROMPTS.md`).
2. **Planificar:** la IA investiga el código y propone un plan; la persona lo aprueba o lo corrige **antes** de escribir código.
3. **Implementar:** en pasos pequeños, un repositorio a la vez (API, luego web, luego app), siguiendo `REGLAS.md`.
4. **Verificar:** la IA ejecuta compilación, pruebas y análisis estático, y muestra el resultado real. Además se prueba a mano
   (navegador, teléfono, `curl` contra el API Gateway).
5. **Revisar:** una persona lee el cambio completo. Se revisa con atención especial a la seguridad y a la corrección.
6. **Integrar:** rama de funcionalidad, Pull Request y GitHub Actions (lint, pruebas y compilación).
7. **Documentar:** README actualizado, con una sección honesta de lo que no se pudo comprobar.

## 3. Quién hace qué

| La persona | La IA |
|---|---|
| Define los requisitos y corrige cuando la IA entiende mal | Propone arquitectura y alternativas con sus costos |
| Aprueba planes y revisa cada cambio | Escribe código, pruebas y documentación |
| Ejecuta las acciones de permisos (IAM, red, claves) | Diagnostica errores leyendo registros y código |
| Prueba en el teléfono real y en el navegador | Ejecuta compilación, pruebas y lint, y reporta el resultado |
| **Es responsable** de lo que se entrega y debe poder explicarlo | Señala riesgos y lo que no pudo comprobar |

## 4. Cómo verificamos lo que escribe la IA

- **Se ejecuta, no se cree:** ninguna afirmación de "funciona" sin comando o prueba que lo respalde.
- **Pruebas que fallan a propósito:** se rompe una regla (por ejemplo un permiso) para comprobar que alguna prueba lo detecta.
- **Pruebas de contrato:** el JSON que envía la app se compara con el que espera el backend.
- **Prueba real contra la nube:** registro, login, permisos por rol e inscripción se probaron contra el API Gateway desplegado;
  un fallo de infraestructura (la instancia sin rol IAM) solo apareció así, no con las pruebas automáticas.
- **Revisión de secretos antes de publicar** un repositorio.

## 5. Riesgos conocidos y su control

| Riesgo | Control |
|---|---|
| La IA inventa o supone algo que no probó | Verificación ejecutada y sección "no comprobado" |
| Se filtran secretos o datos reales | `.gitignore`, revisión previa a publicar, regla 1 y 2 de `REGLAS.md` |
| Código que nadie entiende | Cada integrante debe poder explicar lo que entrega; los README explican la arquitectura |
| Cambios demasiado grandes | Pasos pequeños, un repositorio a la vez, revisión del cambio completo |
| Dependencias o versiones desconocidas | Versiones fijas y revisión de lo que se agrega |
