# Backend de rocódromos, ronda 3: CONTRATO FINAL

Rama `feature/rocodromos-r3` del backend, migraciones V111 a V114, desplegado en **staging** (`develop`).
Todo es aditivo: apps viejas siguen funcionando (campos nuevos opcionales; las listas vienen `[]`, nunca ausentes).
Muestras exactas en `MeteoMontanaAPI/api/src/test/resources/contract/` (`gyms.json`, `gym-report.json`,
`gym-sessions.json`, `meetups.json`): copiarlas idénticas al repo de las apps.

## 1. Quedadas (`/api/meetups`)

`POST /api/meetups` acepta, además de lo de siempre (todos opcionales):

```json
{ "gymId": 5, "name": "Sábado de cuerda", "days": ["2026-11-28"], "discipline": "ROPE",
  "gradeMin": "6a", "gradeMax": "6c+", "partnersWanted": 1,
  "requiredGear": [{"item": "ROPE", "qty": 1}, {"item": "QUICKDRAWS", "qty": 12}] }
```

- `discipline`: llegan `BOULDER|ROUTE|BOTH` como siempre; **`ROPE` se guarda como `ROUTE` y `ANY` como `BOTH`**
  (la respuesta devuelve siempre los valores de siempre, para no romper apps viejas).
- `gradeMin/gradeMax`: grado francés 3 a 9c+ (`6a+`, `7b`...). Se normaliza a minúsculas; 400 si no es válido o
  min > max.
- `partnersWanted`: 1 a 20 (1 = cordada). No sustituye a `memberLimit`.
- `requiredGear`: máx. 4. `item` = `ROPE | GRIGRI | QUICKDRAWS | CRASHPAD`, `qty` 1 a 50. Repetidos se suman.
  Mapean a las claves de «Mi material»: ROPE=`cuerda`, GRIGRI=`grigri`, QUICKDRAWS=`cintas`, CRASHPAD=`crashpads`.
- **No hay edición posterior** de estos campos (PATCH sigue siendo solo la descripción).

Respuesta (`MeetupDto`), campos nuevos al final:

```json
{ "...": "...", "gymId": 12, "placeName": "Zarzalejo",
  "gradeMin": "6a", "gradeMax": "6c", "partnersWanted": 2,
  "requiredGear": [{"item": "ROPE", "qty": 1}, {"item": "QUICKDRAWS", "qty": 12}],
  "gearCoverage": [{"item": "ROPE", "required": 1, "provided": 1, "ok": true},
                   {"item": "QUICKDRAWS", "required": 12, "provided": 6, "ok": false}] }
```

`gearCoverage` suma el material de los apuntados: el que dejó en la quedada (`PUT /api/meetups/{id}/my-gear`)
o, si no lo ha tocado, el de su perfil. Cuerda/gri-gri cuentan 1 por persona que los lleva. Sin requisitos:
`gradeMin/gradeMax/partnersWanted = null`, `requiredGear = []`, `gearCoverage = []`.

**Chat de la quedada**: ya existía. Al crear la quedada se crea un grupo de chat (Firestore) y su id va en
`conversationId`; los que se apuntan se añaden a sus participantes. Abrir la quedada desde el chat:
`GET /api/meetups/by-conversation/{conversationId}`. No hizo falta cambiar nada.

## 2. Escalas de rocódromo

`GymDto` (lista y ficha) lleva ahora `phone` y `scale` (null si no se conoce):

```json
{ "phone": "+34 645 511 702",
  "scale": {"type": "COLORS", "levels": [
     {"name": "blanco", "hex": "#FFFFFF", "gradeFrom": null, "gradeTo": null},
     {"name": "verde", "hex": "#2E9E4F", "gradeFrom": "5a", "gradeTo": "5c"}]} }
```

- `type`: `COLORS` (niveles ordenados de fácil a difícil) o `GRADES` (sin niveles, `levels: []`).
- `GET /api/gyms/{id}/scale` (público): la escala, o **404** si el rocódromo no tiene.
- `PUT /api/admin/gyms/{id}/scale` (admin): body = la escala; 2 a 20 colores, nombre único (30 car.), `hex`
  `#RRGGBB`, `gradeFrom/gradeTo` opcionales (francés, from <= to). Devuelve el `GymDto`.
