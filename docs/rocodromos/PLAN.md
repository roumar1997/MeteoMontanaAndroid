# Rocódromos: plan de implementación

> Estado: PLAN (2026-11-19). Todavía no hay código. Álvaro aprobó hacer todo el alcance de una vez.
> Maqueta aprobada: `Escritorio\rocodromos\maqueta\index.html` + `maqueta.png`.
> Datos: `Escritorio\rocodromos\rocodromos.csv` (349 filas).
> Normas: COMO_TRABAJAMOS.md y ARCHITECTURE.md. Las pantallas se hacen primero en iOS (paridad, §6),
> pero el backend y `shared` van antes que cualquier pantalla.

## 0. Resumen y alcance por entregas

| Entrega | Qué lleva |
|---|---|
| **v1 (base)** | Selector en Escuelas «Roca · Rocódromos · Muros gratis»; mapa con marcadores propios y agrupación; lista con filtros; ficha completa; «Quién va hoy» (reutiliza «Aquí»); quedadas en rocódromo; correcciones y altas revisadas por admin (solo iOS); Plan B «Rocódromos cerca»; iconos; DESIGN.md corregido. **Favoritos de rocódromos.** |
| **v1.1 (sesiones)** | Check-in «He ido hoy» (también automático al marcar «Aquí»); contador en el perfil (semana/mes/año, rocódromo más visitado, racha); «Tu año en cifras» con días en roca frente a días de rocódromo; diario de sesión opcional (grado + color, sin catálogo de bloques) que alimenta la pirámide de grados. |
| **v1.2 (afluencia)** | Afluencia orientativa («suele estar lleno a las 19 h»), calculada con check-ins anónimos agregados, solo si hay datos suficientes. |
| **Futuro (aprobado)** | Avisos: «tu rocódromo favorito ha cambiado bloques» y eventos o competiciones propuestos por usuarios y aprobados por admin. |
| **Descartado** | Apuntar tu propio bono para calcular el coste por sesión (Álvaro lo quitó el 2026-11-19). |

En la v1 ya se dejan preparadas las tablas de favoritos. Las de sesiones se crean en la v1.1, y todas con migraciones aditivas.

## 1. Modelo de datos (backend, migraciones aditivas)

La última migración de `MeteoMontanaAPI/api/src/main/resources/db/migration` es la **V102**. Las nuevas empiezan en
la **V103**. Justo antes de subir hay que comprobar que ninguna otra rama ha cogido ese número; si la ha cogido, se renumera.

### V103__climbing_gyms.sql
```sql
CREATE TABLE climbing_gym (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(160) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  chain VARCHAR(80),
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('COMMERCIAL','MUNICIPAL','FREE')),
  discipline VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' CHECK (discipline IN ('BOULDER','ROPE','BOTH','UNKNOWN')),
  region VARCHAR(80) NOT NULL, province VARCHAR(80) NOT NULL, city VARCHAR(120) NOT NULL,
  address VARCHAR(300),
  lat DOUBLE PRECISION NOT NULL, lon DOUBLE PRECISION NOT NULL,
  website VARCHAR(400), instagram VARCHAR(100),
  opening_hours JSONB,                 -- [{"days":"MON-FRI","open":"10:00","close":"23:00"}]
  price_single_eur NUMERIC(6,2), price_pass_eur NUMERIC(6,2), price_pass_label VARCHAR(60),
  price_updated_at DATE,
  has_autobelay BOOLEAN, has_gear_rental BOOLEAN, kids_friendly BOOLEAN, beginner_friendly BOOLEAN, -- NULL = no se sabe
  outdoor BOOLEAN NOT NULL DEFAULT FALSE,
  verification VARCHAR(16) NOT NULL DEFAULT 'UNCONFIRMED' CHECK (verification IN ('UNCONFIRMED','COMMUNITY','VERIFIED')),
  source VARCHAR(200), source_notes TEXT,
  hidden BOOLEAN NOT NULL DEFAULT FALSE,   -- baja lógica (la hace el admin)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_climbing_gym_geo ON climbing_gym (lat, lon) WHERE hidden = FALSE;
CREATE INDEX idx_climbing_gym_kind ON climbing_gym (kind) WHERE hidden = FALSE;
```
- Se añade un **test de guardarraíl entre los enums de Java y los CHECK de la BD** para `kind`, `discipline` y `verification`. Es la lección del bug de SEMI que está en HISTORIAL.
- Son 349 filas: la lista se descarga entera (unos 60 KB comprimidos) y se filtra en el móvil, así que no hace falta PostGIS. El endpoint `near` del Plan B usa una caja de coordenadas y Haversine en Java.

