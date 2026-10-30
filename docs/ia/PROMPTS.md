# Plantillas de instrucciones

Estructura que usamos para pedir cosas a la IA. Cuatro campos siempre: **contexto, objetivo, restricciones y criterio de
terminado**. Cuanto más concreto el criterio, menos idas y vueltas.

## Nueva funcionalidad

```
Contexto:     [dónde encaja; qué archivos o pantallas toca; qué requisito del caso cubre]
Objetivo:     [qué debe poder hacer el usuario]
Restricciones: [no tocar X; respetar docs/ia/REGLAS.md; sin datos personales]
Terminado cuando: [comportamiento observable + pruebas + comando de verificación en verde]
Antes de escribir código: propón un plan y espera mi aprobación.
```

## Corregir un error

```
Síntoma:      [qué se ve, con captura o mensaje exacto]
Esperado:     [qué debería pasar]
Pistas:       [cuándo empezó, qué cambió, ya probé X]
Pide:         primero la causa raíz con evidencia (registros o código); después el arreglo mínimo y una prueba que lo cubra.
```

## Escribir pruebas

```
Objetivo:     cubrir [clase o endpoint] con casos felices, bordes y errores.
Restricciones: sin red ni emulador; seguir el estilo de las pruebas existentes; no modificar el código de producción.
Terminado cuando: las pruebas nuevas pasan y fallan si se rompe la regla a propósito (muéstralo).
```

## Revisión de seguridad y calidad

```
Revisa el cambio [rama o diff] buscando: autorización faltante, datos personales, secretos, validación de entrada,
errores de concurrencia y regresiones. Para cada hallazgo: archivo, escenario concreto de fallo y arreglo.
No reescribas código en esta pasada: solo informa.
```

## Despliegue y permisos (la persona ejecuta)

```
Necesito desplegar [cambio]. Explica los pasos y qué permisos hacen falta; las acciones de IAM, red y claves las ejecuto yo.
Después ayuda a comprobar el resultado con una prueba real (curl o navegador).
```

## Explicar para la defensa

```
Explica [módulo o flujo] como lo haría alguien que debe defenderlo oralmente: qué problema resuelve, cómo funciona
paso a paso, qué alternativas se descartaron y por qué. Usa el código real, no genérico.
```

## Ejemplo real: corrección de un requisito

Cuando la IA entendió que los asistentes no eran usuarios, la corrección fue directa:

```
Todos deben tener cuenta, estás revisando mal. El asistente también es un rol: puede registrarse, ver qué talleres hay,
inscribirse y, al llegar al evento, se le escanea el QR. Revisa bien las instrucciones del caso.
```

Resultado: decisión D7, un grupo nuevo en Cognito, endpoints `/api/mi/**`, pantallas en la web y en la app, y pruebas.
