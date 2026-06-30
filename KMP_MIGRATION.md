# Plan de migración a Kotlin Multiplatform (KMP) — Android + iOS

> Decisión tomada (2026-06-06): la app MeteoMontana será multiplataforma usando
> **Kotlin Multiplatform (KMP)**. Compartiremos `domain/` y `data/` en Kotlin
> entre Android e iOS. La UI Android sigue siendo Jetpack Compose. La UI iOS
> será **SwiftUI** consumiendo el módulo compartido.

---

## 📍 ESTADO ACTUAL DE LA MIGRACIÓN

> **Esta sección se actualiza al final de cada sesión.** Una sesión nueva
> debe leer SOLO esta sección y ya sabe por dónde seguir.

**Última actualización:** 2026-06-30 — Quedadas completas y en producción (app +
backend); material de escalada; i18n ES/EN; filtros de alerta completos.

---
### Feature: Quedadas para escalar (MEETUPS_DESIGN.md) — ✅ COMPLETA, en producción

Todas las fases (1-9: backend, shared, Android, iOS, moderación, alertas,
material, diff de admin, diario por lineId) están en `main` de ambos repos
(app y backend = producción). Detalle completo de cada fase en la bitácora de
`CLAUDE.md` (sesiones del 2026-06-20 al 2026-06-30).

**Sesión 2026-06-30 — cierre de Quedadas + i18n:**
- ✅ **Material de escalada**: cada participante indica qué material lleva
  (crashpads en bloque; cintas/cuerda/gri-gri en vía). Sección MATERIAL en el
  detalle + banner desplegable en el chat de grupo. Backend V37
  (`gear_json` en `meetup_members`), endpoint `PUT /meetups/{id}/my-gear`.
- ✅ **Filtros completos en la alerta de quedadas**: modalidad (ambas/bloque/
  vía), privacidad (todas/abiertas/seguidos-seguidores/no mixto con gate de
  género), distancia (50/100/200km), escuela específica. **Bug corregido**:
  `matchesDays` comparaba día-de-semana (1-7) contra fechas ISO completas que
  manda la app → las alertas nunca coincidían realmente. Backend V38
  (`discipline`/`privacy`/`max_distance_km`/`user_lat`/`user_lon` en
  `meetup_alerts`). `MeetupAlertScreen.kt` (Android) y `MeetupAlertView`
  (iOS) reescritas con todos los filtros + gate de género.
- ✅ **Internacionalización ES/EN** (Android + iOS completa): ~45 ficheros
  Android con `stringResource()`, ~25 ficheros iOS con `NSLocalizedString`,
  `HelpCatalog` bilingüe, selector de idioma al primer arranque + opción en
  Perfil/Cuenta (Android: `AppCompatDelegate.setApplicationLocales`; iOS:
  swizzling de `Bundle`, cambio en caliente sin reiniciar). Verificado por CI.
- ✅ **App Store**: app "Cumbre Climbing" creada en App Store Connect.
  `https://climbingteams.com/support.html` desplegado (página de soporte +
  FAQ). Checklist completo en `APP_STORE_CHECKLIST.md`.
- ✅ Todo mergeado: app → `main`, backend → `main` (producción, Railway
  redesplegado). AAB v1.8/11 + `.ipa` de producción generados.

**Próximo paso** (sesión siguiente — Apple Developer ya aprobada y pagada):

1. **Probar en dispositivo real** lo hecho hoy: filtros nuevos de la alerta de
   quedadas (modalidad/privacidad/distancia/escuela + que ahora SÍ lleguen
   notificaciones, antes nunca coincidían por el bug de fechas) y el selector
   de idioma ES/EN (primer arranque + cambiarlo desde Perfil/Cuenta, en ambas
   plataformas). `.ipa` de producción de hoy ya descargado en
   `C:\Users\rouma\ipa-serve\MeteoMontana.ipa` listo para AltStore.
2. **Activar Sign in with Apple** (botón ya existe en LoginView pero está
   desactivado / fallaría si se pulsa):
   - Descomentar `com.apple.developer.applesignin` en `iosApp/project.yml`
     (línea ~50, ya localizada)
   - Firebase Console → climbingteams → Authentication → Sign-in method →
     habilitar proveedor **Apple**
   - Recompilar con firma real (ver punto 3, van juntos)
3. **Generar compilación firmada para App Store Connect** (el `.ipa` actual
   del CI es SIN firmar, solo vale para AltStore):
   - Crear **Certificado de distribución** + **Provisioning Profile** en
     developer.apple.com → Certificates, Identifiers & Profiles
   - Compilar vía GitHub Actions (sin Mac) con esos certificados como secrets
   - Subir con Transporter (app de Apple) o `xcrun altool`
4. **Activar APNs** (push real, código ya listo en `PushManager.swift`):
   crear key .p8 en developer.apple.com → subir a Firebase Cloud Messaging →
   descomentar `aps-environment` en `project.yml` → `enabled = true`