- `DELETE /api/admin/gyms/{id}/scale` (admin): la borra.
- **Propuesta comunitaria**: `POST /api/gyms/{id}/reports` con `issues:["SCALE"]` y `scale:{...}` (las dos cosas
  juntas, si no 400). El reporte (`GymReportDto`) devuelve `scale`. El admin lo aplica con
  `POST /api/admin/gyms/reports/{id}/apply` y la escala queda en la ficha. Cuenta para la cuota de 5 reportes/día.
- Datos cargados (V114): **solo Sputnik** (7 centros): blanco, verde, amarillo, azul, morado, rojo, negro (orden
  confirmado por Álvaro; **sin equivalencia a grados**, no hay fuente oficial). Arkose, Indoorwall, Climbat,
  Sharma, Bloc District, The Climb y Boulder Zone: sus webs no publican la escala, no se ha cargado nada.

## 3. Sesiones con lo hecho

`GymSessionDto` lleva `ascents` (`[]` si nada):

```json
{ "id": 11, "gymId": 5, "date": "2026-11-19", "startedAt": "2026-11-19T18:00:00Z", "source": "MANUAL",
  "ascents": [{"key": "verde", "count": 5}, {"key": "azul", "count": 2}] }
```

- `PUT /api/gyms/sessions/{sessionId}/ascents` (sesión propia; 404 si es ajena). Body
  `{"ascents": [{"key":"verde","count":5}]}`. **Sustituye** todo lo anterior (`[]` lo borra). Devuelve la
  `GymSessionDto`. Reglas: con escala de COLORS en el rocódromo, `key` debe ser uno de sus colores (se guarda con el
  nombre de la escala); sin escala, grado francés o nombre corto; con escala GRADES, grado francés. `count` 1 a 999,
  máx. 30 claves, repetidas se suman. 400 si no cuadra. Va tras el interruptor `gyms_enabled`.
- `GET /api/gyms/sessions` y `POST /api/gyms/{id}/sessions/today` ya devuelven `ascents`.
- **Estadísticas**: `GET /api/users/{identifier}/gym-ascents?period=WEEK|MONTH|YEAR&date=2026-11-19&gymId=5`
  (todo opcional; por defecto WEEK de hoy). Misma privacidad que `gym-stats` (404 si perfil privado y no eres tú ni
  seguidor). Semana = lunes a domingo.

```json
{ "period": "WEEK", "from": "2026-11-16", "to": "2026-11-22", "sessions": 2, "total": 7,
  "items": [{"key": "verde", "count": 5}, {"key": "azul", "count": 2}] }
```

## 4. Teléfono
`phone` (texto libre, máx. 40) en `GymDto`; editable por admin con `PUT /api/admin/gyms/{id}` (`"phone": "..."`).
Cargados desde las webs oficiales (V114): 7 Sputnik, Arkose Cuatro Caminos y Bloc District Marina/Tetuán. El resto,
null.

## 5. Datos
King Kong Climbing Gym (`madrid-king-kong-climbing-gym`) marcado `closed = true` (V114).

## 6. Privacidad
La política pública vive en la web (otro repo); textos nuevos en `PRIVACIDAD-ROCODROMOS.md`.

## 7. Pendiente tras la ronda 3 (apps)

- **Fotos aprobadas en la ficha**: `GymDto` no trae fotos. Hoy «Añadir foto» solo envía un reporte (`OTHER` + foto) y no se muestra ninguna foto ni «Denunciar foto». Para mostrarlas hace falta: lista `photos: [{id, url, authorUid}]` en `GymDto` (solo aprobadas) y `POST /api/gyms/{id}/photos/{photoId}/report` (denuncia) con su cola de admin.
- **Chat de rocódromo**: no necesita servidor. Usa `school_chats/gym-{id}/messages` de Firestore (las reglas actuales aceptan cualquier id, texto ≤ 500, solo el autor borra). Si se quiere TTL o moderación propia, habría que sacarlo a su colección.
- **Hora de la quedada** (la maqueta A enseña «18:30»): no hay campo en `POST /api/meetups`; no se envía.
- **Detalle offline de quedadas**: la caché SQLDelight no guarda `gymId`, grado, cordada ni material (solo el detalle online los ve). Si se quiere offline, subir el esquema (migración local).

## 8. Ronda 4: vías y boards en `ascents` (rama `feature/rocodromos-r4`, ya en `develop`/staging)

`PUT /api/gyms/sessions/{id}/ascents` acepta, además de colores y grados, claves con prefijo (no dependen de la escala del rocódromo; aditivo, las apps viejas no las mandan):

