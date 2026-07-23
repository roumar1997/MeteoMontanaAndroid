# MejorasFuturas.md — Plan completo para dejar Cumbre "perfecta"

> Generado a partir de la revisión de arquitectura completa del 2026-07-09
> (4 análisis en paralelo: UI Android, capa compartida KMP, backend Spring,
> seguridad/transversales). Cubre **los dos repos**: `MeteoMontanaAndroid`
> (app Android + iOS + shared KMP) y `MeteoMontanaAPI` (backend).
>
> **Cómo usar este documento**: cada ítem tiene QUÉ (el problema), POR QUÉ
> (qué duele si no se arregla), y CÓMO (los pasos concretos, en orden, de la
> forma más correcta posible). Los bloques están ordenados por retorno:
> el Bloque 1 es barato y de alto impacto; el Bloque 6 es pulido.
> Ninguno bloquea el lanzamiento — la app es publicable tal cual está.
>
> Notas de la revisión: UI 6.5 · Shared KMP 6.5 · Backend 6 · Seguridad 7 ·
> Ingeniería 6.5. Objetivo de este plan: todo a 8.5-9.

---

## ✅ ESTADO 2026-07-20 (fase "dejar perfecta" ejecutada — leer antes que el resto)

Gran parte de este plan YA ESTÁ HECHO (todo en la rama develop, CI verde,
re-auditoría: UI Android 7.5 · iOS 7.5 · shared 7 · backend 7 · ingeniería 8):

- **Bloque 1**: 1.1 ✅ (GlobalExceptionHandler) · 1.2 ✅ (@Valid con tests) ·
  1.3 ✅ (dominio sin Spring) · 1.4 ✅ (caps gearJson/bloquesJson/coords) ·
  1.6 ✅ EL GORDO (N+1 de ratings: 2 queries/vía → 2/escuela). 1.5 pendiente.
- **Bloque 2**: 2.1 ✅ (shared/src/commonTest existe y el CI lo corre) ·
  2.3 ✅ ampliado (contratos del feed + autorización: admin/baneo/foto ajena) ·
  2.4 ✅ parcial (CI vigila develop, Dependabot en ambos repos; análisis
  estático/ArchUnit pendiente). 2.2 pendiente.
- **Bloque 3**: 3.3 ✅ (ReviewContributionUseCase→orquestador + 4 piezas, con
  fix real de @Transactional). 3.1/3.2/3.4/3.5/3.6 = BOY-SCOUT (al tocar cada
  clase; decisión de auditoría: en big-bang mete más riesgo del que quita).
- **Bloque 4 y god-files**: TODOS los ficheros >900 líneas de ambas apps
  repartidos (14 en total: SchoolMap, SchoolDetailView/VM, FeedService,
  Propose*, FeedScreen/View, AdminView, ContributionCard, MainScreen,
  SchoolListView, MeetupsView, AddLinesFlow, BlockDetailDialog) +
  SchoolMapViewModel extraído en iOS. Reglas en ARCHITECTURE.md (normativo).
- **Extra no previsto**: caché de disco del FEED y de BLOQUES en la capa
  compartida (offline en ambas apps) + detalle de escuela INSTANTÁNEO en
  Android (preview stale-while-revalidate) + workflow de release AAB
  (faltan los secrets de firma, los crea Rodrigo).

Lo que QUEDA de este plan: 1.5 (EAGER doble en MeetupJpaEntity), 2.2
(dispatchers inyectados), el boy-scout del Bloque 3, ArchUnit/detekt (2.4),
y los bloques 5-6 (producto/monetización). Nada de ello es urgente.

---

## BLOQUE 1 — Backend: lo barato que arregla mucho (1-2 sesiones)

### 1.1 Manejador global de errores (`@RestControllerAdvice`)

- **QUÉ**: hoy no existe ningún `@ControllerAdvice` en el proyecto. El manejo
  de errores está disperso: `ResponseStatusException` lanzada en 11 sitios
  distintos (a veces desde controllers, a veces desde use cases) y
  `@ResponseStatus` pegado sobre las excepciones de dominio.
- **POR QUÉ**: sin un punto único, cada endpoint devuelve errores con formato
  distinto, es imposible loguearlos de forma uniforme, y cada endpoint nuevo
  reinventa el manejo. Además `server.error.include-message: always` expone
  mensajes internos de cualquier excepción no controlada.
- **CÓMO** (la manera correcta):
  1. Crear `infrastructure/web/GlobalExceptionHandler.java` anotada con
     `@RestControllerAdvice`.
  2. Definir un DTO de error único: `record ApiError(String code, String
     message, Instant timestamp)`. Siempre el mismo shape para las apps.
  3. Un `@ExceptionHandler` por familia:
     - Excepciones de dominio (`SchoolNotFoundException`, etc.) → 404/403/409
       según corresponda.
     - `MethodArgumentNotValidException` (la que lanza `@Valid`, ver 1.2) →
       400 con la lista de campos inválidos.
     - `Exception` genérica → 500 con mensaje neutro ("Error interno") y
       **log completo con stacktrace en servidor**. Nunca filtrar el mensaje
       interno al cliente en el caso genérico.
  4. Quitar los `@ResponseStatus` de las excepciones de dominio (ver 1.3) y
     los `ResponseStatusException` de los use cases: los use cases lanzan
     excepciones de dominio, el advice las traduce a HTTP. Esa es la
     separación correcta: **el dominio no sabe de HTTP**.
  5. Verificar que las apps (Android/iOS) toleran el nuevo shape de error
     antes de mergear a `main` (retrocompatibilidad: mantener el campo
     `message` que ya consumen).

### 1.2 Validación declarativa de entrada (`@Valid` + Bean Validation)

- **QUÉ**: 0 usos de `@Valid` en 26 controllers; 0 anotaciones
  `@NotBlank/@Size/@Pattern`. Toda la validación es manual e inconsistente,
  y por eso hay campos que se escapan sin límite (ver 1.4).
