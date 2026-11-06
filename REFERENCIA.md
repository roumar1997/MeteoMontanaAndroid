# Cumbre — referencia (Android)

> Mapa del código, endpoints y decisiones de componentes. **No se carga en cada
> sesión**: se abre cuando vas a tocar esa zona. Las reglas normativas de
> arquitectura están en `ARCHITECTURE.md`; el porqué histórico, en `HISTORIAL.md`.
> Los diseños y planes viven en `docs/` (APPROACH_DESIGN, BLOCK_SEARCH_DESIGN,
> FEED/MEETUPS/WALLS_DESIGN, KMP_MIGRATION, MejorasFuturas, PLAN_PENDIENTES,
> PLAN_MEJORAS_2026-10, STORE_TEXTOS, COMPLIANCE, APP_STORE_CHECKLIST, AYUDA_INVENTARIO).

## 📁 Dónde está cada cosa

Los ficheros se parten a menudo (tope de 600 líneas): **busca con `Glob`/`Grep`**
en vez de fiarte de listas de ficheros. Las zonas son estas:

```
app/build.gradle.kts          → dependencias, versionName/versionCode (7.1.4 / vc123) y
                                API_BASE_URL (debug → staging, release → producción;
                                `-PdebugApi=prod` para ver datos reales en un debug)
gradle/libs.versions.toml     → versiones de todas las deps
app/src/main/res/xml/network_security_config.xml → IPs permitidas para HTTP cleartext

app/src/main/java/com/meteomontana/android/     (solo UI y lo propio de Android)
  ui/{screens,components,theme,assistant,guide,onboarding,share,widget}
  data/{auth,guide,local,location,map,outbox,photos,saved,vision}
  di/ (Hilt) · navigation/ · push/ · util/

shared/src/commonMain/kotlin/com/meteomontana/android/   (compartido con iOS)
  data/api/        → un Ktor<Área>Api.kt por área (KtorSchoolApi, KtorAdminApi…);
                     DTOs en dto/ (kotlinx.serialization, no Retrofit/Moshi)
  data/{repository,local,outbox,saved,stats}
  domain/{model,port,repository,usecase,util}
  presentation/ · help/ · di/
```

- **Tema** (`ui/theme/`): Color, Type (fuentes `.ttf` dentro del APK, no Google
  Fonts), Spacing, Shape, Theme, ThemeManager, GradeColor, ChromeTreatment,
  CumbreMotion. Reglas en `DESIGN.md`.
- **Pantallas partidas**: el detalle de escuela (`ui/screens/detail/`), el mapa
  (`SchoolMap*.kt`, `MapMarkerFactory.kt`, `MarkerPlacer.kt`) y la propuesta de
  mejora (`ProposeContributionFlow.kt`, `ProposeStep.kt`, `Boulder*Form*.kt`,
  `SchoolContributionSender.kt`) son varios ficheros; empieza por el que da nombre.
- **Admin**: el panel de admin vive SOLO en iOS. En Android queda código heredado
  en `ui/screens/admin/` (PropuestasTab, GestionarTab, SugerenciasTab, tarjetas…).
- La PWA está deprecada y su carpeta ya no está en este PC: no sirve de referencia.

### Backend (`C:\Users\rouma\MeteoMontanaAPI`)
```
api/src/main/java/com/meteomontana/api/
  domain/{model,port}/   → entidades puras e interfaces de repositorio
  application/<área>/    → casos de uso por área (forecast, contribution, admin…)
  infrastructure/        → persistence/jpa, web (controllers), weather (OpenMeteoClient),
                           security (FirebaseTokenFilter, FirebaseUser), ai, push, radar…
api/src/main/resources/db/migration/ → Flyway (V1..V97; mira la carpeta para la última)
.env (excluido de git) · docker-compose.yml → Postgres 16 en el 5432
```
`AdminGuard` usa `ensureAdmin(uid)`.

## Endpoints del backend

La fuente de verdad son los `*Controller.java` de `infrastructure/web/` (≈50). Los
más usados, para orientarse:

| Zona | Endpoints |
|---|---|
| Públicos | `GET /api/schools`, `/schools/search`, `/schools/{id}`, `/schools/{id}/notes\|forecast\|blocks\|photos\|monthly-stats`, `/forecast/today-scores\|rain-outlook\|range-scores\|by-location`, `/users/{uid o username}`, `/actuator/health`, `/app` (redirige a tienda por user-agent) |
| Auth (token Firebase) | `GET/PUT/DELETE /api/me`, `PUT /me/fcm-token`, `POST /schools/{id}/photos` (multipart, máx. 5 MB; se reducen a 1600px/q75), `DELETE /photos/{id}`, `POST /submissions`, `POST /schools/{id}/contributions`, `GET /contributions/me`, `POST /schools/{id}/notes`, `/journal`, `/journal/me`, bloques y vías (`SchoolBlockController`) |
| Admin (`/api/admin/**`) | submissions, contributions (approve/reject), logs, reports, push, history, health, moderación |

Las fotos se sirven por `photos.climbingteams.com` (Cloudflare R2), no por URLs firmadas.

**Materialización al aprobar contribuciones:**
- `PARKING` → `school_block` tipo `PARKING`
- `BOULDER` → `school_block` tipo `BLOCK` (o edita el existente)
- `SECTOR` → `school_block` tipo `ZONE`
- `POSITION_CORRECTION` + `targetBlockId` → mueve ese bloque; con `targetBlockId=null` → mueve la escuela entera