- `route:<partner|solo>:<sent|project>:<grado>` (cuerda con compañero o autoaseguro; encadenada o proyecto), por ejemplo `route:partner:sent:6a+`.
- `board:<kilter|tension|moonboard>:<grado>[:<inclinación 0-90>]`, por ejemplo `board:kilter:6c:40`.

Grado = francés de `FrenchGrade` (3 a 9c+). Cualquier otra forma con esos prefijos da 400. Entran en el máximo de 30 claves y en `gym-ascents` como una clave más (las apps las distinguen con `GymAscentKeys`). Tests: `GymScaleAndAscentsTest` (2 nuevos). No hay migración (`ascent_key` ya es VARCHAR(30); la clave más larga mide 25). Staging tiene af8a22e desplegado (deployment `success`, 2026-11-20 12:35 UTC). Pendiente: PUT real desde un cliente con sesión y `POST /api/gyms/{id}/sessions` con fecha para entrenos de días pasados (hoy solo `sessions/today`).

## 9. Ronda 5 (ya en develop/staging, ramas feature/rocodromos-r5*)

- **Grado doble** en vías y boards: `route:partner:sent:6b+/6c`, `board:kilter:6a/6a+:40`. Solo dos grados CONTIGUOS (`FrenchGrade.normalizeOrDouble`); si no, 400.
- `DELETE /api/gyms/sessions/{sessionId}` (204; 404 si es ajena): borra la sesión con lo hecho. Las apps lo llaman al editar un entreno a cero.
- `GET /api/users/{identifier}/gym-summary` (misma privacidad que gym-stats): `{sessions, gyms, ascents, maxGrade, byGym:[{gymId,name,sessions,ascents,maxGrade}], items:[{key,count}], byMonth:[{month,ascents}]}` (12 meses). El grado doble cuenta por su grado alto; los colores no tienen grado.
- **Hecho (V115, develop)**: «Tu rocódromo» en el servidor: `PUT/GET /api/me/home-gym` y `homeGym` en `gym-summary` (muestra `summary` en `gym-sessions.json`). **Pendiente**:  El desglose por rocódromo no distingue bloques/vías (en «Ascensiones» solo «Todo» lo muestra).

## 10. Ronda 7b: orden de Ascensiones y cuenta de publicaciones (backend `develop` 73963b4, staging `success`)

- **V116 `user_ascent_order`** (`user_uid` PK, `order_csv`, `updated_at`; cae con el usuario). `GET/PUT /api/me/ascent-order` con `{order: "BOARDS,BLOCKS,ROUTES"}`; el servidor sanea (solo BLOCKS/ROUTES/BOARDS, sin repetir, mayúsculas) y, si no queda nada válido, no cambia nada.
- `gym-summary` lleva `ascentOrder` (aditivo, `null` si no lo cambió) con la misma privacidad que el resto del resumen. Muestras `summary`/`summaryEmpty` de `gym-sessions.json` actualizadas en los dos repos.
- **`GET /api/feed/count?uid=`** (uid o username; sin `uid` = el propio) → `{count}`: publicaciones que enseña el perfil (todas menos NEW_BLOCK/NEW_LINE, igual que la lista). 0 si el perfil es privado sin seguirlo o hay bloqueo. Muestra `feed-count.json`. Los posts viven en el backend (`feed_posts`), no en Firestore.
- Tests: `AscentOrderUseCaseTest`, `FeedCountTest`, `GetGymSummaryUseCaseTest` (2 nuevos), `GymContractTest`.

## 11. Ronda 8: desglose por rocódromo y board `system` (backend `develop` 667f0b7, staging `success`)

- `gym-summary` → `byGym[]` gana `items: [{key,count}]` (lo hecho en ESE rocódromo, con su escala) y `days: [{date,ascents}]` (más reciente primero, máx. 60). Aditivo: las apps viejas los ignoran. Muestra `summary` de `gym-sessions.json` actualizada en los dos repos (byte a byte iguales).
- Claves `board:system:<grado>[:<inclinación>]` aceptadas (`GymAscentRules`). Sin migración.
- Tests: `GetGymSummaryUseCaseTest` (desglose por rocódromo), `GymScaleAndAscentsTest` (system), `GymContractTest`. Verificado en staging: el resumen real de la cuenta de pruebas ya trae `items` y `days` por rocódromo.