- **POR QUÉ**: la validación manual se olvida en los endpoints nuevos (ya ha
  pasado). La declarativa es imposible de olvidar: está pegada al DTO y
  falla automáticamente con 400 antes de entrar al use case.
- **CÓMO**:
  1. Añadir `spring-boot-starter-validation` al `pom.xml` (si no está ya
     transitivamente).
  2. En cada `*Request` DTO, anotar los campos:
     `@NotBlank @Size(max = 80) String name;`,
     `@Size(max = 500) String notes;`,
     `@DecimalMin("-90") @DecimalMax("90") double lat;` etc.
     Regla: **todo String que acabe en BD lleva `@Size(max=...)`** acorde a
     la columna, y todo campo obligatorio lleva `@NotNull/@NotBlank`.
  3. En los controllers: `public ResponseEntity<?> create(@Valid @RequestBody
     CreateMeetupRequest req, ...)`. El `@Valid` es lo que activa la
     validación.
  4. El `GlobalExceptionHandler` de 1.1 ya convierte los fallos en 400 con
     formato uniforme.
  5. Los límites que hoy están hechos a mano (username regex, truncados a
     500) se migran a anotaciones y se borra el código manual duplicado.

### 1.3 Desacoplar el dominio de Spring Web

- **QUÉ**: las 5 excepciones de `domain/exception/` importan
  `org.springframework.http.HttpStatus` y llevan `@ResponseStatus`.
- **POR QUÉ**: el dominio debe ser puro (ya lo es en los modelos — esto es la
  única fuga). Si el dominio conoce HTTP, no puedes reutilizarlo desde un
  scheduler o un worker sin arrastrar Spring Web, y viola la regla de
  dependencia de la arquitectura hexagonal que el proyecto declara seguir.
- **CÓMO**: quitar `@ResponseStatus` e imports de Spring de esas 5 clases;
  el mapeo excepción→status pasa a vivir SOLO en el `GlobalExceptionHandler`
  (1.1). Es un cambio mecánico de 15 minutos una vez existe el advice.

### 1.4 Límites de tamaño en escritura (seguridad M1)

- **QUÉ**: `MeetupController.updateMemberGear` persiste `gearJson` sin cap;
  `SubmitContributionUseCase` persiste `bloquesJson`, `topoLinesJson`,
  `photoUrl`, `name`, `notes`, `description` sin validar longitud (los
  `substring(500)` solo se aplican al APROBAR, cuando la fila enorme ya
  está en BD). No hay límite global de body JSON.
- **POR QUÉ**: un usuario autenticado puede escribir megabytes en columnas
  TEXT → DoS de almacenamiento y de memoria al listar. Ya estaba apuntado
  como pendiente en CLAUDE.md.
- **CÓMO**:
  1. Con 1.2 hecho: `@Size(max = 4096)` en `gearJson` de quedadas (512 ya es
     el límite del perfil — usar el mismo criterio), `@Size(max = 100_000)`
     en `bloquesJson` (un topo con 30 vías cabe de sobra), `@Size(max=1000)`
     en `photoUrl`.
  2. Límite global de body: en `application.yaml`,
     `server.max-http-request-header-size` ya tiene default sano; para el
     body JSON añadir `spring.servlet.multipart` ya está (5MB) y para JSON
     configurar `server.tomcat.max-swallow-size` y validar por `@Size` (el
     límite fino correcto es por campo, no global).
  3. Verificar en staging con un `curl` de payload gigante que devuelve 400.

### 1.5 Bug latente JPA: `MeetupJpaEntity` con dos `@OneToMany` EAGER

- **QUÉ**: `days` y `members` son dos `List` con `fetch = EAGER` en la misma
  entidad (`MeetupJpaEntity.java:54,57`).
- **POR QUÉ**: dos *bags* EAGER en la misma entidad es un problema clásico de
  Hibernate: o lanza `MultipleBagFetchException` si algún día se hace
  join-fetch, o genera producto cartesiano (filas duplicadas) y N+1
  garantizado al listar quedadas. Es un bug latente que explotará al crecer
  los datos.
- **CÓMO** (la manera correcta):
  1. Cambiar ambos a `fetch = FetchType.LAZY` (el default de `@OneToMany` —
     es EAGER porque alguien lo forzó).
  2. En los use cases que SÍ necesitan las colecciones (detalle de quedada),
     cargar explícitamente con una query `@Query("select m from
     MeetupJpaEntity m left join fetch m.days where m.id = :id")` y otra
     para members (dos queries separadas — NUNCA dos join fetch de bags en
     la misma query, por lo de arriba). Alternativa igual de válida:
     `@EntityGraph`.
  3. Para el listado, si solo hace falta el count de members, usar una query
     de proyección con `count()` en vez de cargar la colección.
  4. Test de repositorio (`@DataJpaTest`) que liste quedadas y cuente las
     queries (con `hibernate.generate_statistics` o datasource-proxy) para
     demostrar que no hay N+1. Ya existe un precedente en el repo:
     `SchoolBlockRepositoryFetchTest`.

### 1.6 Arreglar los N+1 reales

- **QUÉ**:
  - `GetTodayScoresUseCase.java:53,61` — `schoolRepository.findById(id)` en
    un stream Y OTRA VEZ en el bucle: hasta 100 SELECTs en una request de
    scores de 50 escuelas.
  - `FavoriteUseCase.java:48,66` — `findById` dentro de bucle.
- **POR QUÉ**: latencia y presión de pool de conexiones que crece linealmente
  con los favoritos del usuario. Con testers reales, es la diferencia entre
  50ms y 800ms en el widget.
- **CÓMO**: añadir `findAllByIds(Collection<String> ids)` al puerto
  `SchoolRepository` (y su adaptador con `repository.findAllById(ids)` de
  SpringData), cargar todas de una vez a un `Map<String, School>` y resolver
  del mapa dentro del bucle. Una query en vez de N.