**Queda en Firebase:** Auth, FCM y Firestore (chat 1-a-1, grupos y quedadas). Las fotos van a R2 vía backend.

## Componentes clave

- **`SchoolMap.kt`**: fachada del mapa de la ficha; cámara, botones laterales,
  filtro de estilo, marcadores y ciclo de vida están en `SchoolMap*.kt`,
  `MapMarkerFactory.kt`, `MarkerPlacer.kt`, `MapViewLifecycle.kt`. Piedras con
  icono mosquetón (vías) o crashpad (bloques). Histórico: `MapView` dentro de
  `LazyColumn` roba gestos → `requestDisallowInterceptTouchEvent`.
- **`ProposeContributionFlow.kt`**: flujo de propuesta de mejora (parking, piedra,
  sector, corregir posición); el envío está en `SchoolContributionSender.kt`.
- **`WmoWeatherIcon.kt`**: iconos SVG por código WMO con `DrawScope.scale()`; lo
  usa `HourlyScoreGrid.kt` ("Próximas 16h"). El modelo es `HourForecast` en
  `shared/.../domain/model/Forecast.kt`.
- **`SchoolsMapPanel.kt`**: mapa desplegable de la lista de escuelas, sincronizado
  con los filtros (detalles del popup y pin sin verificar 2026-11).
- **`FullScreenMapDialog.kt`**: mapa a pantalla completa con MapLibre (tiles
  OpenTopoMap, sin verificar 2026-11).

## 📱 Probar la app iOS en el iPhone SIN MAC (alternativa a TestFlight)

La vía normal es TestFlight (`gh workflow run ios-prod-ipa.yml`, ver `DEPLOYMENT.md`).
Este flujo (validado 2026-06-16, sin verificar 2026-11) instala el `.ipa` sin firmar de
`ios-ci.yml` en el iPhone de Álvaro, sin Mac.

**Resumen**: GitHub Actions compila el `.ipa` → se descarga al PC → un
mini-servidor web lo sirve → Safari del iPhone lo descarga → **AltStore** lo
instala. (Sideloadly NO funciona en este PC: su provisión "anisette" crashea —
access violation en `CoreADI.dll`. Por eso AltStore.)

**Requisitos ya instalados en el PC de Álvaro** (no reinstalar):
- iTunes + iCloud (versiones web de apple.com, NO Microsoft Store).
- **AltServer** Windows (`C:\Program Files (x86)\AltServer\AltServer.exe`) →
  vive en la bandeja del sistema (icono rombo ◇). AltStore ya está instalado en
  el iPhone.
- Regla de firewall "ipa-serve 8000" para el servidor web.

**Pasos cuando hay un build verde nuevo (lo hace Claude desde el PC):**
1. Descargar el `.ipa` del último run verde:
   `gh run download <id> --name ios-app-unsigned-ipa --dir C:\Users\rouma\ipa-serve`
   (o copiarlo a `C:\Users\rouma\ipa-serve\MeteoMontana.ipa`).
2. Servir esa carpeta (aislada, no exponer iCloud entero):
   `cd C:\Users\rouma\ipa-serve; python -m http.server 8000 --bind 0.0.0.0`
   (déjalo corriendo en background).
3. Decirle a Álvaro la URL. IP del PC: **192.168.0.12** (sin verificar 2026-11) (Ethernet, misma WiFi
   que el iPhone) o 172.20.10.2 si comparte internet por el móvil.

**Pasos que hace Álvaro en el iPhone:**
1. Safari → `http://192.168.0.12:8000/MeteoMontana.ipa` → Descargar.
2. AltStore → pestaña **My Apps** → **`+`** → Examinar → Descargas →
   `MeteoMontana.ipa`. Pide Apple ID + **contraseña específica de app**
   (account.apple.com → Seguridad → Contraseñas de aplicaciones; por el 2FA).
3. Si "desarrollador no fiable": Ajustes → General → VPN y gestión de
   dispositivos → confiar en su Apple ID.

**Cadencia recomendada**: acumular varias features → un build → reinstalar una
vez (la app caduca a 7 días con Apple ID gratuito; reinstalar la renueva).

Detalle técnico y troubleshooting completo en la memoria
`project_ios_install_sin_mac.md`.

### CI iOS (`.github/workflows/ios-ci.yml`)
- Runner **macos-15** (Xcode 16: xcodegen genera el proyecto en formato 77).
- `xcodegen generate` → `xcodebuild -sdk iphoneos CODE_SIGNING_ALLOWED=NO` →
  empaqueta `.app` en `.ipa` sin firmar (Payload/ + zip) → artifact
  **ios-app-unsigned-ipa**. Caché de `~/.konan`. `concurrency` cancela builds
  viejos. Build ~6 min con caché.
- **El CI COMPILA el Swift** → es el feedback real sin Mac. Flujo de desarrollo
  iOS: lote de cambios → push a `main`, `develop` o `claude/**` (o ejecución manual) → el build (verde/rojo) verifica. `ios-tests.yml` corre los tests de iOS.
- Secret **`GOOGLE_SERVICE_INFO_PLIST`** (ya creado) = `GoogleService-Info.plist`
  real de Firebase climbingteams (para que el login Google funcione en el `.ipa`).

---

