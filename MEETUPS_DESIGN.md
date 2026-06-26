# MEETUPS_DESIGN.md — Quedadas para escalar

> Diseño (sin implementar aún) de una **tercera pestaña** "Quedadas": quedar
> para escalar en una escuela, en uno o varios días, con chat de grupo,
> privacidad (abierta / solo seguidores / solo mujeres), caducidad automática
> y moderación (expulsar + denunciar). LEER antes de empezar a implementar.
>
> Decidido con Rodrigo el 2026-06-26. Bocetos en `meetups-mockups.html`
> (ábrelo en el navegador; estilo Cumbre, los 3 pasos).

---

## 0. Resumen en una línea

Una **quedada** = un **grupo de chat** (ya lo tienes) + escuela + fecha(s) +
privacidad + límite + foto, que **caduca y se borra solo** al pasar la fecha.

---

## 1. Qué hace la pestaña (spec funcional cerrado)

- **Tercera pestaña** en la barra inferior: `Tiempo · Escuelas · Quedadas`.
- **Listar** quedadas con **filtros**: por **escuela**, por **día**, y por
  **relación** (Todas / Sigo / Me siguen).
- **Crear** una quedada o **unirse** a una existente.
- Al crear se elige: **nombre**, **escuela**, **uno o varios días**,
  **privacidad**, **límite de gente** y, opcional, **foto del grupo**.
- **Chat de grupo**: habla todo el que se una (reutiliza los grupos de chat
  que ya existen en Firestore).
- **Privacidad por quedada** (3 opciones):
  - **Abierta** — cualquiera puede verla y unirse.
  - **Solo seguidores** — solo la ve / puede unirse quien sigue al creador.
    A los no-seguidores **no les aparece** en lista ni en búsqueda.
  - **Solo mujeres** — solo la ven / pueden unirse usuarias con género=Mujer.
- **Límite de gente**: un **número** exacto, o **"Abierto"** (sin tope).
- **Foto del grupo**: la pone **solo quien crea** la quedada.
- **Acceso directo al tiempo** de la escuela desde la quedada (abre el detalle
  de la escuela / su forecast).
- **Miembros**: lista con **foto de perfil** → tocar abre su perfil → seguir.
- **Caducidad**: cuando pasa el **último día** de la quedada, **al día
  siguiente se borra entera** (incluido el chat) y deja de aparecer en
  búsqueda. Ej.: quedada para sáb 28 + dom 29 → el lun 30 ya no existe.
- **Notificación opt-in**: "avísame si crean quedadas nuevas para esta
  escuela / estos días" → push + notificación in-app cuando aparezca una que
  encaje (por si las que hay no te valen y quieres enterarte de las nuevas).

### Moderación
- **Expulsar miembro**: solo el creador puede sacar a alguien (pierde acceso
  al chat y a la quedada al instante).
- **Denunciar usuario**: cualquier miembro puede reportar a otro. La denuncia
  **llega al admin (Rodrigo)** en su panel, con: a quién reportan, quién
  reporta, motivo y contexto (qué quedada / mensaje). Desde ahí el admin puede
  **eliminar la cuenta**, banear, o lo que considere.

---

## 2. Género (para "solo mujeres") — privacidad y anti-trampas

**Ni Google ni Apple nos dan el género** en el login (solo nombre, email,
foto). Hay que preguntarlo nosotros.

- **Campo opcional en el perfil**: `Mujer / Hombre / Prefiero no decirlo`.
  - **Por defecto sin definir.** No se fuerza en el primer login (RGPD =
    minimización + UX). Se pide solo cuando hace falta.
- **El género NUNCA se muestra en ningún sitio** — ni en el perfil propio
  visible, ni en perfiles públicos, ni en búsqueda. Es un dato **privado**
  que solo existe para la comprobación de "solo mujeres".
- **Dónde se guarda**: en **Postgres** (tabla de usuario del backend), en el
  lado **privado** (NO en `PublicProfileDto`). Lo escribe `PUT /api/me`.
- **El gate de "solo mujeres" lo hace el BACKEND** (server-side):
  - **Crear** una quedada "solo mujeres" → el backend comprueba que el
    creador tiene género=Mujer. Si no, error → la app muestra
    *"Indica tu género en tu perfil para crear quedadas de mujeres"* y lleva a
    editar perfil.
  - **Unirse** a una "solo mujeres" → el backend comprueba género=Mujer.
  - La app **nunca ve el género de nadie**; solo recibe permitido/denegado.
- **Límite asumido**: es **autodeclarado**, no verificable (ninguna app lo
  verifica de verdad). Un hombre podría poner "Mujer" para colarse → la red de
  seguridad real es **denunciar + admin** (expulsar / eliminar cuenta).

---

## 3. Cambio transversal: la FOTO de perfil siempre visible

> No es de esta pestaña, pero salió aquí y hay que hacerlo a la vez o antes.