---

## BLOQUE 2 — Red de seguridad: tests (el mayor déficit del proyecto)

> **Estado actual**: 0 tests en `shared/` (el código que corre en DOS
> plataformas), 561 líneas de test para 16.500 de backend, cero cobertura de
> la capa web, de autorización y de `ReviewContributionUseCase` (628 líneas
> de lógica crítica). El historial documenta bugs que un test habría cazado
> (`matchesDays` comparando día-de-semana contra fechas ISO; el SMALLINT que
> tumbó producción 10 minutos).

### 2.1 Habilitar tests en la capa compartida (`commonTest`)

- **QUÉ**: crear `shared/src/commonTest/kotlin/` — hoy no existe.
- **POR QUÉ**: un bug en `shared/` se envía a las dos tiendas a la vez. Es
  la capa con mejor relación coste/beneficio de test: lógica pura, sin
  Android ni red.
- **CÓMO**:
  1. En `shared/build.gradle.kts`, añadir al source set `commonTest`:
     `kotlin("test")` y `org.jetbrains.kotlinx:kotlinx-coroutines-test`.
  2. **Prerequisito — inyectar dispatchers** (ver 2.2): sin esto los tests de
     repos de caché no pueden controlar las corrutinas.
  3. Prioridad de cobertura, en orden:
     - **Mappers DTO→dominio** (`Mappings.kt`, `MeetupMappings.kt`): un test
       por mapper con un DTO completo y otro con todos los nullables a null.
       Son los tests más baratos y cazan el error más común (añadir campo al
       backend y olvidar mapearlo).
     - **`OutboxRepository`**: la lógica de cancelación de opuestos e
       idempotencia (marcar favorito offline + desmarcar = cola vacía). Usar
       el driver in-memory de SQLDelight
       (`app.cash.sqldelight:sqlite-driver` con JdbcSqliteDriver en tests).
     - **Use cases con lógica real**: `GetFavoritesWidgetDataUseCase`
       (orquestación + degradación), lógica de walls/`WallDiff`, filtros de
       meetups.
  4. Ejecutarlos en CI: añadir `:shared:testDebugUnitTest` (o
     `:shared:allTests`) al workflow `android-ci.yml` para que corran en
     cada push. Un test que no corre en CI no existe.

### 2.2 Inyectar `CoroutineDispatcher` en la capa de datos

- **QUÉ**: `Dispatchers.Default`/`.IO` están hardcodeados en
  `OutboxRepository`, `MeetupCacheRepository`, `CachedSchoolsRepository`,
  `ProfileCacheRepository`, `SavedSchoolRepository`, `OutboxFlusher`…
- **POR QUÉ**: es LA razón técnica por la que la capa compartida no se puede
  testear bien — en un test necesitas un `StandardTestDispatcher` para
  controlar el tiempo virtual. Además `Dispatchers.IO` no existe en iOS
  nativo (en KMP se usa `Dispatchers.Default` o el de la plataforma), así
  que centralizarlo también limpia la portabilidad.
- **CÓMO** (patrón estándar):
  1. Cada clase recibe el dispatcher por constructor con default:
     `class OutboxRepository(..., private val dispatcher: CoroutineDispatcher
     = Dispatchers.Default)`.
  2. Los `withContext(Dispatchers.Default)` internos pasan a
     `withContext(dispatcher)`.
  3. Producción no cambia nada (el default aplica); los tests pasan
     `StandardTestDispatcher(testScheduler)`.

### 2.3 Tests del backend donde duele

- **QUÉ/POR QUÉ/CÓMO**, en orden de prioridad:
  1. **`ReviewContributionUseCase`** (después o antes del troceo de 3.3 —
     idealmente ANTES, como red para el refactor): un test por tipo de
     contribución (PARKING crea block tipo PARKING, BOULDER crea block +
     parsea `bloquesJson` y adjunta líneas, POSITION_CORRECTION con y sin
     `targetBlockId`, SECTOR crea ZONE). Mockear repos con Mockito (patrón
     que ya usan `CreateMeetupUseCaseTest`).
  2. **Autorización**: tests `@WebMvcTest` (o `@SpringBootTest` + MockMvc con
     el filtro real) que verifiquen: endpoint admin sin token → 401, con
     token no-admin → 403, borrar foto de otro usuario → 403, usuario
     baneado → rechazado. **Es la lógica más sensible del sistema y hoy
     tiene cero tests.**
  3. **Un test de contrato por controller nuevo** a partir de ahora (regla
     de disciplina, no de refactor): request válida → 200 + shape esperado,
     request inválida → 400.
  4. Los tests de N+1 de 1.5/1.6 (`@DataJpaTest` contando queries).

### 2.4 CI que cubra `develop` + análisis estático

- **QUÉ**: `ci.yml` del backend solo dispara en `main` y `claude/**` — un
  push a `develop` (¡la rama de staging donde pruebas!) no ejecuta tests.
  No hay análisis estático ni escaneo de dependencias en ningún repo.
- **POR QUÉ**: staging es donde detectas los problemas antes de prod; si CI
  no corre ahí, el primer aviso de un test roto llega al mergear a main.
  El escaneo de dependencias (CVEs) es estándar 2026 y gratis.
- **CÓMO**:
  1. En `ci.yml`: `on.push.branches: [main, develop, "claude/**"]`.
  2. Activar **Dependabot** en ambos repos: fichero
     `.github/dependabot.yml` con ecosistemas `maven` (backend), `gradle`
     (app) y `github-actions` (ambos), intervalo `weekly`. Gratis, sin
     mantenimiento.
  3. Análisis estático backend: añadir el plugin **Error Prone** o
     **SpotBugs** a Maven, gate en CI (empieza en modo informe, pasa a modo
     fallo cuando esté limpio).
  4. Android: activar `lintDebug` de AGP en el CI (`./gradlew :app:lintDebug`)
     con `lint { warningsAsErrors = false; abortOnError = true }` en
     `build.gradle.kts` — caza recursos rotos, APIs deprecadas y problemas
     de i18n automáticamente.
  5. **Branch protection en GitHub** (Settings → Branches → `main`, ambos
     repos): requerir status checks verdes antes de merge. Hoy la protección
     de prod es social ("pedir OK a Rodrigo"); esto la hace técnica. Es un
     click, no código.