### V104__climbing_gym_seed.sql (carga inicial)
- Lo genera **una sola vez** `scripts/rocodromos/csv-to-sql.js`. Es node para no romper los acentos, y usa un parser CSV de verdad, porque hay comas dentro de comillas y espacios dobles.
- Correspondencias: `tipo` comercial→COMMERCIAL, municipal→MUNICIPAL, gratuito→FREE; las filas con tipo vacío dependen de la decisión abierta 2. `modalidad` bloque→BOULDER, cuerda→ROPE, ambos→BOTH y el resto→UNKNOWN.
- El `slug` se forma con provincia, ciudad y nombre normalizado, con un sufijo si choca. Los inserts usan `ON CONFLICT (slug) DO NOTHING`, así que la carga se puede repetir y es aditiva.
- **Todas las filas entran como `UNCONFIRMED`**, salvo las que estén en una lista explícita de verificadas. Hoy el CSV **no marca ninguna como verificada** (las notas solo dicen «sin verificar»), así que esa lista empieza vacía. Precios, horarios y booleanos quedan a NULL.
- El script saca un informe con las filas sin tipo, las que no tienen coordenadas y los posibles duplicados (a menos de 80 m y con nombre parecido). Ese informe se revisa antes de subir nada.

### V105__climbing_gym_reports.sql (correcciones y altas)
```sql
CREATE TABLE climbing_gym_report (
  id BIGSERIAL PRIMARY KEY,
  gym_id BIGINT REFERENCES climbing_gym(id),   -- NULL = alta de un muro nuevo
  author_uid VARCHAR(128) NOT NULL,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('CORRECTION','NEW_GYM')),
  issues VARCHAR(40)[] NOT NULL DEFAULT '{}',  -- IS_FREE, IS_PAID, IS_BOULDER, IS_ROPE, CLOSED, WRONG_LOCATION, OTHER
  comment VARCHAR(1000), photo_url VARCHAR(400),
  proposed JSONB,                              -- en un alta: nombre, lat, lon, tipo, modalidad, outdoor
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPLIED','REJECTED')),
  reviewed_by VARCHAR(128), reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_gym_report_pending ON climbing_gym_report (created_at) WHERE status = 'PENDING';
```
- Para frenar abusos, cada usuario puede mandar como mucho 5 reportes al día (el mismo patrón de cuota que las aportaciones).

### V106__gym_presence_meetups_favorites.sql
- `gym_presence` es una **tabla hermana** de `school_presence` (V70) con la misma forma. Así la presencia actual no corre ningún riesgo. Las dos se manejan con un puerto común parametrizado por `PlaceRef`.
- `meetup` gana una columna `gym_id` nullable. Las versiones viejas de la app no la mandan ni la leen. El DTO lleva un campo opcional nuevo, `placeName`.
- `gym_favorite (user_uid, gym_id, created_at, PK(user_uid, gym_id))`, con el mismo patrón que los favoritos de escuelas (`FavoriteRepository`).
- Lo de que «Aquí» sea obligatorio en el chat de escuela no se aplica aquí, porque no hay chat de rocódromo.

