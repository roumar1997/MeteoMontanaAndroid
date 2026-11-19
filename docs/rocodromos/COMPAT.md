# Rocódromos: compatibilidad con las versiones ya publicadas

> Estado: 2026-11-19. Bloquea el encendido de `gyms_enabled`. Léelo antes de publicar el backend en `main`.

## El problema

Una quedada en un rocódromo (V106) no tiene escuela: el servidor manda `"schoolId": null` (más `gymId` y `placeName`).
Las apps publicadas (Android 7.1.4 y 7.2.1, iOS 7.2 y 7.2.2) declaran `MeetupDto.schoolId` como `String` **no nulo**.
Al decodificar un `null` ahí, kotlinx.serialization lanza `JsonDecodingException`, y como la lista llega entera en una
sola respuesta, **se cae toda la lista de quedadas** (no solo la del rocódromo). Pasa igual con `GET /meetups/{id}` y
con `GET /meetups/by-conversation/{conversationId}` (el chat de la quedada).

`coerceInputValues = true` no lo salva: solo convierte `null` en el valor por defecto cuando el campo tiene uno, y
`schoolId` no lo tiene en las versiones viejas.

Los cambios de esta rama (`schoolId` nullable, `gymId`, `placeName`) arreglan las versiones **nuevas**. Las viejas
siguen ahí durante semanas.

## Qué tiene que hacer el backend antes de encender `gyms_enabled`

Para cualquier cliente que **no** mande la cabecera de capacidades nueva, **excluir las quedadas con `gymId`** de:

- `GET /api/meetups` (la lista),
- `GET /api/meetups/{id}` (responder 404 como si no existiera),
- `GET /api/meetups/by-conversation/{conversationId}` (404),

y, por la misma razón, de cualquier otro sitio que devuelva `MeetupDto` (alertas, notificaciones, invitación).
Quien no lo manda no sabe pintar un rocódromo: para él esas quedadas no existen.

Alternativa o complemento: subir la versión mínima soportada (`/app-version`, que ya tiene el gate 2.19.0) a la primera
versión con la cabecera. Es más drástico: obliga a actualizar a todos. Recomendación: filtrar por cabecera y no subir la
mínima.

Orden seguro de publicación:

1. Backend con el filtro por cabecera a `develop` y verificado en staging (con un cliente sin cabecera: la lista de
   quedadas sigue cargando aunque exista una quedada de rocódromo).
2. Apps nuevas en las dos tiendas.
3. Backend a `main`.
4. Encender `gyms_enabled` solo cuando las dos tiendas tengan la versión nueva.

## La cabecera

La app nueva manda en **todas** las peticiones (se añade en `ApiHttpClient`, `defaultRequest`):

```
X-Client-Features: gyms
```

- Constantes en shared: `CLIENT_FEATURES_HEADER = "X-Client-Features"` y `CLIENT_FEATURES = "gyms"`.
- El valor es una **lista separada por comas** de capacidades (hoy solo `gyms`), para poder añadir otras más adelante
  sin otra cabecera: el servidor debe comprobar con `contains` sobre la lista, no con igualdad.
- Las versiones <= 7.2.2 no la mandan. Ausente = cliente viejo = no enseñarle quedadas de rocódromo.
- Es una cabecera sin datos personales y no pasa por CORS (la PWA está deprecada).

Pseudocódigo del filtro (Java):

```java
boolean supportsGyms = Optional.ofNullable(request.getHeader("X-Client-Features"))
        .map(h -> Arrays.stream(h.split(",")).map(String::trim).anyMatch("gyms"::equals))
        .orElse(false);
meetups = supportsGyms ? all : all.stream().filter(m -> m.gymId() == null).toList();
```

## También con el interruptor apagado

El filtro no depende de `gyms_enabled`: aunque esté apagado, una quedada con `gymId` ya creada (por ejemplo en staging)
no debe llegar a un cliente viejo. Y mientras el interruptor siga apagado, el servidor ya rechaza **crear** quedadas
con `gymId` (`MeetupController`, `gate.ensureEnabled("gyms_enabled")`), así que en producción no existirán hasta
que se encienda.

## Otros contratos revisados (sin riesgo para versiones viejas)

- `GymDto`, `GymNearDto`, `GymReportDto`: endpoints nuevos, nadie viejo los llama.
- `MeetupDto.gymId` / `placeName` / `CreateMeetupRequestDto.gymId`: campos aditivos; `ignoreUnknownKeys = true` en las
  viejas los ignora.
- Interruptor: `gyms_enabled` viene `false` por V103. En la app nueva una clave que el servidor **no** mande cuenta como
  APAGADA (`FeatureFlags.isGymsEnabled`), al revés que el resto de funciones; así no sale nada sin que el servidor lo diga.
- Un valor de enum que la app no conoce (`kind`, `discipline`, `verification`, estado de un reporte) no rompe la
  decodificación: cae en un valor por defecto (los DTO llevan los enums como `String`).