---

## BLOQUE 3 — Restaurar tu propia arquitectura (las features nuevas rompieron las reglas)

> Patrón detectado: el núcleo (escuelas, forecast, notas) cumple la
> arquitectura declarada; todo lo añadido después (meetups, moderación,
> social, radar, boletín, comentarios) se la salta. No es un problema de
> diseño — es que al crecer rápido se dejó de pagar el peaje de la capa. El
> arreglo es mecánico.

### 3.1 Backend: puertos para meetups / moderación / social (~14 use cases)

- **QUÉ**: `ReviewContributionUseCase`, `DeleteMyAccountUseCase` (¡8 repos
  SpringData!), `AdminStatsUseCase`, `SendAdminPushUseCase`,
  `SearchUsersUseCase`, `UpdateFcmTokenUseCase`, `UserModerationService`,
  `ContentModerationService`, `NoteVotesService`, `ResolveReportUseCase`,
  `RateLineUseCase`, `LineCommentService`, `WeekendAlertUseCase`,
  `OptimalWindowAlertUseCase` importan `SpringData*Repository` y
  `*JpaEntity` directamente en vez de puertos de `domain/port/`.
- **POR QUÉ**: la capa de negocio queda soldada al detalle de persistencia:
  no puedes testearla sin JPA, no puedes cambiar la persistencia sin tocar
  negocio, y la palabra "hexagonal" del CLAUDE.md deja de ser verdad.
- **CÓMO** (mismo patrón que ya usas en School/Note — copiar tu propio
  ejemplo bueno):
  1. Por cada agregado sin puerto (Meetup, Report, ModerationAction,
     LineComment, NoteVote, WeekendAlertPrefs): interfaz en `domain/port/`
     con SOLO los métodos que el negocio usa (interface segregation: no
     copies los 30 métodos de SpringData, expón los 5 que se usan).
  2. Adaptador en `infrastructure/persistence/jpa/`:
     `JpaMeetupRepositoryAdapter implements MeetupRepository`, que mapea
     JpaEntity ↔ modelo de dominio.
  3. El use case pasa a recibir el puerto por constructor. El cuerpo casi no
     cambia; cambian los tipos.
  4. Hazlo **incremental, un agregado por sesión**, empezando por el que
     vayas a tocar de todos modos (regla boy-scout). NO big-bang.

### 3.2 Backend: controllers que acceden a JPA directamente

- **QUÉ**: `ShareController` (inyecta `SpringDataSchoolBlockRepository`,
  maneja JpaEntities y CONSTRUYE HTML inline — 3 responsabilidades),
  `LineSearchController`, `AdminBrowseController`, `WeekendAlertController`.
- **POR QUÉ**: la capa web conociendo persistencia es acoplamiento en la
  dirección prohibida; y el HTML inline en el controller es lógica de
  presentación sin test posible.
- **CÓMO**:
  1. Por cada uno: crear el use case correspondiente en `application/`
     (p. ej. `GetShareLandingUseCase`) que devuelva un modelo de dominio.
  2. Para `ShareController` además: extraer la construcción de HTML a una
     clase `ShareHtmlRenderer` (plantilla con text blocks de Java 21 o
     Thymeleaf si crece) — testeable con un unit test de snapshot.
  3. El controller queda en: parsear params → llamar use case → render.

### 3.3 Backend: trocear `ReviewContributionUseCase` (628 líneas)

- **QUÉ**: la clase más grande del backend. Mezcla: materialización de 5
  tipos de contribución, parseo JSON con Jackson, envío de emails HTML con
  plantillas inline, propagación de grado al diario, 4 repos + email + user.
- **POR QUÉ**: viola SRP de manual; es la lógica más crítica del producto
  (lo que convierte propuestas en datos reales del mapa) y la más difícil de
  testear tal cual.
- **CÓMO** (patrón strategy, la solución canónica para "switch por tipo"):
  1. **Primero los tests de caracterización** (2.3.1) — nunca refactorizar
     628 líneas sin red.
  2. Interfaz `ContributionMaterializer { boolean supports(ContributionType);
     void materialize(PendingContribution, String adminUid); }`.
  3. Una clase por tipo: `ParkingMaterializer`, `BoulderMaterializer` (aquí
     vive `parseAndAttachLines`), `SectorMaterializer`,
     `PositionCorrectionMaterializer`. Spring las inyecta todas como
     `List<ContributionMaterializer>` y el use case elige con
     `supports()` — añadir un tipo nuevo = añadir una clase, sin tocar las
     existentes (Open/Closed).
  4. Extraer `ReviewNotificationService` (email + push al autor): el email
     HTML inline pasa a plantilla. Y sacarlo **fuera de la transacción**
     (publicar un `ApplicationEvent` y mandarlo en un listener `@Async
     @TransactionalEventListener(phase = AFTER_COMMIT)` — así el email nunca
     retiene la conexión del pool ni se manda si la transacción hace
     rollback; es el patrón correcto que ya usas en
     `SubmissionReviewedEvent`).
  5. El use case queda en ~80 líneas: validar → elegir materializer →
     ejecutar → registrar log → publicar evento.

### 3.4 App: sacar las llamadas `Ktor*Api` de los ViewModels

- **QUÉ**: `SchoolDetailViewModel` inyecta `KtorAdminApi`, `KtorMountainApi`,
  `KtorNoteApi` y los llama directo; `SchoolListViewModel` inyecta
  `KtorSchoolApi` (searchLines); `AdminViewModel` y `ChatViewModel` igual.
  Rompe la regla "VMs dependen solo de use cases" del propio CLAUDE.md.