### V107__gym_sessions.sql (v1.1)
```sql
CREATE TABLE gym_session (
  id BIGSERIAL PRIMARY KEY,
  user_uid VARCHAR(128) NOT NULL,
  gym_id BIGINT NOT NULL REFERENCES climbing_gym(id),
  session_date DATE NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),       -- hora para la afluencia
  source VARCHAR(16) NOT NULL CHECK (source IN ('MANUAL','PRESENCE')),
  UNIQUE (user_uid, gym_id, session_date)              -- marcar «Aquí» y además «He ido hoy» no duplica
);
CREATE INDEX idx_gym_session_user ON gym_session (user_uid, session_date);
CREATE INDEX idx_gym_session_gym_time ON gym_session (gym_id, started_at);
CREATE TABLE gym_session_ascent (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES gym_session(id) ON DELETE CASCADE,
  discipline VARCHAR(8) NOT NULL CHECK (discipline IN ('BOULDER','ROPE')),
  grade VARCHAR(8) NOT NULL,          -- la misma escala que la pirámide actual
  hold_color VARCHAR(16),             -- paleta cerrada: RED, BLUE, GREEN, YELLOW, BLACK, WHITE, ORANGE, PURPLE, PINK, OTHER
  style VARCHAR(8) CHECK (style IN ('FLASH','REDPOINT','ATTEMPT')),
  count SMALLINT NOT NULL DEFAULT 1
);
```
- Las sesiones de rocódromo cuentan para «Tu año en cifras» como días de rocódromo, separados de los días en roca. En la pirámide de grados, los pegues de rocódromo salen **separados o con un interruptor «incluir rocódromo»**, porque un 7a de plástico no es un 7a de roca (decisión abierta 6).
- Se respeta la privacidad del perfil: si el diario es privado, las sesiones también lo son.
- Las sesiones y sus pegues entran en `AccountDataPurger` al borrar la cuenta.

### Afluencia (v1.2): sin tabla nueva
Hay una consulta agregada sobre `gym_session.started_at` por día de la semana y hora, con los **últimos 90 días**. Solo se publica si hay **20 check-ins o más** en esa franja y **8 usuarios distintos o más** (anonimato y datos suficientes). Un trabajo nocturno la precalcula en `gym_busyness (gym_id, weekday, hour, level)`, que sería la V108.

### Futuro: avisos y eventos
`gym_event` (propuesto por un usuario y aprobado por el admin: tipo `RESET` o `COMPETITION`, fecha, texto) y una notificación a quien tenga ese rocódromo en favoritos, reutilizando `NotificationRepository` y las preferencias de alertas. No se diseña más hasta que se abra esa fase.

## 2. Backend: endpoints y hexágono

```
domain/model/gym/       ClimbingGym, GymKind, GymDiscipline, GymVerification, OpeningHours, GymPrice, GymReport, GymSession, GymAscent
domain/port/            ClimbingGymRepository, GymReportRepository, GymPresenceRepository, GymFavoriteRepository,
                        GymSessionRepository (v1.1), GymBusynessRepository (v1.2); ReportPhotoStorage reutiliza el de los avisos con foto
application/gym/        ListGymsUseCase, GetGymUseCase, FindGymsNearUseCase, SubmitGymReportUseCase,
                        ListPendingGymReportsUseCase, ReviewGymReportUseCase, AdminUpdateGymUseCase, Toggle/ListGymFavoritesUseCase, DTOs y mappers
application/gym/session (v1.1)  CheckInGymUseCase, LogGymAscentsUseCase, GetGymStatsUseCase
application/presence/   Set/Get/ClearGymPresenceUseCase; al marcar «Aquí» en un rocódromo, llama a CheckInGymUseCase (v1.1)
infrastructure/persistence/gym/  entidades, JpaRepository y adaptadores
infrastructure/web/gym/ ClimbingGymController, GymReportController, GymSessionController, AdminGymController
```
La revisión de reportes tiene **puerto desde el principio**, para no repetir la deuda de `ReviewContributionUseCase`. Habrá un use case por fichero, todos de 400 líneas o menos.