Hoy la privacidad de perfil oculta cosas. Hay que garantizar que
**la foto de perfil y el @username NUNCA se ocultan** — ni en perfiles
privados, ni en búsqueda, ni en listas de seguidores ni en miembros de una
quedada. Lo privado es **bio, bloques/vías y diario**, no la identidad visual.
La gente tiene que reconocerse por la cara, no solo por el nombre.

- Revisar `PublicProfileDto` / reglas Firestore / pantallas de búsqueda y
  follow para que `photoUrl` + `username` siempre se devuelvan/pinten.

---

## 4. Arquitectura — dónde vive cada cosa

| Pieza | Dónde | Por qué |
|---|---|---|
| **Entidad Quedada** (escuela, días, privacidad, límite, foto, miembros) | **Postgres** (backend) | El filtrado por follows/género/escuela es server-side y relacional; encaja con contributions/journal |
| **Chat del grupo** | **Firestore** (conversación de grupo existente) | Ya tienes la infra de grupos (`conversations` + `messages`) |
| **Género del usuario** | **Postgres** (lado privado del perfil) | Nunca se expone a otros clientes; el gate es server-side |
| **Denuncias** | **Postgres** + panel admin | Igual que submissions/contributions |
| **Notificaciones de quedada nueva** | Backend (FCM + notif in-app) | Reutiliza `FcmService` + `NotificationService` |
| **Foto del grupo** | Firebase Storage | Igual que fotos de piedras/perfil |

**Clave**: la **lista de quedadas la sirve el backend** (`GET /api/meetups`),
NO una query directa a Firestore — porque el backend es quien sabe a quién
sigues y tu género, y así puede **filtrar la visibilidad** (solo seguidores /
solo mujeres) sin filtrar datos a quien no debe. El chat sí es Firestore en
tiempo real.

---

## 5. Modelo de datos (Postgres — propuesta)

```
meetups
  id              UUID PK
  school_id       FK -> schools(id)
  name            VARCHAR(80)
  privacy         VARCHAR(16)   -- OPEN | FOLLOWERS | WOMEN
  member_limit    INT NULL      -- NULL = abierto (sin tope)
  photo_url       VARCHAR NULL
  creator_uid     VARCHAR        -- FK lógico al usuario
  conversation_id VARCHAR        -- id del grupo de chat en Firestore
  created_at      TIMESTAMP
  last_day        DATE           -- el día más tardío de la quedada
  expires_at      TIMESTAMP      -- last_day + 1 día (para el borrado)

meetup_days        -- una quedada puede ser varios días
  meetup_id   FK -> meetups(id) ON DELETE CASCADE
  day         DATE
  PRIMARY KEY (meetup_id, day)

meetup_members
  meetup_id   FK -> meetups(id) ON DELETE CASCADE
  uid         VARCHAR
  joined_at   TIMESTAMP
  PRIMARY KEY (meetup_id, uid)

meetup_alerts      -- notificación opt-in "avísame si crean nuevas"
  id          UUID PK
  uid         VARCHAR
  school_id   FK NULL    -- NULL = cualquier escuela
  days_csv    VARCHAR NULL  -- ISO 1-7 que le interesan, NULL = cualquiera

reports            -- denuncias de usuarios
  id              UUID PK
  reported_uid    VARCHAR
  reporter_uid    VARCHAR
  reason          VARCHAR(300)
  context_type    VARCHAR(16)   -- MEETUP | CHAT | PROFILE
  context_id      VARCHAR NULL  -- meetup_id / conversation_id
  status          VARCHAR(16)   -- PENDING | RESOLVED | DISMISSED
  created_at      TIMESTAMP

-- En la tabla de usuario existente:
users
  ... (existente)
  gender   VARCHAR(16) NULL   -- WOMAN | MAN | UNSPECIFIED. PRIVADO, nunca en PublicProfileDto.
```

---

## 6. Endpoints backend (propuesta)

**Públicos/auth (usuario):**
| Endpoint | Qué hace |
|---|---|
| `GET /api/meetups?schoolId=&date=&relation=` | Lista quedadas NO caducadas, filtrando visibilidad (open / followers / women) según quién pregunta. `relation` = all/following/followers |
| `POST /api/meetups` | Crear. Si `privacy=WOMEN`, comprueba género del creador. Crea la conversación de grupo (Admin SDK) y fija `expires_at` |
| `POST /api/meetups/{id}/join` | Unirse. Comprueba privacidad (followers/women/límite). Añade a `meetup_members` + `participants` de la conversación |
| `POST /api/meetups/{id}/leave` | Salir |
| `POST /api/meetups/{id}/kick` `{uid}` | Expulsar (solo creador) |
| `GET /api/meetups/{id}` | Detalle + miembros (con foto/username, NUNCA género) |
| `PUT /api/me` (ampliado) | Guardar `gender` (privado) |
| `GET/PUT /api/me/meetup-alerts` | Gestionar las alertas opt-in |
| `POST /api/reports` | Denunciar a un usuario |