- **POR QUÉ**: esas rutas no tienen abstracción: no se pueden mockear en
  tests de VM, no existen para iOS si algún día un VM compartido las
  necesita, y son inconsistentes con el 90% restante del código.
- **CÓMO**: mismo peaje de siempre — por cada llamada directa: método en el
  repositorio correspondiente (o repo nuevo `MountainRepository`,
  `ModerationRepository` en `domain/repository/` + impl Ktor) + use case si
  hay orquestación + el VM pasa a inyectar el use case. Los métodos ya
  existen en las Api; es mover la llamada una capa hacia dentro. Hacerlo
  cuando toques cada pantalla (boy-scout), no en big-bang.

### 3.5 App: modelos de dominio para radar / boletín / comentarios / moderación

- **QUÉ**: `RadarDto`, `MountainBulletinDto`, `LineCommentDto`,
  `ContentReportDto`/`UserModerationDto` llegan crudos hasta Compose
  (`MountainBulletinSection`, `LineCommentsSection`) y hasta SwiftUI
  (`MountainBulletinView.swift`, `LineCommentsView.swift`).
- **POR QUÉ**: los DTOs son el contrato con el backend; si el backend renombra
  un campo, hoy se rompe la UI de dos plataformas directamente. El modelo de
  dominio es el amortiguador. Además es inconsistente: escuelas/forecast SÍ
  lo hacen bien.
- **CÓMO**: por cada DTO filtrado: data class de dominio en
  `shared/.../domain/model/`, extensión `toDomain()` en `Mappings.kt` (patrón
  existente), el repo mapea en el borde, y UI/VMs pasan a consumir el modelo.
  Test del mapper en `commonTest` (2.1). Con calma, uno por sesión.

### 3.6 Shared: arreglar la violación dominio→data y dar puerto a las cachés

- **QUÉ**: `domain/usecase/widget/GetFavoritesWidgetDataUseCase.kt:3` importa
  `com.meteomontana.android.data.saved.CachedSchoolsRepository` (clase
  concreta de data). Los 5 repos de `data/saved/` + `OutboxRepository` no
  tienen interfaz en dominio.
- **POR QUÉ**: es LA violación de la regla de dependencia en la capa
  compartida: el dominio debe definir interfaces y data implementarlas,
  nunca al revés.
- **CÓMO**: interfaz `CachedSchoolsPort` (o `SchoolCacheRepository`) en
  `domain/repository/` con los métodos que usa el use case;
  `CachedSchoolsRepository` la implementa; el use case recibe la interfaz.
  Repetir para las otras cachés cuando se toquen. El DI (Hilt en Android,
  `IosDependencyContainer` en iOS) cablea la impl.

---

## BLOQUE 4 — App Android: mantenibilidad de la UI

### 4.1 Trocear los composables monstruo

- **QUÉ**: `SchoolMap.kt` (1.822 líneas, 13 composables), 
  `ProposeContributionFlow.kt` (1.577, 17 composables), `MainScreen.kt` (669
  con NavHost + tabs keep-alive + sheet con NavHost interno),
  `SchoolDetailScreen` y compañía.
- **POR QUÉ**: inmantenibles, imposibles de testear/previsualizar en
  aislamiento, y cada bug de gestos/estado obliga a razonar sobre 1.800
  líneas.
- **CÓMO** (por responsabilidad, no por tamaño arbitrario):
  - `SchoolMap.kt` → `SchoolMapView.kt` (el AndroidView de MapLibre + cámara),
    `SchoolMapMarkers.kt` (construcción/estilo de markers por tipo),
    `SchoolMapGestures.kt` (el touch-interceptor documentado),
    `SchoolMapPopups.kt` (mini-fichas), `SchoolMapButtons.kt` (botonera).
  - `ProposeContributionFlow.kt` → un fichero por paso del flujo
    (`TypePickerDialog.kt`, `ParkingFormDialog.kt`, `BoulderFormFlow.kt`…)
    + un `ProposeContributionState` que modele el paso actual como sealed
    interface (el estado del wizard ya no vive disperso).
  - `MainScreen.kt` → extraer el grafo a funciones de extensión
    `NavGraphBuilder.schoolsGraph(...)`, `NavGraphBuilder.profileGraph(...)`
    (patrón oficial de Navigation Compose), y las ~17 lambdas duplicadas de
    `ProfileScreen` (versión tab vs versión sheet) a una clase
    `ProfileNavigator` que se construye una vez.
  - Regla a futuro: **ningún fichero de UI > 500 líneas, ningún composable
    > 150**. Si lo supera, se trocea en el momento.

### 4.2 Dividir `SchoolDetailViewModel` (god class: 870 líneas, 26 dependencias)

- **QUÉ**: un solo VM mezcla detalle+forecast, notas+votos, bloques+editor,
  contribuciones, favoritos, diario, moderación admin, boletín, stats, tiles
  offline.
- **POR QUÉ**: 26 dependencias es la señal más clara de SRP roto; cada
  feature nueva de la pantalla lo engorda y cualquier test necesita mockear
  26 cosas.
- **CÓMO** (dos opciones válidas, recomendación la 1):
  1. **VMs por sección** con scope de la misma pantalla:
     `SchoolDetailViewModel` (core: escuela+forecast),
     `SchoolNotesViewModel`, `SchoolBlocksViewModel`,
     `SchoolAdminViewModel`. Compose permite varios `hiltViewModel()` en la
     misma pantalla; cada sección colecta el suyo. Ventaja: aislamiento
     total, tests pequeños.
  2. Alternativa: mantener un VM fachada y extraer *delegates* (clases
     colaboradoras inyectadas que poseen su porción de estado). Menos
     idiomático en Compose, útil si hay mucho estado cruzado.
  - En ambos casos: primero listar qué estado es realmente compartido entre
    secciones (probablemente solo `schoolId` y la escuela) — el resto se va
    con su sección.

