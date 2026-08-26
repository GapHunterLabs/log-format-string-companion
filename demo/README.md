# Cómo probar este plugin

Cuando un programa escribe mensajes en su "registro de actividad"
(como un diario de lo que va pasando), a veces esos mensajes tienen
espacios reservados para datos que se completan después. Este plugin
avisa cuando la cantidad de espacios reservados no coincide con la
cantidad de datos que se están pasando — un error real y común.

## Qué hacer

1. En el panel de la izquierda, abrí el archivo **`OrderLogger.java`**
   (dentro de `src` → `main` → `java` → `com` → `acmecorp` →
   `orders`).
2. Mirá los 3 bloques de código, uno por uno.

## Qué deberías ver

- En el primer bloque (`logMismatch`): **debería aparecer un aviso**
  — el mensaje tiene 1 espacio reservado pero se le están pasando 2
  datos.
- En el segundo bloque (`logCorrect`): **no debería aparecer ningún
  aviso** — la cantidad coincide bien.
- En el tercer bloque (`logWithException`): **tampoco debería
  aparecer ningún aviso**, aunque a simple vista parezca igual que el
  primero — este es el caso más importante de probar. Ahí el "dato
  extra" es en realidad un error/excepción que se registra aparte,
  no un dato del mensaje, así que es correcto que no haya aviso.

## Si algo no se ve así

Sacá la captura igual, y avisame qué bloque no coincide con lo de
arriba (especialmente si el tercer bloque muestra un aviso que no
debería tener).