**Admin:**
| Endpoint | Qué hace |
|---|---|
| `GET /api/admin/reports` | Cola de denuncias pending |
| `POST /api/admin/reports/{id}/resolve\|dismiss` | Revisar denuncia |
| `POST /api/admin/users/{uid}/ban` (o reutilizar borrado) | Eliminar/banear cuenta |

**Caducidad** (no es endpoint): `@Scheduled` diario (como
`ForecastPrefetchScheduler` / `WeekendAlertScheduler` ya existentes) que borra
las quedadas con `expires_at <= now` y borra su conversación de Firestore vía
Admin SDK. El `ON DELETE CASCADE` limpia días y miembros.

---

## 7. Reglas de visibilidad (resumen)

- **OPEN**: la ve cualquiera autenticado.
- **FOLLOWERS**: la ve el creador y **quien sigue al creador** (el backend
  consulta el grafo de follows). A los demás `GET /api/meetups` ni se la
  devuelve.
- **WOMEN**: la ve el creador y usuarias con `gender=WOMAN`. El backend lo
  comprueba; nunca expone el género.
- **Chat (Firestore)**: el grupo se crea con Admin SDK y `participants` =
  miembros. Las reglas de mensajes de grupo ya existen (autorizan por
  `participants`). Al unirse/expulsar, el backend actualiza `participants`.

---

## 8. Notificaciones "quedada nueva"

1. Al crear una quedada, el backend busca `meetup_alerts` que encajen
   (misma escuela y/o algún día coincidente) y que pertenezcan a usuarios que
   **podrían verla** (respetando privacidad: no avises de una "solo mujeres" a
   un hombre, ni de una "solo seguidores" a quien no sigue).
2. Para cada match: notificación in-app (`NotificationService`) + push
   (`FcmService`), con deep-link a la quedada.

---

## 9. Bocetos

Ver **`meetups-mockups.html`** (abrir en navegador). Tres pantallas en estilo
Cumbre (papel/tinta/terracota):

1. **Lista + filtros** — chips Escuela/Día + segmento Todas/Sigo/Me siguen +
   "CREAR QUEDADA" + tarjetas (foto, escuela + tiempo, días, nº miembros +
   límite, etiqueta de privacidad ABIERTA/SEGUIDORES/SOLO MUJERES, UNIRME).
2. **Detalle** — foto del grupo, nombre + creador + caducidad, fila escuela
   con "VER TIEMPO", días, privacidad, miembros con **foto** → perfil,
   "UNIRME" + "ABRIR CHAT DEL GRUPO".
3. **Crear** — foto (solo creador), nombre, escuela, días (multi), privacidad
   (TODOS / SOLO SIGO / SOLO MUJERES), límite (número o ABIERTO), toggle de
   aviso de quedadas nuevas.

---

## 10. Fases de implementación (propuesta)

> Backend primero (es la puerta de autorización), luego shared, luego Android,
> luego iOS. Pedir OK antes de cada commit/merge (testers en vivo).

- **Fase 0 — Foto de perfil siempre visible** (transversal, pequeña): asegurar
  `photoUrl`+`username` nunca se ocultan (backend DTO + reglas + UI búsqueda).
- **Fase 1 — Backend Quedadas (core)**: migración Postgres (meetups, days,
  members, gender en users), `GET/POST /api/meetups`, join/leave, creación de
  conversación de grupo vía Admin SDK, `@Scheduled` de caducidad. Tests.
- **Fase 2 — Género + gate "solo mujeres"**: `PUT /api/me` con gender privado,
  comprobación server-side en create/join WOMEN. (Probar en staging.)
- **Fase 3 — shared (KMP)**: modelos + use cases + API Ktor para quedadas y
  alertas; exponer en `IosDependencyContainer`.
- **Fase 4 — Android UI**: tercera pestaña, lista+filtros, detalle, crear,
  unirse, expulsar, acceso al tiempo, miembros→perfil, chat (reusa grupos).
- **Fase 5 — Moderación**: denunciar (usuario) + sección "Denuncias" en panel
  admin + acción banear/eliminar cuenta.
- **Fase 6 — Notificaciones** "quedada nueva" (alerts opt-in + push).
- **Fase 7 — iOS**: paridad EXACTA de todo lo anterior (SwiftUI), verificado
  por CI.

---

## 11. Decisiones abiertas (resolver al empezar)

- ¿La lista de quedadas se refresca con **pull-to-refresh** (simple, MVP) o
  hace falta tiempo real? (El chat sí es tiempo real; la lista probablemente
  no lo necesita.)
- ¿Se puede **editar** una quedada tras crearla (cambiar día/límite) o solo
  borrarla y recrearla? (MVP: solo borrar/recrear.)
- ¿Tope máximo de miembros aunque sea "Abierto"? (p. ej. cap a 50 por sanidad.)
- ¿"Solo mujeres" requiere también que el **creador** sea mujer SIEMPRE? (Sí,
  decidido: no puedes crear "solo mujeres" si no eres mujer.)
```