| Método | Ruta | Auth | Respuesta | Entrega |
|---|---|---|---|---|
| GET | `/api/gyms?updatedSince=` | pública | `List<GymDto>` con ETag | v1 |
| GET | `/api/gyms/{id}` | pública | `GymDto` | v1 |
| GET | `/api/gyms/near?lat=&lon=&radiusKm=30&limit=5` | pública | `List<GymNearDto>` | v1 |
| POST | `/api/gyms/{id}/reports` · `/api/gyms/reports` | usuario | 201 `GymReportDto` | v1 |
| POST | `/api/gyms/reports/photo` | usuario | URL en `photos.climbingteams.com` (máx. 5 MB) | v1 |
| GET/PUT/DELETE | `/api/gyms/{id}/presence` | usuario | igual que la presencia de escuelas | v1 |
| GET · PUT/DELETE | `/api/gyms/favorites` · `/api/gyms/{id}/favorite` | usuario | ids de los favoritos | v1 |
| GET · POST apply/reject · PUT | `/api/admin/gyms/reports…` · `/api/admin/gyms/{id}` | admin | revisión y edición | v1 |
| POST | `/api/meetups` | — | acepta `gymId` opcional | v1 |
| POST/DELETE | `/api/gyms/{id}/sessions/today` | usuario | `GymSessionDto` | v1.1 |
| PUT | `/api/gyms/sessions/{id}/ascents` | usuario | lista de pegues | v1.1 |
| GET | `/api/users/{uid}/gym-stats?year=` | según la privacidad | `GymStatsDto` (semana/mes/año, el más visitado, racha, días de rocódromo frente a roca) | v1.1 |
| GET | `/api/gyms/{id}/busyness` | pública | `BusynessDto` o 204 si no hay datos suficientes | v1.2 |

En `GymDto` todo es opcional salvo la identificación, el tipo y las coordenadas. Si el cliente recibe un enum que no conoce, lo trata como `UNKNOWN` y no se cae. Las excepciones se traducen solo en `GlobalExceptionHandler`. El interruptor remoto (V99) tiene la flag `gyms_enabled`, que en producción está apagada hasta publicar, y en la v1.1 se añade `gym_sessions_enabled`.

## 3. shared (KMP)

```
domain/model/gym/     modelos + OpeningHours.isOpenAt (Europe/Madrid)
domain/usecase/gym/   GetGyms (con caché y updatedSince), GetGym, FindGymsNear, FilterGyms (GymFilters, lógica pura),
                      GymClusterer (agrupación por rejilla según el zoom, para que iOS y Android agrupen igual),
                      SubmitGymReport, SubmitNewGym, ToggleGymFavorite, Set/GetGymPresence, ShouldSuggestGymsUseCase (Plan B)
domain/usecase/gym/session (v1.1)  CheckInGym, LogAscents, GymStatsCalculator (racha, semana/mes/año), YearInNumbers con días de rocódromo
data/api/             KtorGymApi, KtorGymPresenceApi, KtorGymReportApi, KtorGymSessionApi: TODAS las suspend llevan @Throws (trampa de SKIE)
data/cache/           GymCatalogCache (puerto + implementación, como SchoolCatalogCache; funciona sin conexión)
```
- **Toda la lógica vive en shared**: filtros (modo ROCODROMOS/FREE_WALLS, gratis, bloque, cuerda, abierto ahora, cadena, distancia), orden, los textos «Abierto · hasta 23:00» y «Cerrado», el precio con su fecha, la racha y el formato de la afluencia.
- `ShouldSuggestGymsUseCase` reutiliza el criterio de roca mojada que ya existe, sin duplicar umbrales.
- Antes de subir se pasa `:shared:compileCommonMainKotlinMetadata`.

## 4. Pantallas (paridad exacta: primero iOS y después Android a partir de capturas)

Textos (los de la maqueta, en ES con su clave EN):

| Zona | Texto |
|---|---|
| selector | «Roca» · «Rocódromos» · «Muros gratis» |
| filtros | «Todos» «Gratis» «Bloque» «Cuerda» «Abierto ahora» |
| tipo | «Comercial» «Municipal» «Muro gratis» «De pago»; el marcador lleva «GRATIS» |
| modalidad | «Bloque» «Cuerda» «Bloque + cuerda» «Muro al aire libre» |
| ficha | «Horario» «Precio orientativo» «Modalidad» «Instagram» «Cómo llegar» «Ver ficha» «Quién va hoy» «Unirme» «Foto de la comunidad» |
| estado | «Confirmado por la comunidad» / «Sin confirmar» |
| corregir | «¿Algo no es así? Corregir» · «¿Qué no es correcto?» · «Es gratis» «Es de pago» «Es de bloque» «Es de cuerda» «Está cerrado» «Ubicación mal» «Otro» · «Comentario» · «Enviar» · «La revisará un admin» |
| alta | «Añadir un muro que falta» · «Nombre: Muro del parque…» |
| plan B | «Rocódromos cerca» |
| v1.1 | «He ido hoy» · «Apuntar lo que he hecho» · «Sesiones de rocódromo» · «Racha» · «Tu rocódromo» · «Días en roca» / «Días de rocódromo» (los textos finales se proponen a Álvaro) |
| v1.2 | «Suele estar lleno a las 19 h» / «Suele estar tranquilo» |