### 4.3 `collectAsStateWithLifecycle` en toda la app

- **QUÉ**: 88 usos de `collectAsState()` plano, 0 del lifecycle-aware.
- **POR QUÉ**: los flows siguen colectando con la app en background —
  observadores de chat en tiempo real, ubicación, etc. gastando batería y
  red sin pantalla. Es el estándar recomendado por Google desde 2022.
- **CÓMO**: añadir `androidx.lifecycle:lifecycle-runtime-compose` al catálogo,
  y reemplazo mecánico `collectAsState()` → `collectAsStateWithLifecycle()`
  (mismo shape de retorno; es un buscar-y-reemplazar + imports). Una sesión.

### 4.4 Eventos one-shot con `Channel` en vez de `StateFlow<String?>`

- **QUÉ**: `_modMsg` en `AdminViewModel`, `_autoOpenVia` +
  `consumeAutoOpenVia()` en `SchoolDetailViewModel` — el patrón
  "StateFlow nullable + consumo manual".
- **POR QUÉ**: en re-suscripción (rotación, volver a la pantalla) el último
  valor se re-emite y puede re-disparar el efecto (toast duplicado,
  navegación repetida). El consumo manual es frágil y fácil de olvidar.
- **CÓMO** (patrón idiomático):
  ```kotlin
  private val _events = Channel<UiEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()
  // en la UI:
  LaunchedEffect(Unit) { viewModel.events.collect { handle(it) } }
  ```
  `UiEvent` como sealed interface (`ShowMessage`, `OpenVia(id)`, …). El
  Channel garantiza entrega exactamente-una-vez a un colector.

### 4.5 Previews del sistema de diseño

- **QUÉ**: 0 `@Preview` en 106 ficheros.
- **POR QUÉ**: con un design system propio (Cumbre) y trabajo sin Android
  Studio a mano, cada ajuste visual hoy cuesta un ciclo completo de
  CI+instalación. Las previews devuelven el feedback a segundos y documentan
  los componentes.
- **CÓMO**: empezar SOLO por `ui/components/` (los reutilizables:
  `SchoolListItem`, `HourlyScoreGrid`, `WmoWeatherIcon`, botones/cards del
  tema): un `@Preview(showBackground = true)` + otro con
  `uiMode = UI_MODE_NIGHT_YES` (tema oscuro) por componente, con datos fake
  en un `object PreviewData`. No intentar previews de pantallas completas
  con VM (requiere fakes grandes; poco retorno).

### 4.6 Terminar el i18n de verdad

