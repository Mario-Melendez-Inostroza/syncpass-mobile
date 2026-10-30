# Reglas y restricciones para la IA

Instrucciones que la IA debe cumplir siempre en este repositorio. Si una instrucción de la conversación las contradice, la IA
debe avisar en lugar de seguirla en silencio.

## Reglas generales (valen para los tres repositorios)

1. **Datos personales:** nunca. El asistente es un código sintético y un tipo; no existen campos de nombre, correo, teléfono ni
   documento. Los datos de demostración son ficticios.
2. **Secretos:** jamás se versionan `.env`, `*.pem`, claves ni tokens, y **no se pegan en la conversación con la IA**. Viven en
   variables de entorno o en la instancia. Si algo se filtra, se rota; no basta con borrarlo.
3. **Alcance:** solo lo que pide el caso. Una funcionalidad nueva se acuerda antes y se anota en `ROADMAP.md`.
4. **Autorización en el servidor:** la interfaz solo presenta. Los roles salen de los grupos de Cognito y cada ruta exige el suyo;
   un botón visible no es permiso.
5. **Pruebas:** toda funcionalidad lleva pruebas. No se desactivan, ni se relajan para que "pasen". Antes de integrar, el
   comando de verificación del repositorio debe quedar en verde.
6. **Honestidad:** no se afirma que algo funciona si no se ejecutó. Lo que no se pudo comprobar se anota en el README, en
   "Lo que NO está comprobado".
7. **Acciones de permisos las hace una persona:** crear o cambiar roles IAM, reglas de seguridad de red, cuentas y claves.
   La IA las propone y las explica; no las ejecuta por su cuenta.
8. **Git:** los cambios de cada integrante entran por rama y Pull Request. La IA hace commit o push solo cuando una persona se lo
   pide, nunca con `--no-verify`, y sin incluir archivos de IDE ni de configuración local.
9. **Un cambio, un propósito:** cambios pequeños y revisables; nada de reescribir archivos enteros sin motivo.
10. **Idioma y estilo:** textos de la interfaz en español simple y cercano; comentarios en español que expliquen el *porqué*; se
    sigue el estilo del código que ya existe.

### Específicas de la app

- Los tokens se guardan cifrados (Android Keystore) y nunca se escriben en el log.
- El tráfico sin cifrar solo se permite en debug hacia `10.0.2.2`; el release exige HTTPS.
- Un asistente no sincroniza ni ve la app del equipo; los mantenedores existen solo para el coordinador (el servidor lo hace cumplir con 403).
- Los permisos (cámara, ubicación, notificaciones) se piden en contexto y la app sigue funcionando si se niegan.