Los colores van siempre con tokens, nunca con hex sueltos:
- Comercial: `terra` y `terraFill`.
- Municipal: el azul que ya existe. Si no hay token, se crea `gymMunicipal` en `CumbreTheme.swift` y en `Color.kt`, para claro y oscuro y con un contraste de 4.5:1 o más.
- Gratis: el verde de los scores buenos.

La tarjeta de cristal usa el material del armazón del mapa (DESIGN §1.9). Los radios siguen la norma: controles con `pillRadius`, tarjetas rectas y estadísticas a 14.

**iOS (SwiftUI)**: `SchoolsSegmentPicker`, `GymsMapView`, `GymsListView` + `GymFilterBar` y `GymDetailView`, partida en secciones para no pasar de 600 líneas: Info, Precio, Servicios, Presencia, Quedadas y Favorito. También `GymReportSheet`, `NewGymSheet` y `GymsNearbyBanner`. En la v1.1 se añaden `GymCheckInButton`, `GymSessionLogSheet` (grado + rueda de color) y `GymStatsCard` en el perfil y en «Tu año en cifras». Los ViewModels reciben `init(container:)` con valor por defecto.

**Admin (solo iOS)**: pestaña «Rocódromos» con la cola de reportes (foto, chips, comentario y cambio propuesto, con Aplicar y Rechazar) y un editor de la ficha (precio y su fecha, horario, servicios, verificación, ocultar). En la fase futura se le añade la aprobación de eventos.

**Android (Compose)**: espejo pantalla por pantalla. Los clusters salen del `GymClusterer` de shared. Los ViewModels solo llaman a use cases (lo vigila `ArchitectureRulesTest`). La foto se elige con el selector de documentos del sistema (nada de `READ_MEDIA_IMAGES`). No hay medidas fijas.

**Iconos SVG** con el mismo trazo, extremos redondeados y caja de 24 que los actuales: `ic_gym`, `ic_free_wall`, `ic_autobelay`, `ic_gear_rental`, `ic_kids`, `ic_beginner`, `ic_clock`, `ic_price`, `ic_directions`, `ic_instagram` (genérico) y, en la v1.1, `ic_checkin` e `ic_hold_color`. El original de cada uno se guarda en `docs/rocodromos/icons/`; en Android se pasa a VectorDrawable y en iOS va en Assets con «Preserve vector data». Ningún emoji.

**DESIGN.md**: hay que reescribir el §1.6. El contenido usa 0, 2 y 4 (Shape.kt); los controles usan `pillRadius = 999` (`CumbrePillShape`) y las tarjetas de estadística `statCardRadius = 14` (`CumbreStatCardShape`). Se quitan el «nunca subir de 4dp» del §1.6 y la mención de «radios de 0 a 4dp» del §1.9, y se cita `CumbreTheme.swift` como fuente.

## 5. Tests