- **QUÉ**: ~412 `Text("...")` hardcodeados en español conviven con
  `stringResource` en 40 ficheros. Los mensajes generados en VMs ("Aviso
  enviado", "Suspendido X días") son literales imposibles de traducir. El
  CLAUDE.md dice "i18n ES/EN completo" — no lo es.
- **POR QUÉ**: la mitad de la UI ignora el selector de idioma que ya
  construiste; un usuario EN ve mezcla.
- **CÓMO**:
  1. Activar el lint check `HardcodedText` como error en `build.gradle.kts`
     (`lint { error += "HardcodedText" }`) — así CI lista TODOS los casos y
     evita regresiones futuras.
  2. Migrar por pantalla (no big-bang) a `strings.xml` + `stringResource`.
  3. Para los VMs: nunca String final en el VM — exponer el evento/estado
     tipado (`ModerationResult.WarningSent(days=7)`) y que la UI lo traduzca
     con `stringResource(R.string.warning_sent, days)`. Regla: **los VMs no
     conocen idiomas**.
  4. En iOS, mismo criterio con `Localizable.strings` (la paridad exige
     migrar ambos).

### 4.7 Accesibilidad mínima

- **QUÉ**: 46 de 117 iconos con `contentDescription = null`.
- **POR QUÉ**: TalkBack no puede anunciar iconos accionables; además Play
  está subiendo el listón de accesibilidad en revisión.
- **CÓMO**: auditar los 46: si el icono es **decorativo** (acompaña a un
  texto visible), `null` es CORRECTO — dejarlo. Si es **accionable sin
  texto** (IconButton solo), poner descripción de la acción
  (`stringResource(R.string.cd_share)`). El lint `ContentDescription`
  ayuda a listarlos.

### 4.8 Limpiezas menores

- Reemplazar los nombres totalmente cualificados inline
  (`androidx.compose.runtime.mutableStateOf` en medio del código) por
  imports normales — legibilidad; es residuo de ediciones rápidas.
- Eliminar `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt` (plantillas
  vacías que inflan la sensación de cobertura).
- Actualizar el CLAUDE.md donde dice "Retrofit + OkHttp + Moshi": la capa
  real compartida es **Ktor + kotlinx.serialization**. La doc desactualizada
  confunde a las sesiones futuras (esta revisión lo sufrió).

---

## BLOQUE 5 — Seguridad y robustez restantes

### 5.1 Rate limiting de verdad (M2)

- **QUÉ**: `RateLimitFilter` (a) confía en el primer valor de
  `X-Forwarded-For`, que el cliente puede falsificar rotándolo por request;
  (b) es un `ConcurrentHashMap` en memoria por instancia (con N réplicas el
  límite se multiplica por N); (c) al pasar de 10.000 IPs hace
  `counters.clear()` — resetea la ventana de TODOS de golpe.
- **POR QUÉ**: como control anti-abuso es evadible. Mitigado hoy por la caché
  de forecast, pero débil.
- **CÓMO** (en orden de coste):
  1. **Barato**: usar la IP del peer real o el ÚLTIMO valor añadido por el
     proxy de Railway a `X-Forwarded-For` (el único no falsificable, porque
     lo añade tu proxy), no el primero. Verificar con Railway cuántos proxies
     hay delante para saber qué posición es de fiar.
  2. Sustituir el `clear()` por expulsión LRU (p. ej. una `Caffeine` cache
     con `maximumSize` y `expireAfterWrite(1, MINUTES)` como contador —
     ya tienes Caffeine en el classpath).
  3. **Si algún día hay ≥2 réplicas**: mover el contador a un almacén
     compartido (Redis con `INCR`+`EXPIRE`, o Bucket4j con backend Redis).
     Hasta entonces, documentar la asunción de instancia única.
  4. Alternativa arquitectónica: rate limiting por `uid` (no por IP) en los
     endpoints autenticados — el uid no se puede falsificar.

### 5.2 Cachear el check de baneo

- **QUÉ**: `FirebaseTokenFilter` hace `isBanned(uid)` → un SELECT por CADA
  request autenticada.
- **POR QUÉ**: coste por request y presión de pool bajo carga; el dato cambia
  rarísima vez.
- **CÓMO**: `@Cacheable("bans")` con Caffeine `expireAfterWrite(60s)` (un
  baneo tarda ≤60s en aplicar — aceptable), y **evict explícito** de la
  entrada al banear/desbanear desde `UserModerationService` para que el corte
  sea inmediato en la instancia que ejecuta la acción.

### 5.3 Afinar CORS

- **QUÉ**: `allowCredentials(true)` + `allowedHeaders("*")` con orígenes
  restringidos.
- **POR QUÉ**: la API es stateless con Bearer (no usa cookies) →
  `allowCredentials(true)` no aporta nada y relaja la postura.
- **CÓMO**: `allowCredentials(false)` y `allowedHeaders("Authorization",
  "Content-Type")`. Probar la PWA después (es la única consumidora CORS).

### 5.4 No filtrar mensajes internos de excepción

- **QUÉ**: `server.error.include-message: always` en `application.yaml`.
- **POR QUÉ**: cualquier excepción no controlada expone su mensaje interno
  (rutas, SQL, clases) al cliente.
- **CÓMO**: al terminar 1.1 (advice global con mensajes controlados), cambiar
  a `include-message: never` — el advice ya da mensajes útiles y seguros a
  las apps. Hacerlo en este orden, no antes (las apps muestran `message`).

### 5.5 Higiene de credenciales (acciones de Rodrigo, sin código)

- **Rotar las contraseñas de las dos BD de Railway** (quedaron pegadas en un
  chat — apuntado en CLAUDE.md desde hace semanas). Railway → cada Postgres →
  regenerar credenciales; el backend las lee por env var, solo hay que
  actualizar la variable.
- Setear `INVITE_SECRET` en Railway prod+staging (pendiente desde 2026-07-03;
  las invitaciones HMAC de quedadas lo usan).
- Verificado en esta revisión (2026-07-09): **cero secretos en el código o en
  los workflows de ambos repos** (todo va por `${{ secrets.* }}` y ficheros
  gitignoreados; los que parecen claves en CI son stubs dummy de test).
  Los checks de API keys de Google/Firebase ya están restringidos (revisión
  2026-06).

### 5.6 Cabeceras de seguridad (menor, API JSON)

- **CÓMO**: en `SecurityConfig`, dentro de `http.headers(...)`: HSTS
  (`includeSubDomains`, max-age 1 año — Railway ya sirve HTTPS),
  `X-Content-Type-Options: nosniff` (default de Spring Security, verificar
  que no se deshabilitó). CSP no aplica a una API JSON pura, pero SÍ a las
  landings HTML de `/s/*` — añadir ahí una CSP simple cuando se refactorice
  `ShareController` (3.2).

---

## BLOQUE 6 — Proceso e infraestructura de release

### 6.1 Workflow de release Android (AAB) en Actions

- **QUÉ**: hoy el AAB de Play se compila EN LOCAL con el keystore del PC; no
  hay workflow de release Android.
- **POR QUÉ**: el release depende de una máquina concreta; es el paso más
  frágil del pipeline y el único no reproducible.
- **CÓMO**: workflow `android-release.yml` con `workflow_dispatch` (manual,
  como el de iOS): secrets `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
  → decodifica el keystore → `./gradlew :app:bundleRelease` → sube el AAB
  como artifact. Fase 2 opcional: subida automática a Play con
  `r0adkll/upload-google-play` + un service account de Play Console (track
  `internal` primero).

### 6.2 Retención de artifacts (la causa del CI rojo del 2026-07-09)

- **QUÉ**: el fallo "iOS CI: All jobs have failed" del 9 de julio fue
  **cuota de almacenamiento de artifacts agotada** (el build compiló bien).
- **CÓMO**: en `android-ci.yml` e `ios-ci.yml`, en el paso
  `actions/upload-artifact`, añadir `retention-days: 7` (los APK/IPA de
  debug caducan solos; nunca necesitas uno de hace un mes). Borrar los
  artifacts viejos acumulados una vez desde la pestaña Actions (o via API)
  para liberar la cuota ya.

### 6.3 Branch protection técnica

- Ver 2.4.5. `main` de ambos repos: requerir CI verde + prohibir force-push.
  En el backend considerar exigirlo también para `develop`.

### 6.4 Monetización — checklist previo obligatorio (legal + tiendas)

Los 3 pasos ANTES de activar cualquier cobro (suscripción, pago, anuncios),
en este orden:

1. **API comercial de Open-Meteo** (o migrar la previsión horaria a AEMET).
   La API gratis es CC-BY-NC — válida SOLO mientras la app no genere ningún
   ingreso (hoy cumple: gratis, sin anuncios, con atribución). El cambio es
   **solo backend** (verificado 2026-07-10: las apps nunca llaman a
   Open-Meteo; solo `OpenMeteoClient`/`OpenMeteoArchiveClient`): leer env
   `OPENMETEO_API_KEY` → si existe, host `customer-api.open-meteo.com` +
   `&apikey=` (mismo patrón que `AEMET_API_KEY`). **No requiere subir
   versión a ninguna tienda** — efecto inmediato para todas las apps
   instaladas. Consumo actual ~96 llamadas/día fijas (prefetch batch) → el
   plan comercial más barato sobra.
2. **DSA a "comerciante"** en App Store Connect → Negocio (hoy declarado
   "no comerciante", 2026-07-10). Exige dirección/teléfono/email verificados
   por Apple (~días) que se publican en la ficha de la UE.
3. **Acuerdo para apps de pago** (App Store Connect → Negocio → Acuerdos,
   está en "Nuevo" sin firmar) + datos bancarios/fiscales. Equivalente en
   Play: cuenta de comercio (Play Console → Monetizar → Empezar).

### 6.5 Observabilidad mínima

- **QUÉ**: hoy el diagnóstico en prod es leer logs de Railway a mano.
- **CÓMO** (proporcional a un proyecto indie, sin sobre-ingeniería):
  1. **Crash reporting en las apps**: Firebase Crashlytics (ya tienes
     Firebase; es añadir el plugin + SDK). Sin esto, los crashes de los
     testers son invisibles salvo que te escriban.
  2. Backend: logs estructurados en los puntos calientes (aprobar
     contribución, push, forecast fetch fallido) con uid+entidad — ya se
     hace parcialmente; unificar formato.
  3. Un uptime monitor gratuito (UptimeRobot o similar) contra
     `/actuator/health` de prod con aviso a tu email.

### 6.6 iOS: mismas reglas

- La deuda estructural iOS es de tamaño de fichero (`SchoolDetailView.swift`
  2.583 líneas, `AdminView.swift` 1.582) — aplicar el mismo criterio de
  troceo que 4.1 cuando se toquen (extraer subviews por sección; en SwiftUI
  además mejora el type-checking lento que ya has sufrido con las
  concatenaciones de Strings).
- Cuando exista `commonTest` (2.1), la mayor parte de la lógica compartida
  queda cubierta también para iOS "gratis" — es otra razón por la que 2.1 es
  la mejora con más retorno de todo el documento.

---

## Sobre el acceso a la API de Claude (verificado 2026-07-09)

Pregunta de Rodrigo: *"revisa que nadie tiene acceso a mi API de Claude por
trabajar contigo"*. Resultado de la revisión:

- **Grep completo de ambos repos** (`sk-ant`, `anthropic`, `claude.*key`,
  `CLAUDE_API`): **cero claves**. Las únicas coincidencias son nombres de
  ramas `claude/**` en metadatos internos de git (`.git/`), que no son
  credenciales ni se publican.
- **Workflows de GitHub**: los únicos secretos referenciados son los de
  Firebase/App Store/keystores (`secrets.GOOGLE_SERVICES_JSON`, etc.),
  gestionados por GitHub Secrets. Ninguno relacionado con Claude/Anthropic.
- **Cómo funciona la sesión**: las sesiones web de Claude Code se autentican
  contra TU cuenta de claude.ai; esa credencial vive en la infraestructura de
  Anthropic, **nunca entra en el repositorio ni en los commits**. Nada de lo
  que se ha committeado o pusheado contiene tokens de sesión ni claves.
- Conclusión: **nadie obtiene acceso a tu API/cuenta de Claude por el código
  de estos repos**. Las únicas credenciales que existen en el proyecto están
  en GitHub Secrets (cifrados) y en ficheros locales gitignoreados
  (verificado de nuevo: `.gitignore` correcto en ambos repos).

---

## Orden de ataque recomendado (resumen ejecutable)

| # | Qué | Repo | Esfuerzo | Retorno |
|---|---|---|---|---|
| 1 | Advice global + `@Valid` + límites de tamaño (1.1-1.4) | API | 1 sesión | Muy alto |
| 2 | EAGER→LAZY meetups + N+1 (1.5-1.6) | API | ½ sesión | Alto (bug latente) |
| 3 | `retention-days` + branch protection + CI en develop + Dependabot (2.4, 6.2, 6.3) | ambos | ½ sesión | Alto |
| 4 | `commonTest` + dispatchers inyectados + primeros tests (2.1-2.2) | Android | 1-2 sesiones | Muy alto |
| 5 | Tests de `ReviewContributionUseCase` + autorización (2.3) | API | 1 sesión | Muy alto |
| 6 | Trocear `ReviewContributionUseCase` con strategy (3.3) | API | 1 sesión | Alto |
| 7 | `collectAsStateWithLifecycle` + eventos con Channel (4.3-4.4) | Android | ½ sesión | Medio |
| 8 | Puertos backend por agregado, incremental (3.1-3.2) | API | 1 agregado/sesión | Medio |
| 9 | Use cases para las `Ktor*Api` de los VMs, incremental (3.4-3.6) | Android | al tocar cada pantalla | Medio |
| 10 | Trocear SchoolMap / ProposeFlow / SchoolDetailVM (4.1-4.2) | Android | 2-3 sesiones | Alto a largo plazo |
| 11 | i18n completo + lint HardcodedText (4.6) | Android+iOS | 2 sesiones | Medio |
| 12 | Rate limit + caché de ban + CORS + headers (5.1-5.4, 5.6) | API | 1 sesión | Medio |
| 13 | Rotar credenciales BD + INVITE_SECRET (5.5) | Railway | 15 min | Alto (higiene) |
| 14 | Workflow release Android + Crashlytics + uptime (6.1, 6.5) | ambos | 1 sesión | Medio |
| 15 | Previews + accesibilidad + limpiezas (4.5, 4.7, 4.8) | Android | continuo | Bajo-medio |

**Reglas de disciplina para no regenerar la deuda** (gratis, desde ya):
1. Ningún fichero de UI > 500 líneas; ningún composable > 150.
2. Todo String que llegue a BD lleva `@Size`.
3. Los VMs no conocen idiomas ni `Ktor*Api`: solo use cases y eventos tipados.
4. Endpoint nuevo = puerto + use case + `@Valid` + un test de contrato.
5. Feature nueva en `shared/` = test en `commonTest` en el mismo PR.