5. Completar ficha de App Store Connect: capturas de pantalla (mínimo 3,
   iPhone 6,5", pendiente de hacer con el iPhone real), subir la compilación
   firmada, rellenar "Información para el equipo de revisión" — la app exige
   login, crear una cuenta de Google de prueba para que el revisor entre con
   "Continuar con Google" (rellenar usuario/contraseña + nota explicativa).
6. Subir el AAB v1.8/11 (ya generado, ruta en la bitácora de CLAUDE.md) a
   Play Console, prueba cerrada.

Detalle completo y checklist marcable en `APP_STORE_CHECKLIST.md`.

---

**Última actualización anterior:** 2026-06-15 (PRIMERA SESIÓN EN MAC — Fase E arrancada).
🎉 **Hito: la app iOS COMPILA, INSTALA y ARRANCA en el simulador mostrando las
191 escuelas reales del backend de Railway.** Validación end-to-end de toda la
arquitectura KMP (Ktor desde iOS + SKIE async/await + DI Kotlin + SQLDelight
nativo). Resumen de hoy:
- ✅ **Fase A/B/C** (pre-Mac) seguían correctas.
- ✅ **Fase D** — app iOS registrada en Firebase `climbingteams`,
  `GoogleService-Info.plist` colocado en `iosApp/iosApp/` (gitignored).
- ✅ **Fase E1/E2** — framework iOS compila. Errores de Kotlin/Native que
  Windows no podía ver, ya arreglados:
  1. ABI klib: **Ktor 3.1.3 → 3.0.3** y **kotlinx-serialization 1.8.1 → 1.7.3**
     (las 3.1/1.8 exigen Kotlin 2.1.x; el proyecto usa 2.0.21). Android sigue
     verde (103 tests OK con estas versiones).
  2. `gradlew` sin bit de ejecución (se perdió al pasar de Windows). `chmod +x`.
- ✅ **Fase E3 (parcial)** — `xcodegen generate` OK + build SwiftUI OK en
  simulador. Fixes en `iosApp/project.yml`:
  - `PRODUCT_NAME: MeteoMontana` (faltaba → producto `.app` sin nombre).
  - `OTHER_LDFLAGS += -lsqlite3` (el driver nativo SQLDelight/sqliter referencia
    símbolos `sqlite3_*` del sistema).
  - Las firmas SKIE de `SchoolListView` compilaron sin tocar nada.

**⚠️ Nota de entorno Mac:** el CLI `xcrun simctl launch` se cuelga en este Mac
(macOS 26.3 / Xcode 26.5, muy nuevos; afecta también a apps del sistema, NO a
nuestra app). Para arrancar la app: **tocar el icono a mano en la ventana del
Simulator**. La app build/install por CLI funciona perfecto.

**Actualización 2026-06-15 (2ª sesión Mac):** ✅ **primer bridge `suspend`
funcionando end-to-end** — `LocationProvider` (CLLocationManager). El patrón
bridge queda VALIDADO: Swift conforma un protocolo Kotlin (`IosLocationBridge`)
con callbacks, y `IosLocationProvider` (iosMain) lo envuelve en `suspend` vía
`suspendCancellableCoroutine`. El tab **Tiempo** ya muestra el forecast real en
tu ubicación (reusa `ForecastBodyView`, extraído de `SchoolDetailView`). Build
iOS OK + Android 103 tests verdes. Los demás bridges (FileReader/Auth/Chat/
Storage) siguen la misma receta — ver checklist 3.4.

**Actualización 2026-06-15 (3ª sesión Mac):** ✅ **AuthService bridge** hecho
(login Google + token + StateFlow de sesión). El `authService` ya alimenta el
token del HttpClient → los endpoints autenticados funcionarán en cuanto haya
sesión. ⚠️ Falta **probar el login a mano** (tap + cuenta Google). Quedan los
bridges FileReader (fotos) y Chat/Storage, y conectar las pantallas privadas
(favoritas/perfil/notas) ahora que hay sesión + token.

**👉 Qué hacer en una sesión nueva (en el Mac):**
1. **Probar el login de Google a mano** (tocar icono "person" en Escuelas →
   "Continuar con Google"). Si falla, revisar URL scheme / clientID.
2. **Seguir con FileReader** (fotos perfil/topo) y Chat/Storage con el MISMO
   patrón bridge ya validado (`LocationBridge`/`AuthBridge`). Luego conectar
   pantallas privadas (favoritas, perfil, notas) que ya tienen token disponible.
2. **Replicar el resto de pantallas SwiftUI** desde la plantilla `SchoolListView`
   aplicando el diseño Cumbre (hoy la lista es SwiftUI pelado): detalle +
   forecast + heatmap, mapa (MapLibre iOS), login, etc.
3. **Build/instalar**: `xcodegen generate` (si tocas `project.yml`) +
   `xcodebuild ... -sdk iphonesimulator -destination 'id=<UDID>' CODE_SIGNING_ALLOWED=NO build`
   + `xcrun simctl install booted <ruta>.app` + tocar el icono.
4. Si tocas `shared/commonMain`: re-linkar el framework con
   `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` (1ª vez ~3 min).
5. Mejoras Android siguen sin bloquear iOS. Cada cambio: tests verdes + commit.

> ⚠️ Ojo build: tocar `shared/commonMain` recompila el módulo con SKIE
> (primera vez en frío ~40 min; luego incremental). NO editar ficheros Gradle
> mientras hay un build corriendo (desincroniza el catálogo de versiones).

**Modelo recomendado:** Sonnet para Fases 1.x y 2.x (refactor mecánico, plan ya escrito). Opus solo para decisiones de arquitectura ambiguas o bugs sin diagnóstico claro.

**Progreso global:**

- [x] **Fase 0** — Planificación (documento creado, decisiones tomadas).
- [x] **Fase 1.0** — Tests como red de seguridad (2 sesiones). ✅
  - [x] Sesión 1/2: 45 tests de funciones puras (Haversine, parseLatLonPaste,
    toBloquesJson, gradeStyle, LineStroke). Todos verdes. ✅
  - [x] Sesión 2/2: 33 tests de ViewModels (12 SchoolList + 12 SchoolDetail
    + 9 Admin). Todos verdes. ✅ Total Fase 1.0: 78 tests.
- [x] **Fase 1** — Refactor Clean Android. ✅ COMPLETA (1.1→1.6 todas cerradas).
  - [x] 1.1 — Use cases en `domain/usecase/`. ✅ Hecho schools, forecast,
    blocks, contributions, notes, favorites, notifications, profile y
    admin (9 use cases). Los 3 ViewModels (`SchoolListViewModel`,
    `SchoolDetailViewModel`, `AdminViewModel`) ya no dependen de
    `SchoolApi`/`AdminApi` directos (solo `AdminViewModel` mantiene
    `SchoolApi` para `getSchools()` del tab GESTIONAR — pendiente
    extraer en 1.3).
  - [x] 1.2 — Modelos de dominio puros (DTOs fuera de UiState). ✅
    16 modelos en `domain/model/`. Todos los `UiState` y consumidores
    de pantalla usan modelos de dominio. Solo `CreateBlockRequest`,
    `CreateBlockLineRequest`, `ContributionRequest`,
    `SubmitSchoolRequest`, `CreateJournalRequest`, `CreateNoteRequest`,
    `UpdateProfileRequest`, `FcmTokenRequest`, `AdminPushRequest`,
    `RejectReason` se mantienen como input DTOs (anotaciones Moshi;
    se cambian a Kotlinx Serialization en Fase 2).
  - [x] 1.3 — Partir `SchoolApi` por bounded context. ✅ 10 APIs nuevas
    (ForecastApi, BlockApi, NoteApi, ContributionApi, ProfileApi,
    FavoritesApi, JournalApi, SubmissionApi, SocialApi, NotificationApi).
    SchoolApi queda con 3 métodos (getSchools, searchSchools, getSchoolById).
    78 tests siguen verdes.
  - [x] 1.4 — Sacar dibujo del topo del Composable (instrucciones `DrawOp`). ✅
    DrawOp sealed class + TopoLineData + renderTopo() en domain/util/,
    sin imports Android. TopoPhotoCanvas y ContributionTopoDialog usan
    renderTopo() + traductor drawOp() Android-only. 7 tests nuevos → 86 total.
  - [x] 1.5 — Abstracciones de Firebase (`PhotoUploader`, `AuthService`, `ChatService`). ✅
    Interfaces en `domain/port/`. Implementaciones: `FirebaseStoragePhotoUploader`,
    `FirebaseAuthService`, `FirebaseChatService` en `data/`. Bindings en
    `RepositoryModule`. ChatViewModel, ChatListViewModel, AuthInterceptor y
    SchoolDetailViewModel migrados a inyectar las interfaces. 86 tests verdes.
  - [x] 1.6 — Wrappers de tipos Android (`FileRef` en vez de `Uri`). ✅
    FileRef (value class, domain/model/), FileReader (port), AndroidFileReader (data/).
    SchoolDetailViewModel ya no importa android.net.Uri ni Context. 86 tests verdes.
- [x] **Fase 2** — Crear módulo `shared` KMP (2-3 sesiones).
  - [x] 2.1 — Setup KMP + targets Android/iOS. ✅ Módulo `shared/` creado con
    `kotlin-multiplatform` + `android-library`. Targets: `androidTarget`,
    `iosX64`, `iosArm64`, `iosSimulatorArm64` (iOS deshabilitado en Windows,
    compila en Mac). Domain models + ports + repository + util movidos a
    `commonMain`. App consume `project(":shared")`. 86 tests verdes.
  - [x] 2.2 — Migrar `domain/usecase/` a `commonMain`. ✅ 22 use cases movidos.
    Interfaces de repositorio en commonMain: ForecastRepository, BlockRepository,
    NoteRepository, FavoritesRepository, ProfileRepository, NotificationsRepository,
    AdminRepository. Implementaciones Retrofit en app/data/repository/. Use cases
    sin @Inject (plain constructors), provistos por UseCasesModule Hilt. 86 tests verdes.
    Quedan en app/ (params DTO): CreateBlockUseCase, UpdateBlockUseCase, SubmitContributionUseCase.
  - [x] 2.3 — Migrar `data/` a `commonMain` con Ktor + Kotlinx Serialization. ✅
    DTOs con @Serializable + toDomain() en Mappings.kt. 12 KtorXxxApi classes.
    8 KtorXxxRepository classes + KtorContributionRepository. GetBlockUseCase añadido.
    CreateBlockUseCase, UpdateBlockUseCase, SubmitContributionUseCase movidos a commonMain.
    Retrofit + Moshi + OkHttp eliminados del proyecto. AuthInterceptor eliminado.
    NetworkModule reescrito con buildApiHttpClient(). RepositoryModule usa @Provides.
    ErrorMessage.kt migrado de HttpException (Retrofit) a ClientRequestException (Ktor).
    AdminViewModel.loadAllSchools() ahora usa GetSchoolsUseCase → List<School>.
    86 tests verdes.
  - [x] 2.4 — Implementaciones Firebase movidas a `shared/androidMain/`. ✅
    FirebaseAuthService, FirebaseChatService, FirebaseStoragePhotoUploader,
    AndroidFileReader en `shared/src/androidMain/`. Sin @Inject (plain constructors).
    RepositoryModule convertido a `object` con @Provides para los 4 servicios.
    Firebase deps añadidas a `shared/build.gradle.kts` via `add("androidMainImplementation", ...)`.
    `.gitignore` actualizado para excluir `shared/build/`. 86 tests verdes. assembleDebug OK.
  - [x] 2.5 — Adaptar `androidApp` para consumir `shared`. ✅
    Todos los ViewModels de `app/` dependen SOLO de use cases de `shared/commonMain`.
    Ningún VM importa KtorXxxApi directamente. SearchSchoolsUseCase añadido.
    SchoolRepository + KtorSchoolRepository implementan searchSchools().
    103 tests verdes. assembleDebug OK. Mergeado y pusheado a main.
- [~] **Fase 3** — App iOS en SwiftUI. ← **EN CURSO** (arrancada en Mac 2026-06-15).
  - [x] 3.1 — `iosApp/` con XcodeGen; framework KMP enlazado y compilando.
  - [x] 3.2 — Shared framework en SwiftUI: DI vía `IosDependencyContainer`
    (Kotlin) envuelto por `AppDependencies.swift`. Funciona: la lista carga
    datos reales del backend.
  - [x] 3.3 — Pantallas MVP iOS (2026-06-15): ✅ lista con score-badge coloreado
    + buscador + filtros estilo/roca; ✅ detalle con forecast (hero, condiciones,
    ventana óptima, mejor día, heatmap horas) en diseño Cumbre
    (`CumbreTheme.swift`). Verificado en simulador con datos reales.
  - [~] 3.4 — Ports `suspend` con patrón bridge (location/files/Firebase).
    - [x] **LocationProvider** (2026-06-15): `IosLocationBridge` (Kotlin
      iosMain) implementado en Swift con CLLocationManager (`LocationBridge`),
      envuelto por `IosLocationProvider` (suspendCancellableCoroutine).
      Tab **Tiempo** cableado al `GetForecastByLocationUseCase` compartido:
      verificado en simulador (forecast real en tu ubicación). Conformance
      Swift→protocolo Kotlin confirmada (compila + linka + corre).
    - [x] **AuthService** (2026-06-15): `IosAuthBridge` (Kotlin iosMain) +
      `AuthBridge.swift` (FirebaseAuth) + `IosAuthService` (StateFlow + suspend).
      Login con **Google Sign-In** (SPM `GoogleSignIn-iOS`, URL scheme con el
      REVERSED_CLIENT_ID en project.yml). `LoginView` (cuenta: signed-in/out +
      cerrar sesión) accesible desde el icono "person" de `SchoolListView`.
      `onOpenURL` → `GIDSignIn.handle`. `authService` ya pasado al
      `IosDependencyContainer` → endpoints autenticados reciben token. Compila +
      arranca sin crash. ⚠️ Login interactivo NO probado (requiere tap + cuenta
      Google real); pendiente de validar en dispositivo/simulador a mano.
    - [ ] **FileReader** (foto perfil/topo) bridge. ← **SIGUIENTE**
    - [ ] **ChatService** (Firestore) + **PhotoUploader** (Storage) bridges.
  - [ ] 3.5 — Mapa de escuela (MapLibre iOS) en el detalle.
- [ ] **iOS .swift en paralelo** — durante Fases 1 y 2.
  - [ ] Estructura `iosApp/iosApp.xcodeproj` (con stubs sin compilar).
  - [ ] Cada sesión que refactorice algo Android → deja .swift equivalente.
- [ ] **Fase 3** — App iOS en SwiftUI (requiere Mac, 5-8 sesiones).
  - [ ] Apertura proyecto Xcode, ajustes visuales.
  - [ ] TestFlight.

**👉 Siguiente paso concreto:** ver final del documento ("Próximo paso").

> ⏸️ **EL TRABAJO PRE-MAC ESTÁ HECHO** (Fases A, B y C-base, 2026-06-13). Lo
> que queda requiere Mac (ver Guía de implementación iOS 🍏 + "Próximo paso").
> Mientras no haya Mac, las sesiones pueden hacer mejoras en la app Android
> (no bloquean iOS) o el usuario puede adelantar la Fase D (Firebase iOS).

**Lo que necesita el usuario aportar pronto:**
- Antes de fin de Fase 2: registrar app iOS en consola Firebase y descargar
  `GoogleService-Info.plist` (5 minutos desde Windows, navegador).
- Antes de Fase 3: Mac mini M4 (~700 €) + cuenta Apple Developer ($99/año).

---

---

## 🎯 PLAN DE ATAQUE PRE-MAC (orden exacto)

> Objetivo: que cuando llegue el Mac, lo único que quede sea **compilar,
> ajustar visualmente y publicar** — no empezar. Todo lo de abajo se hace en
> Windows. Marca `[x]` al cerrar cada paso.
>
> Estado real (2026-06-13): `commonMain` = domain+data completos.
> `androidMain` = 6 impls de plataforma. `iosMain` = casi vacío.
> `iosApp/` = no existe.

### FASE A — Cerrar la lógica compartida (Windows, 100% verificable en Android) ✅ alta confianza

Que NADA de lógica de negocio quede Android-only. Cada paso: `assembleDebug`
+ `:app:testDebugUnitTest` verdes antes de commit.

- [x] **A0** — `Geo.haversineKm` unificado en `commonMain` (2026-06-13).
- [x] **A1** — `LocationProvider` → interfaz en `commonMain/domain/port/` +
  modelo `UserLocation` en `commonMain/domain/model/`. Impl
  `AndroidLocationProvider` (FusedLocation) en `app/data/location/`, enlazada
  por `@Provides` en `LocalModule`. iOS implementará la interfaz en Fase B.
  7 consumidores reconectados a la interfaz. (2026-06-13)
- [x] **A2** — `GetFavoritesWidgetDataUseCase` + `FavoriteWidgetItem` en
  `commonMain`: el ensamblado de datos del widget (favoritas + score +
  estilo/roca + distancia + orden) salió de `loadWidgetState`. El widget
  Glance solo mapea y renderiza. (2026-06-13)
- [x] **A3** — Barrido de `commonMain`: cero `import android./androidx./java./
  javax.` y cero usos inline (solo aparecen en comentarios). Sin fugas. (2026-06-13)
- [x] **A4** — Sin `androidx.room` en `commonMain`; caché/offline/stats usan
  SQLDelight. Lógica compartida limpia. (2026-06-13)

**✅ FASE A COMPLETA (2026-06-13)** — el 100% de la lógica de negocio está en
`commonMain` y compila sin dependencias de plataforma. Lista para iOS.

### FASE B — Impls iOS en `iosMain` Kotlin (solo lo no-UI) ✅ casi completa

Corrección de alcance (2026-06-13, tras leer los contratos): solo van en
`iosMain` Kotlin las impls **sin UI ni SDK externo**, que es donde
Kotlin/Native brilla. Las de UI/sistema (ubicación, ficheros) y Firebase son
**interop frágil en Kotlin/Native** y se escriben mucho mejor en **Swift**
(Fase C), implementando la misma interfaz de `commonMain` (Kotlin expone las
interfaces a Swift por el framework). No se compilan en Windows; se validan
en Xcode (Fase E2).

- [x] **B1** — `DatabaseFactory` iOS (NativeSqliteDriver). Ya existía.
- [x] **B2** — `IosNetworkMonitor` (NWPathMonitor). (2026-06-13)
- [→] **B3** — `LocationProvider`, `FileReader`, Firebase Auth/Chat/Storage:
  **movidos a Fase C (Swift)**. Ver lista C0.

### FASE C — App SwiftUI `iosApp/` + impls Swift de ports (Windows, "a ciegas") ⚠️ media confianza

- [ ] **C0 — Impls Swift de los ports de `commonMain`** (clases Swift que
  implementan las interfaces Kotlin, inyectadas en la DI iOS):
  - `IosLocationProvider` (CLLocationManager + delegate → `current()`).
  - `IosFileReader` (UIImage + `jpegData(compressionQuality:)`).
  - `IosFirebaseAuthService`, `IosFirebaseChatService`,
    `IosFirebaseStoragePhotoUploader` (Firebase iOS SDK vía SPM).
- [ ] **C1** — Estructura: carpetas, `Info.plist`, `MeteoMontanaApp.swift`.
- [ ] **C2** — DI iOS (`AppDependencies.swift`, manual o Koin) instanciando
  repos/use cases del `shared`.
- [ ] **C3** — Pantallas MVP espejo de las Composables: Login, Lista de
  escuelas, Detalle (forecast + heatmap + franja de roca), Mapa.
- [ ] **C4** — Resto: notas, diario, perfil, proponer parking/piedra, chat,
  admin. Cada `.swift` con `#Preview` y datos falsos.

### FASE D — Lo que haces TÚ en Windows (navegador, 10 min)

- [ ] **D1** — Registrar app iOS en Firebase `climbingteams` (Add app → iOS →
  bundle id `com.meteomontana.ios`) y meter `GoogleService-Info.plist` al repo.

### FASE E — SOLO con Mac (lo mínimo posible)

- [ ] **E1** — Generar framework: `./gradlew linkReleaseFrameworkIosSimulatorArm64`.
- [ ] **E2** — Arreglar errores de compilación de `iosMain` (los que Windows
  no pudo ver).
- [ ] **E3** — Abrir Xcode, build SwiftUI, ajustes visuales, simulador.
- [ ] **E4** — Cuenta Apple Developer ($99/año), provisioning, Sign in with Apple.
- [ ] **E5** — TestFlight → beta → App Store.

**Reparto honesto de confianza:** Fase A es oro (verificable, además mejora el
Android). Fases B y C se escriben a ciegas: ahorran muchísimo tiempo de Mac,
pero TENDRÁN errores que arreglar en E2/E3 — es normal y esperado. Fase D son
10 min tuyos. Con A–D hechas, el Mac es "rematar", no "arrancar".

---

## ✅ Paridad iOS — checklist exhaustivo (objetivo: idéntica a Android)

> Meta del usuario (2026-06-15): la app iOS debe ser **exactamente igual** que
> Android — todas las pantallas, mismo diseño Cumbre, mismos datos.
> Trabajamos desde el código Android. Estado y dependencias de cada bloque:

**HECHO (sin infra extra):**
- [x] Tema Cumbre (`CumbreTheme.swift`): colores, scoreColor/scoreLabel, serif/mono.
- [x] Fuentes reales bundladas: Source Serif 4 + JetBrains Mono (UIAppFonts).
- [x] **Lista de escuelas** clavada (header iconos, "Escuelas"+count+Enviar,
  banner ☕, buscador, chips ESTILO/ROCA, fila rica: badge tintado + rank +
  nombre serif + estrella + subtítulo + heatmap 10 celdas + ●SECA/MOJADA).
- [x] **Detalle** clavado (ForecastBody): SÍ/NO, ÍNDICE/100, banda roca, desglose
  factores, tiempo actual, 16h DESDE LA HORA ACTUAL, 8 celdas condiciones,
  7 días, mejor día.
- [x] **Barra de tabs** Tiempo·Escuelas·Radar (MainTabView).
- [x] **Radar** clavado (WKWebView Windy).
- [x] **CÓMO LLEGAR** (Google Maps) en el detalle.

**LocationProvider bridge (CLLocationManager) — HECHO:**
- [x] Tab **Tiempo** (WeatherScreen): forecast por ubicación + **grid de favoritas** + forecastBody.
- [x] Distancia "· N KM" en el subtítulo de cada fila de la lista (Geo.haversineKm).
- [ ] Punto azul del usuario en los mapas (pendiente de MapLibre).

**PENDIENTE — necesita MapLibre iOS (SPM):**
- [ ] **Mapa de escuela** en el detalle (markers parking/bloque/zona, popup,
  CÓMO LLEGAR por marker, "+ PROPONER").
- [ ] **Mapa global** en la lista (SchoolsMapPanel).
- [ ] Mapas admin (FullScreenMapDialog).

**PENDIENTE — necesita AuthService bridge (Firebase Auth Google Sign-In, patrón
bridge). Desbloquea TODO lo privado:**
- [x] **Login al arrancar** (gate obligatorio = AppRoot.kt): `RootView` muestra
  `LoginView` (marca CUMBRE + Google) sin sesión, `MainTabView` con sesión.
  `SessionStore` a nivel de app. `AccountView` (perfil + cerrar sesión) desde
  el icono de persona. **Pendiente: Sign in with Apple** (requisito App Store
  si se mantiene Google).
- [x] **Perfil** (AccountView: avatar, nombre, usuario, bio, grado, badges).
  Falta EditProfile + cambiar foto (→ PhotoUploader bridge).
- [ ] **Diario** (JournalEntries/JournalSchools + AddBlockSheet).
- [x] **Favoritas** (estrella optimista en lista/detalle + grid en Tiempo).
- [x] **Notas** de escuela (leer + publicar texto). Falta adjuntar foto (bridge Storage).
- [x] **Notificaciones** (NotificationsView: inbox + marcar leídas).
- [ ] **Chats** (ChatList + Chat) → necesita ChatService bridge (Firestore).
- [ ] **Proponer** escuela/parking/piedra (Submit + ProposeContributionFlow +
  editor topo) → necesita PhotoUploader + FileReader bridges.
- [x] **Usuarios**: buscar (SearchUsersView), perfil público (PublicProfileView),
  seguir/dejar de seguir optimista, solicitudes (FollowRequestsView). Falta
  listas de seguidores/seguidos (use cases ya wired).
- [x] **Mis propuestas / mis contribuciones** (listas de solo lectura en el perfil).
- [ ] **Admin** (cola de propuestas, gestionar bloques).

**PENDIENTE — necesita use case nuevo en shared (backend ya tiene el endpoint):**
- [ ] **Stats mensuales últimos años** (GET /api/schools/{id}/monthly-stats):
  hoy es Android-only (Room). Crear KtorMonthlyStatsApi + repo + use case en
  shared, exponer en IosDependencyContainer, y MonthlyStatsSection en el detalle.

**Caché local — HECHO:**
- [x] Caché del catálogo (SQLDelight, stale-while-revalidate): la lista pinta
  desde caché al instante y revalida desde red; funciona offline. La BD la crea
  Swift con `DatabaseFactory().create()` y se pasa al `IosDependencyContainer`.

**PENDIENTE — otros:**
- [x] Modo oscuro (luna del header cicla sistema/claro/oscuro) + paleta dark.
- [ ] Iconos WMO como SVG reales (hoy SF Symbols aproximados).
- [ ] DayDetail, Compare, SavedSchools, WeekendAlert.

**Orden recomendado:** (1) LocationProvider bridge → Tiempo+distancias;
(2) AuthService bridge → login + todo lo privado; (3) MapLibre → mapas;
(4) monthly stats; (5) modo oscuro + pulido. Cada bridge: ver patrón abajo.

## 🍏 Guía de implementación iOS (decisiones técnicas, 2026)

Investigado y decidido en la sesión 2026-06-13. Esto es lo que hace que el
tiempo de Mac sea mínimo.

### Stack
- **SKIE** (Touchlab, plugin Gradle en `shared`, v0.10.12): expone `StateFlow`
  como `AsyncSequence` y `suspend` como `async throws` en Swift. Ya aplicado +
  framework `Shared` (estático) declarado en `shared/build.gradle.kts`.
- **Motor HTTP iOS**: `ktor-client-darwin` en `iosMain` (ya añadido).
- **DI**: **factory manual en Kotlin** — `di/IosDependencyContainer` (commonMain)
  construye HttpClient + apis + repos + use cases. Swift solo instancia el
  contenedor. NO usamos Koin (evitamos Hilt+Koin a la vez). Compila para
  androidTarget → verificable en Windows.
- **Firebase iOS**: por **SPM** (no CocoaPods, que pasa a solo-lectura dic 2026),
  declarado en `iosApp/project.yml`. Swizzling desactivado
  (`FirebaseAppDelegateProxyEnabled=false`) por ser SwiftUI.
- **Proyecto Xcode**: vía **XcodeGen** (`iosApp/project.yml`) → `xcodegen
  generate` en el Mac crea el `.xcodeproj` (no se versiona el binario).
- **Framework**: build phase de Gradle `:shared:embedAndSignAppleFrameworkForXcode`.

### El problema clave: `suspend` en los ports (Swift→Kotlin)
SKIE deja a Swift **llamar** `suspend` de Kotlin como `async` sin problema. Pero
**implementar** un `suspend` de Kotlin *desde* Swift (que es lo que hacen los
ports `LocationProvider`, `FileReader`, `PhotoUploader`, `AuthService`,
`ChatService`) NO es directo. Solución recomendada = **patrón bridge**:
1. Swift implementa un protocolo simple **con callbacks** (no suspend), p. ej.
   `IosLocationBridge { func current(_ cb: @escaping (UserLocation?) -> Void) }`.
2. En `iosMain`, una clase Kotlin implementa la interfaz `suspend` del port
   envolviendo el bridge Swift con `suspendCancellableCoroutine`.
3. El bridge Swift se inyecta al `IosDependencyContainer`.
→ Hacer esto **con el Mac** (hay que verificar conformance/ABI). Por eso en
Windows NO escribimos estos impls a ciegas: irían mal seguro.

### Lo que SÍ está hecho en Windows (Fase C parcial)
- `iosApp/project.yml` (XcodeGen + Firebase SPM + framework).
- `iosApp/iosApp/MeteoMontanaApp.swift` (entry + FirebaseApp.configure()).
- `iosApp/iosApp/DI/AppDependencies.swift` (envuelve el contenedor Kotlin).
- `iosApp/iosApp/Screens/SchoolListView.swift` (pantalla MVP de referencia:
  framework → DI → use case async → SwiftUI). Plantilla para el resto.
- `di/IosDependencyContainer` (Kotlin, commonMain) — grafo público MVP.

### Pasos en el Mac (Fase E, en orden)
1. `brew install xcodegen`; en `iosApp/`: `xcodegen generate`.
2. Abrir en Xcode; resolver paquetes SPM (Firebase).
3. Bajar `GoogleService-Info.plist` (Fase D) al target.
4. Build: arreglar firmas SKIE en `SchoolListView` (async, opcionales boxed).
5. Implementar los ports con el patrón bridge (location, files, firebase).
6. Replicar pantallas restantes a partir de la plantilla.

---

## Por qué KMP y no otra opción

Auditoría real del front actual:

| Opción | Esfuerzo | Reutilización | Calidad final |
|---|---|---|---|
| **A.** Reescribir nativo Swift/SwiftUI | Alto (≈ otra vez Android) | Solo backend | Excelente, dos códigos paralelos |
| **B. KMP (elegida)** | Medio-alto | Backend + domain + data | Excelente, lógica única, UI nativa cada plataforma |
| C. Compose Multiplatform | Bajo si UI fuera Compose puro | Casi todo | **No viable hoy**: usamos MapLibre Android, `android.graphics.Canvas` en topo, Coil, Firebase con CredentialManager — todos bloqueantes |

**KMP** nos da:
- Un único cálculo de score, una única regla de mojado, un único parser de
  bloques JSON. Si cambia un grado o un umbral, cambia en los dos sitios.
- UI nativa en cada plataforma (Compose en Android, SwiftUI en iOS) →
  rendimiento y look&feel correctos.
- MapLibre iOS y Firebase iOS son SDKs maduros, los integramos en el target iOS.

---

## Lo que necesitas físicamente — estrategia "todo lo posible sin Mac"

**Estrategia confirmada (2026-06-06):** dejamos lo de Apple/Mac para el final.
Avanzamos en Windows hasta que sea **literalmente imposible** continuar sin
Mac. Eso significa hacer en Windows:

### En Windows (sin Mac, sin Apple Developer, sin coste)

- ✅ **Fase 0** entera — planificación.
- ✅ **Fase 1** entera — refactor Clean Android.
- ✅ **Fase 2** entera — crear `shared` KMP, migrar a Ktor, mover modelos a
  `commonMain`. La app Android sigue funcionando y consume el `shared`.
- ✅ **Fase 2.4 extendida** — **yo escribo los `actual` de iOS en Kotlin
  ahí mismo** (`shared/src/iosMain/`). Quedan listos esperando a un Mac
  que los compile.
- ✅ **Pseudocódigo y stubs de SwiftUI** — puedo dejar los `.swift` escritos
  en `iosApp/` en el repo. No los podemos compilar desde Windows, pero
  estarán versionados en git esperando.

Cuando termina la Fase 2 estás en un punto donde:
- El módulo `shared` compila para Android y **el código fuente de iOS está
  100% escrito** (Kotlin `iosMain` + Swift `iosApp/`).
- Falta solo: abrir el proyecto en Xcode, dar a Build, arreglar lo que salte,
  configurar provisioning profile, subir a TestFlight.

### Cuando NO se puede seguir sin Mac

Estos pasos requieren Mac obligatoriamente:

1. **Compilar el framework iOS** (`./gradlew :shared:linkDebugFrameworkIosArm64`
   requiere las herramientas de línea de comandos de Xcode, solo macOS).
2. **Abrir el proyecto Xcode** y resolver errores de SwiftUI / linker /
   provisioning.
3. **Probar en simulador** o dispositivo iOS real.
4. **Firmar y subir a TestFlight** / App Store.

### Opciones de Mac (cuando llegue el momento)

1. **Mac mini M4** (~700 € nuevo). El más barato y rinde de sobra para Xcode.
2. **MacBook Air M3** (~1200 € nuevo). Si quieres movilidad.
3. **Alquilar Mac en la nube** — MacStadium / MacinCloud / Scaleway M1.
   Caro a largo plazo (50–100 €/mes), útil solo para validar el primer build.
4. **GitHub Actions con runner macOS** — Sirve para build/release CI, no
   para desarrollo iterativo.

**Recomendación honesta:** comprar Mac cuando termine Fase 2 y veas que el
`shared` funciona y el código iOS está listo. Así el Mac llega "para acabar"
en lugar de "para arrancar".

### Lo que NO se puede dejar "para el final" — Firebase iOS

Una excepción importante: para que `FirebaseAuthService` y
`FirebaseStoragePhotoUploader` funcionen en iOS necesitamos el archivo
`GoogleService-Info.plist` del proyecto Firebase. **Esto lo puedes generar
desde Windows** entrando en https://console.firebase.google.com → proyecto
`climbingteams` → "Add app" → iOS → bundle ID `com.meteomontana.ios` (o el
que decidas). Descargas el `.plist` y lo metes en el repo. Sin Mac, sin
Apple Developer todavía.

**Esto lo haces tú una vez. Yo te aviso cuándo.** Probablemente al final
de Fase 2.

---

## Próximo paso

> ### ✅ HECHO (2026-06-27) — FASE 6 NOTIFICACIONES "QUEDADA NUEVA"
> Backend (`develop`): V33__meetup_alerts.sql + dominio/JPA/repo + GetMeetupAlertUseCase/SetMeetupAlertUseCase + hook en CreateMeetupUseCase. App (`main`): shared DTOs + use cases + DI; Android campana 🔔 en header Quedadas; iOS bell en header. Build Android verde.
> **Pendiente**: mergear `develop`→`main` del backend tras OK de Rodrigo.
>
> **Siguiente fase sugerida**: Fase 8 — Foto de quedada (subir foto al crear; el modelo ya tiene `photoUrl`), o mergear el backend y hacer prueba real de la notificación.

> ### ✅ HECHO (2026-06-17) — OFFLINE COMPLETO EN EL DETALLE
> Resuelto: `SchoolMapSection` (en `SchoolDetailView.swift`) ya no depende solo
> de la red. Nuevo helper `loadBlocksOnlineOrOffline()` usado por
> `.task(id: expanded)` y `reloadBlocks()`: intenta `getBlocks.invoke`; si falla
> o devuelve `[]` (sin red), cae a `savedSchools.loadOffline(id)` y mapea con
> `repo.toBlock(entity:lines:)`. Así offline salen el mapa con marcadores, las
> piedras, sus vías (de `snap.lines`) y las fotos (`TopoPhotoView` ← `ImageCache`
> en disco). Pendiente de que el CI iOS compile (sin Mac). Los **tiles** del mapa
> siguen necesitando red (futuro: `OfflineTileManager` como Android).
>
> ### ⛰️ PENDIENTE PRIORITARIO (pedido 2026-06-17) — OFFLINE COMPLETO EN EL DETALLE
> **Síntoma**: al entrar a una escuela **guardada SIN internet**, el detalle abre
> el **mapa solo con el pin de la escuela** — NO cargan los **bloques/piedras**,
> ni sus **vías**, ni las **fotos**. Rodrigo quiere que offline esté **TODO**
> (mapa + piedras + vías + fotos), igual que con red.
>
> **Causa**: en `iosApp/iosApp/Screens/SchoolDetailView.swift` el mapa y las
> piedras se cargan SIEMPRE por red con `getBlocks.invoke(schoolId:)`
> (p.ej. líneas ~102/117/413/552 y `SchoolMapSection` en ~297). Sin red eso
> devuelve `[]` → no hay bloques → el mapa solo pinta la escuela. Las fotos de
> las vías se pintan con `TopoPhotoView` (~790) desde URL/red.
>
> **Qué ya existe para apoyarse** (NO reinventar):
> - `SavedSchoolRepository.loadOffline(id)` → `OfflineSnapshot` con
>   `blocks` + `lines` (vías) + `forecast`. Mapear con `repo.toBlock(entity, lines)`
>   a `Block` del dominio (ya hay helper). `saveOffline`/`refreshOffline` ya
>   guardan bloques+vías y **pre-descargan las fotos** a `ImageCache` (FNV hash
>   en `Caches/photo-cache`).
> - `OfflineSchoolView` (desde el perfil → "Escuelas guardadas") YA lee
>   `loadOffline` y pinta piedras+fotos offline — usarlo de referencia/espejo.
> - `TopoPhotoView` ya lee de `ImageCache` cuando no hay red.
>
> **Plan sugerido**: en `SchoolDetailView`/`SchoolMapSection`, cuando
> `getBlocks` falle o no haya red (o directamente si la escuela está guardada),
> caer a `savedSchools.loadOffline(school.id)` para obtener bloques+vías y
> renderizar el mapa, las piedras y `TopoPhotoView` desde `ImageCache`. Así el
> detalle offline queda idéntico al online. (Tiles del mapa siguen necesitando
> red — eso es aparte, ver `OfflineTileManager` de Android para el futuro.)
>
> Contexto: los crashes por red (SIGABRT) ya están resueltos con `@Throws`
> (commit `c489a3f`); el forecast offline + banner "actualizado hace X" ya está
> (`188e605`/`ab68a5f`). Esto es el siguiente trozo: bloques/vías/fotos offline.

**Estado tras la preparación pre-Mac (2026-06-13):** lo máximo que se podía
hacer en Windows está hecho y verificado. El módulo `shared` se consume desde
iOS y el andamiaje iOS está montado:

- `commonMain/`: domain + data + use cases + `IosDependencyContainer` (DI) ✅
- `androidMain/`: Firebase + NetworkMonitor + FileReader + DatabaseFactory ✅
- `iosMain/`: `DatabaseFactory` + `IosNetworkMonitor` ✅
- `iosApp/`: XcodeGen (`project.yml`), Firebase SPM, app entry, DI Swift,
  pantalla MVP `SchoolListView` (plantilla) ✅
- SKIE + framework `Shared` + `ktor-client-darwin` configurados ✅

**HECHO en la 1ª sesión Mac (2026-06-15):** Fase D, framework iOS compilando,
xcodegen + build SwiftUI OK, app arranca con datos reales. Detalle arriba en
📍 ESTADO ACTUAL.

**HECHO en la 2ª sesión Mac (2026-06-15):** primer bridge `suspend`
(`LocationProvider`/CLLocationManager) funcionando end-to-end + tab Tiempo
mostrando forecast real. Patrón bridge validado (Swift conforma protocolo
Kotlin con callbacks; `IosLocationProvider` lo envuelve en suspend). Plantilla
para los demás: `iosApp/iosApp/DI/LocationBridge.swift` +
`shared/src/iosMain/.../data/location/IosLocationProvider.kt`.

**HECHO en la 3ª sesión Mac (2026-06-15):** AuthService bridge (login Google +
token + StateFlow de sesión + LoginView). Pendiente probar login a mano.

**Lo que falta (orden para la próxima sesión Mac):**
0. **Probar el login de Google a mano** (icono person → Continuar con Google).
1. **Ports `suspend` restantes con el mismo patrón bridge** (ya validado):
   FileReader (fotos) → ChatService (Firestore) + PhotoUploader (Storage).
   Luego conectar pantallas privadas (favoritas/perfil/notas) — ya hay token.
2. **Replicar pantallas SwiftUI** desde `SchoolListView` con diseño Cumbre:
   detalle de escuela + forecast + heatmap + mapa (MapLibre iOS) + login.
3. Provisioning, Sign in with Apple, TestFlight.

**Recordatorio build iOS:** xcodegen está instalado en `~/bin/xcodegen` (plan B
sin Homebrew, porque `/opt/homebrew` es del usuario `temp` y `sudo` no va en
esta cuenta). UDID simulador iPhone 16 usado: `25D70E56-D622-4B37-A44A-95FAB601BF92`.

**Pendiente Android:** nada bloqueante. Mejoras visuales/técnicas se pueden
seguir haciendo sin afectar a iOS.

### Nota de entorno

El proyecto requiere **Java 21** para compilar (Kotlin 2.0.21 no soporta
Java 25 que es la del sistema). Hay que ejecutar Gradle con
`JAVA_HOME` apuntando al JBR de Android Studio:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest
```

Desde Android Studio funciona automáticamente porque usa su JBR.
Cuando actualicemos Kotlin a 2.2.x en Fase 2 ya soportará Java 25.

---

## 🤖 Protocolo para sesiones nuevas (LEER PRIMERO)

Si estás abriendo una sesión nueva y NO tienes contexto, sigue exactamente
estos pasos:

1. **Lee `CLAUDE.md`** (raíz del repo Android). Te da el contexto general:
   stack, endpoints, convenciones.

2. **Lee este documento** (`KMP_MIGRATION.md`) y específicamente la sección
   **📍 ESTADO ACTUAL DE LA MIGRACIÓN** del principio. Ahí ves:
   - Qué está hecho y qué falta.
   - El bloque **"Qué hacer en una sesión nueva"** te dice el siguiente paso.

3. **NO repreguntes al usuario** "¿por dónde íbamos?". Ya lo sabes leyendo
   los documentos.

4. **NO replanifiques** la migración. Las fases y el orden están decididos.
   Si crees que hay un cambio importante, propónselo al usuario, pero no
   actúes sin confirmar.

5. **Ejecuta la tarea siguiente** tal como está descrita en su sección
   ("Próximo paso" al final del documento + descripción de su fase).

6. **Al final de la sesión**, ANTES de hacer commit, **actualiza dos cosas
   en este documento**:
   - El checklist de la sección **📍 ESTADO ACTUAL**: marca `[x]` las
     tareas hechas, mueve el `← SIGUIENTE` a la próxima.
   - La sección **"Próximo paso"** al final del documento: describe en
     concreto qué hará la próxima sesión (no genérico, concreto).
   - Cambia "Última actualización: AAAA-MM-DD" al inicio del estado.

7. **Commit + push a `main` de ambos repos** (Android y Backend si tocaste
   los dos). Mensaje de commit descriptivo.

### Cómo se trabaja (flujo de CADA cambio) — IMPORTANTE

El backend (`MeteoMontanaAPI`) corre en **Railway apuntando a `main`**, y la
app Android apunta a producción. Por eso cada cambio se **valida y se sube**;
nada se queda a medias en local.

1. **Compila + tests** (con `JAVA_HOME` al JBR de Android Studio, que es Java 21):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
   ```
   - `testDebugUnitTest` corre los ~100 tests (Haversine, ViewModels…).
     **Si un test se pone rojo, se arregla ANTES de commitear.** No se mergea
     en rojo (regla "CI verde antes de mergear").
   - Tocar `shared/commonMain` recompila el módulo con **SKIE** (1ª vez en
     frío ~40 min; luego incremental). **NO editar ficheros Gradle mientras
     hay un build corriendo** (desincroniza el catálogo de versiones).
2. **Instala en el móvil** (adb en `%LOCALAPPDATA%\Android\Sdk\platform-tools`):
   `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Si tocaste el
   widget, quítalo y re-añádelo en el launcher para que coja la versión nueva.
3. **Commit + push a `main`.** El backend en Railway **redespliega solo** al
   pushear a `main` (la app verá el cambio tras el deploy + pull-to-refresh).
4. **Actualiza** el `📍 ESTADO ACTUAL` + `Próximo paso` de este doc y la
   bitácora de `CLAUDE.md`.

### Reglas duras

- **Idioma del usuario**: español. **Código**: inglés.
- **Paso a paso, una cosa a la vez.** No mezclar tareas de fases distintas.
- **Verifica antes de proponer.** Lee el código existente antes de tocar algo.
- **Aplica los cambios directamente** (Edit/Write). No pegar snippets para
  que el usuario copie a mano.
- **Si tocas backend + Android**, empieza por el backend.
- **Tests verdes SIEMPRE** antes de commit (no solo en refactors): si un
  cambio rompe un test, se arregla primero.
- **No instalar nada** que requiera Mac o licencias de pago. Si lo necesitas,
  páralo y díselo al usuario.

---

## 🍎 PORT A iOS — backlog acumulado en sesiones Android

> Cada vez que en Android añadamos una feature que use APIs nativas, código
> Android-only, librerías sin port a iOS, o UI Compose, **se anota aquí**.
> Cuando arranque la fase iOS (cuando el usuario tenga Mac), esta es la lista
> de cosas que hay que portar en SwiftUI / Swift / KMP nativo.

### Features añadidas pendientes de port a iOS

#### A) Foto de perfil (subida)
- **Estado Android**: ✅ funciona. Picker `ActivityResultContracts.GetContent` →
  decode + compress con `BitmapFactory` a 1024px JPEG 80% → `PhotoUploader.
  uploadProfilePhoto(bytes)` → Firebase Storage `profile-photos/{uid}.{ext}` →
  PUT `/api/me` con `photoUrl`.
- **Lado iOS**:
  - Picker: `PHPickerViewController` o `PhotosPicker` (SwiftUI nativo iOS 16+).
  - Compresión: `UIImage.jpegData(compressionQuality:)`.
  - Inyectar `PhotoUploader` (impl iOS usando FirebaseStorage SDK).
  - Reutilizar `UpdateMyProfileUseCase` desde `shared/`.
- **Backend cambios ya aplicados** (sirven para los dos):
  - `UpdateProfileRequest` añade campo `photoUrl`.
  - `UserDtoMapper.resolvePhotoUrl()` detecta URLs https:// y las devuelve
    sin firmar.
- **Reglas Storage publicadas** (ya están vivas para los dos):
  ```
  match /profile-photos/{file} { allow read: if true; allow write: if request.auth != null && size<5MB; }
  match /piedra-photos-pending/{file} { ... }
  match /piedra-photos/{file} { ... }
  ```

#### B) Cropper de foto de perfil (rotar + zoom + circular)
- **Estado Android**: pendiente — librería `uCrop` (o cropper custom Compose).
- **Lado iOS**: `TOCropViewController` (Pod Swift madura) o cropper custom SwiftUI
  con `MagnificationGesture` + `RotationGesture`. Ambos producen `Data` de
  bytes que pasan al `PhotoUploader` compartido.

#### C) Pantalla `DayDetailScreen` (detalle de día clickable)
- **Estado Android**: ✅ Compose. ÍNDICE DEL DÍA + condiciones (MÁX/MÍN/VIENTO/
  LLUVIA/PROB) + tabla "PRÓXIMAS 24H" con icono WMO + temp + % + mm + viento +
  minibarra de lluvia.
- **Lado iOS**: SwiftUI equivalente. Datos vienen ya del `Forecast` del
  `shared/`. Tab y nav del schoolId/lat/lon/dayIndex igual.
- Helper `Modifier.clickable { onDayClick(i) }` en `DayRow` → en iOS, gesto
  `.onTapGesture { ... }` en cada fila.

#### D) Stats mensuales últimos 3 años + cache local
- **Estado Android**: ✅ `MonthlyStatsRepository` + Room (`MonthlyStatsEntity`,
  TTL 180d) + `OpenMeteoArchiveClient` (Ktor → archive-api.open-meteo.com) +
  `ClimbScore.kt` (port simplificado del scoring de la PWA).
- **Decisión clave para iOS**: **migrar persistencia a SQLDelight** desde
  `shared/commonMain` ANTES de portar. Razón:
  - Room es Android-only (no compila en Mac/iOS).
  - SQLDelight es KMP-friendly, mismo SQL en ambos.
- **Tareas concretas**:
  1. Migrar `MonthlyStatsEntity` + `MonthlyStatsDao` → SQLDelight schema en
     `shared/commonMain/sqldelight/`.
  2. Mover `MonthlyStatsRepository` a `shared/commonMain` (no usa nada
     Android-only).
  3. Mover `OpenMeteoArchiveClient` a `shared/commonMain` — reemplazar
     `org.json.JSONObject` por kotlinx-serialization con `@Serializable`
     (shared ya tiene el plugin).
  4. Mover `ClimbScore.kt` a `shared/commonMain` (puro Kotlin).
  5. UI iOS: `MonthlyStatsSection` SwiftUI equivalente.
- **Beneficio**: la lógica histórica + cache se comparte 100% entre Android
  y iOS. Solo la UI cambia.

#### E) Escuelas guardadas offline (snapshot + mapa + vías)
- **Estado Android**: ✅ Room (`SavedSchoolEntity`, `SavedBlockEntity`,
  `SavedBlockLineEntity`, `SavedForecastEntity`) + `SavedSchoolRepository` +
  serialización forecast vía `ForecastJson.kt` (org.json) +
  `SavedSchoolsScreen` + botón "GUARDAR OFFLINE" + banner "● SIN CONEXIÓN ·
  Datos del 7 jun 14:23".
- **Decisión clave para iOS**: misma de D — migrar a SQLDelight.
- **Tareas concretas**:
  1. Migrar las 5 entities + 2 DAOs a SQLDelight `.sq` files.
  2. Mover `SavedSchoolRepository` y `ForecastJson` a `shared/commonMain` —
     reemplazar `org.json.JSONObject` por kotlinx-serialization (`@Serializable`
     en los data classes de Forecast — ya están serializables porque están
     en commonMain).
  3. UI iOS: `SavedSchoolsScreen` SwiftUI.
  4. UI iOS: banner offline equivalente en SchoolDetailView.
- **Fotos de bloques offline**:
  - Android usa Coil — cachea en disco automáticamente al cargar online,
    sirve la misma URL offline desde cache.
  - iOS equivalente: `Kingfisher` o `NukeUI` (configurar disk cache).
  - **No** necesita descarga manual a filesystem.
- **Tiles de mapa offline**: pendiente en Android también (MapLibre
  `OfflineManager`). En iOS, MapLibre iOS SDK tiene su propio
  `MGLOfflineStorage`. Ambos requieren código separado por plataforma.

#### F) "PRÓXIMAS 16 HORAS" desde la hora actual
- **Estado Android**: ✅ `HourlyScoreGrid` usa `java.time.LocalDateTime.now()`
  para filtrar.
- **Lado iOS**: reemplazar por `Date()` + `Calendar.current.component(.hour, ...)`
  o `kotlinx.datetime.Clock.System.now()` desde shared.
- **Mejor**: mover el filtro a `shared/commonMain` con `kotlinx.datetime` y
  exponer una propiedad `Forecast.hoursFromNow(maxN: Int)` en domain. Así
  ambos UIs llaman al mismo helper.

#### G) Detección online/offline
- **Estado Android**: implícita — si una llamada Ktor falla, se considera
  offline y `SchoolDetailViewModel` cae al snapshot Room.
- **Pendiente para los dos**: abstracción `NetworkMonitor` en
  `shared/commonMain` (interfaz). Implementaciones:
  - Android: `ConnectivityManager.NetworkCallback`.
  - iOS: `NWPathMonitor` de Network framework.
- Útil para mostrar banner global "SIN CONEXIÓN" + decidir flujos antes de
  intentar llamadas.

#### H) Logging
- **Estado Android**: `android.util.Log.i(tag, msg)` en
  `EditProfileViewModel`, `FirebaseStoragePhotoUploader`.
- **Para shared**: usar `co.touchlab:kermit` (KMP-friendly, syntax igual a
  Logcat). En iOS escribe a `os_log`. Permite poner los logs en
  `shared/commonMain` sin perder visibilidad en cada plataforma.

#### I) Acceso a ficheros (URI → bytes)
- **Estado Android**: `context.contentResolver.openInputStream(uri)` +
  `BitmapFactory`.
- **Lado iOS**: `PHPickerResult.itemProvider.loadDataRepresentation(...)`.
- **Abstracción ya existente**: `FileReader` port (`shared/commonMain/port/`).
  Falta:
  - Extender `FileReader` con método `readImageCompressed(ref, maxDim, quality)`
    para que el cropper/compresor también esté abstraído.
  - Impl Android: la lógica que ya está en `EditProfileViewModel.compressImage`.
  - Impl iOS: equivalente con `UIImage`.
- Así `EditProfileViewModel.uploadPhoto(ref: FileRef)` puede vivir en `shared/`.

#### J) Widget de home "Favoritas hoy"
- **Estado Android**: ✅ Glance (`ui/widget/FavoritesWidget.kt`). Rediseñado a
  tarjetas: por cada favorita, bloque de score + nombre + "KM · estilo · roca"
  + heatmap horario. Datos sin red extra: favoritas + scores
  (`GetMyFavoritesUseCase`, `GetTodayScoresUseCase`, ambos en `shared`) +
  catálogo cacheado (SQLDelight) para estilo/roca/coords + `LocationProvider`
  para la distancia (`Geo.haversineKm`, ya en `shared/commonMain`).
- **Lado iOS**: el widget se reescribe con **WidgetKit** (SwiftUI +
  `TimelineProvider`) — Glance no tiene equivalente portable. PERO el
  **ensamblado de datos** (ordenar favoritas por score, calcular distancia,
  decidir qué mostrar) que hoy vive en `loadWidgetState` dentro de `app/` es
  lógica de negocio reutilizable.
- **Refactor recomendado antes del port**: extraer ese ensamblado a un
  `GetFavoritesWidgetDataUseCase` en `shared/commonMain` que devuelva un
  modelo neutro (lista de `{id, name, score, dryRock, hours, style, rock,
  distanceKm}`). Así el widget Android (Glance) y el iOS (WidgetKit) llaman
  al mismo cálculo y solo cambia el render. Hoy, portar a iOS obligaría a
  reescribir esa lógica.

### Cambios de arquitectura recomendados ANTES de portar a iOS

Para que la fase iOS sea rápida y compartamos código de verdad:

1. **Migrar Room → SQLDelight** (Fase 4.x propuesta).
   - Crear `shared/commonMain/sqldelight/com/meteomontana/db/` con `.sq` files.
   - Mover `MonthlyStatsRepository`, `SavedSchoolRepository`,
     `MonthlyStatsEntity`, `SavedSchool*` a commonMain.
   - Driver en androidMain: `AndroidSqliteDriver`. En iosMain: `NativeSqliteDriver`.
2. **Migrar org.json → kotlinx-serialization** (en `ForecastJson` y
   `OpenMeteoArchiveClient`). Ya tenemos el plugin en shared.
3. **Crear `NetworkMonitor` port + impls** por plataforma.
4. **Crear `Clock`/`Now` helper** con `kotlinx.datetime` en commonMain para
   reemplazar usos de `java.time.LocalDateTime`.
5. **Extender `FileReader`** con `readImageCompressed()` para mover lógica
   de subida de foto a shared.
6. **Adoptar Kermit** para logs.
7. **Mover `LocationProvider` a `shared`** con `expect/actual`: hoy es
   Android-only en `app/data/location/` (FusedLocation). Debe ir a
   `shared/commonMain` (interfaz `expect`) + `androidMain` (FusedLocation) +
   `iosMain` (`CLLocationManager`). Lo consumen `SchoolListViewModel`, los
   mapas y el widget Favoritas.

Una vez hecho esto, el port a iOS es básicamente: setup proyecto Xcode +
SwiftUI screens espejo de las Composables + 2 impls (FileReader, NetworkMonitor)
+ inyección Koin (o init manual desde Swift).

---