- **Backend**: un test por use case con los puertos simulados; migraciones Flyway con Testcontainers en CI; guardarraíl entre enums y CHECK; test del seed (349 filas, ningún slug repetido, todas UNCONFIRMED salvo la lista); cuota de reportes; 403 para quien no es admin; unicidad del check-in diario; umbral de anonimato de la afluencia; purga de las sesiones al borrar la cuenta.
- **Contrato servidor↔apps**: `GymDto`, `GymNearDto`, `GymReportDto`, `GymSessionDto`, `GymStatsDto` y `BusynessDto` en los tests de contrato que ya existen, comprobando que un campo o un enum desconocido no rompen nada.
- **shared (commonTest)**: GymFilters, `isOpenAt` (medianoche, domingo, horario partido, NULL), GymClusterer, ShouldSuggestGyms, la racha (cambio de semana y de año, zona horaria) y los días de roca frente a los de rocódromo.
- **Android**: tests de los ViewModels y capturas de la lista, la ficha completa, la ficha con todo a NULL, la hoja de corrección y la tarjeta de estadísticas, en claro, en oscuro y en la pantalla estrecha.
- **Maestro**: un recorrido nuevo, `.maestro/10_rocodromos.yaml` (selector → filtro Gratis → ficha → Favorito → Corregir → Enviar), y en la v1.1 el `11_sesion_rocodromo.yaml`. Además se amplía `02_escuelas.yaml` para comprobar que «Roca» sigue igual.
- **iOS**: `SwiftBoundaryThrowsTest` tiene que cubrir las nuevas `Ktor*Api`.

## 6. Fases y cuándo están hechas

| # | Fase | Hecha cuando… | Agente / modelo |
|---|---|---|---|
| 1 | Script CSV→SQL + informe | 349 filas leídas e informe revisado por Álvaro | Haiku |
| 2 | Backend v1 (V103–V106) | `./mvnw test` en verde (salvo contextLoads) y `check-file-sizes` OK | Franky (Sonnet 5.5); una revisión de los puertos con Sanji (Opus 5.5 en alto) |
| 3 | Backend a `develop`/staging | Railway ha desplegado **ese commit**; `GET /api/gyms` devuelve 349 y `/near` devuelve distancias | Haiku |
| 4 | shared v1 + commonTest + contrato | tests en verde y `compileCommonMainKotlinMetadata` OK | Sonnet 5.5 |
| 5 | Iconos, tokens y DESIGN.md | SVGs guardados y contraste comprobado | Sonnet 5.5 |
| 6 | iOS v1 + admin | build de CI en verde | Brook (Sonnet 5.5) |
| 7 | Android v1 | `:app:testDebugUnitTest :app:assembleDebug`, capturas y Maestro 10 en verde, y probado en el Redmi | Robin (Sonnet 5.5) |
| 8 | Ronda de pruebas v1 y TestFlight | todos los puntos 1–18 de la lista OK | Usopp (Sonnet 5.5) |
| 9 | Publicar v1 | backend en `main` y apps en las tiendas, con «sí, fusiona»; `gyms_enabled` se enciende cuando las dos tiendas ya tienen la versión | Haiku + coordinación de Zoro |
| 10 | v1.1 sesiones (V107 + shared + apps) | tests, Maestro 11 y puntos 19–23 OK; staging antes que prod | Sonnet 5.5; Opus 5.5 en alto para revisar la privacidad y la pirámide |
| 11 | v1.2 afluencia (V108 + trabajo nocturno) | `/busyness` devuelve 204 por debajo del umbral y el nivel por encima (con datos de staging); punto 24 OK | Sonnet 5.5 |
| 12 | Futuro: avisos y eventos | se diseña cuando toque | — |

Antes de cada push, el agente `reviewer` revisa el diff. Al terminar cada fase se apunta en HISTORIAL.md y se ponen al día estado-cumbre.json y CLAUDE.md.

## 7. Riesgos

- **Datos sin verificar**: hay unas 117 filas sin tipo y unas 159 con la modalidad «desconocido». La etiqueta «Sin confirmar» tiene que verse bien, «Corregir» queda a un toque y el informe se revisa antes de cargar.
- **Precios caducados**: siempre «orientativo» y con su fecha; si no hay fecha, no se enseña el precio.
- **Abuso de reportes y fotos**: cuota diaria, moderación del admin y el mismo flujo de fotos que ya usan los avisos.
- **Mapa lento**: las capas no se pintan juntas (el selector las separa) y los marcadores se agrupan.
- **Tablas vivas** (presencia, quedadas): por eso hay una tabla hermana y columnas nullable.
- **Sesiones**: datos personales con hora. Se respeta la privacidad del perfil, la afluencia solo muestra agregados por encima del umbral y las sesiones se purgan al borrar la cuenta. Los grados de rocódromo podrían falsear la pirámide de roca (decisión abierta 6).
- **Paridad con dos mapas**: por eso la agrupación y los textos salen de shared.
- **Trampas conocidas**: @Throws en las suspend, `optString` "null", acentos con PowerShell, purgar Cloudflare si se cambia una foto y el número de migración ocupado por otra rama.
- **Licencia**: los datos de OpenStreetMap son ODbL, así que la ficha muestra «© OpenStreetMap» cuando la fuente es OSM.

## 8. Lo que tiene que probar Álvaro

**v1**
1. En Escuelas, el selector «Roca · Rocódromos · Muros gratis»: con Roca todo sigue igual que hoy.
2. Con Rocódromos desaparecen las escuelas y la nota del tiempo, y salen marcadores terracota (comerciales) y azules (municipales).
3. Con Muros gratis solo salen marcadores verdes con «GRATIS».
4. Al alejar el mapa, los marcadores se agrupan con un número; al acercar, se separan.
5. Al tocar un marcador sale la tarjeta de cristal con «Abierto · hasta …» y «Ver ficha».
6. En la lista, los filtros Gratis, Bloque, Cuerda y Abierto ahora, el chip de cadena y el orden por distancia funcionan.
7. En la ficha, «Cómo llegar» abre el navegador de mapas, y la web e Instagram se abren.
8. En la ficha salen horario, precio con su fecha, modalidad, autobelay, alquiler y niños/principiantes (o «sin confirmar» cuando no se sabe).
9. Se ve el estado «Confirmado por la comunidad» o «Sin confirmar».
10. En «Quién va hoy», al pulsar «Unirme» apareces tú, y otro usuario te ve.
11. Una quedada creada en un rocódromo sale en Quedadas con el nombre del rocódromo.
12. «¿Algo no es así? Corregir» con chips, comentario y foto termina en «La revisará un admin».
13. «Añadir un muro que falta» funciona con un nombre y un punto en el mapa.
14. En el iPhone, como admin, salen los reportes de 12 y 13: Aplicar cambia la ficha y Rechazar la quita.
15. En una escuela mojada aparece «Rocódromos cerca» con los más próximos.
16. Un rocódromo marcado como favorito sale en tus favoritos y sigue ahí al reiniciar la app.
17. En modo oscuro y en la pantalla estrecha no se corta nada y los colores se leen bien.
18. Sin conexión, la lista que ya estaba cargada sigue saliendo.

**v1.1**
19. «He ido hoy» suma una sesión, y pulsarlo dos veces no la duplica.
20. Marcar «Aquí» en un rocódromo cuenta como sesión sin hacer nada más.
21. En el perfil salen las sesiones de la semana, el mes y el año, el rocódromo más visitado y la racha.
22. «Tu año en cifras» separa los días en roca de los días de rocódromo.
23. Lo que apuntas por grado y color sale en la pirámide de grados según la decisión 6.

**v1.2**
24. En un rocódromo con mucho uso sale «Suele estar lleno a las …»; en uno con pocos datos no sale nada.

## 9. Decisiones abiertas (con mi recomendación)

1. **Qué filas se cargan como verificadas**: el CSV no marca ninguna. Recomiendo cargarlas todas como «Sin confirmar» y que el admin confirme desde el panel las cadenas grandes.
2. **Las 117 filas sin tipo**: recomiendo cargarlas como «Sin confirmar» con el tipo que diga el informe (municipal si es una instalación pública de OSM; si no, comercial). La alternativa es no cargarlas hasta revisarlas.
3. **«Confirmado por la comunidad»**: recomiendo que en la v1 solo lo ponga el admin y que la confirmación automática con 3 usuarios quede para más adelante.
4. **Plan B**: reutilizar el criterio de roca mojada que ya existe y un radio de 30 km.
5. **Publicación**: detrás de `gyms_enabled`, que se enciende cuando las dos tiendas tienen la versión.
6. **La pirámide con grados de rocódromo**: recomiendo una pirámide aparte (o un interruptor «Incluir rocódromo», apagado por defecto) para no mezclar plástico y roca.
7. **Umbral de la afluencia**: recomiendo 20 check-ins y 8 usuarios distintos por franja en 90 días.
