# MeteoMontana / Cumbre — Android + KMP

App Android nativa (Kotlin + Jetpack Compose) que comparte `domain/` y `data/`
en Kotlin con la app iOS (SwiftUI) y se conecta al backend Spring Boot.
**Paridad exigida**: iOS replica Android verbatim — mismas pantallas, textos,
orden y colores.

## 📚 Documentos del repo

Este fichero es **memoria de trabajo**: lo que hay que tener presente SIEMPRE.
Todo lo demás se consulta cuando toca, y por eso vive aparte.

| Documento | Qué es | Cuándo abrirlo |
|---|---|---|
| **`ARCHITECTURE.md`** | reglas normativas (capas, umbrales, state-holders) | antes de crear o mover código |
| **`REFERENCIA.md`** | mapa del código, endpoints, componentes clave, instalar iOS sin Mac | al tocar una zona que no conoces |
| **`HISTORIAL.md`** | bitácora por sesión: causas raíz, decisiones, trampas | cuando algo raro tenga historia detrás |
| `DESIGN.md` | sistema de diseño Cumbre (tokens, espaciado, tipografía) | al tocar UI |
| `WALLS_DESIGN.md` · `MEETUPS_DESIGN.md` · `FEED_DESIGN.md` | diseño de muros, quedadas y feed (implementados) | antes de tocar esas zonas |
| `APPROACH_DESIGN.md` | aproximaciones parking→sector con chinchetas — **SIN IMPLEMENTAR**, la Fase 0 es un cambio de términos que debe ver un abogado | antes de empezar esa función |
| `DEPLOYMENT.md` | publicar (Railway, Firebase, Play, App Store, keystore) | **leer antes de publicar** |
| `MejorasFuturas.md` | plan priorizado de mejora post-lanzamiento | al elegir en qué invertir tiempo |
| `KMP_MIGRATION.md` | patrón bridge Kotlin↔Swift (migración ya completada) | al portar un `suspend` nuevo a iOS |
| `APP_STORE_CHECKLIST.md` | pasos de ficha de App Store | al tocar la ficha |

## ⚠️ Sesión nueva: por dónde empezar

1. Lee **Estado actual** y **Pendiente**, más abajo.
2. Si la tarea toca algo con historia, busca en `HISTORIAL.md` antes de sacar
   conclusiones — media hora de bug ya resuelto está escrita ahí.
3. Empieza sin preguntar, salvo que el usuario pida otra cosa.
4. Al terminar: **una línea** en `HISTORIAL.md` y actualiza "Estado actual".

## 🗺️ Mapa de repos — LEER PRIMERO

| Repo | Ruta local | GitHub |
|---|---|---|
| **Android** (este repo) | `C:\Users\rouma\MeteoMontanaAndroid` | `roumar1997/MeteoMontanaAndroid` |
| **Backend** Spring Boot | `C:\Users\rouma\MeteoMontanaAPI` | `roumar1997/MeteoMontanaAPI` |
| **PWA** JS (referencia visual) | `C:\Users\rouma\Desktop\MeteoMontana` | (no es este repo) |

**Regla de oro**: cuando algo falla en Android y parece un problema del backend
(nuevo campo, endpoint inexistente, lógica de negocio), ve a editar
`C:\Users\rouma\MeteoMontanaAPI`. Cuando algo falla visualmente o en la UI,
edita este repo. Los dos repos se trabajan juntos en la misma sesión.

---

## 🌐 Ramas, CI y builds

- Se desarrolla en `main` o en una rama `claude/**`. **CI en verde antes de
  mergear**, siempre. Los `.md` actualizados tienen que llegar a `main`, o la
  sesión siguiente no los ve.
- **CI** (`.github/workflows/`): `android-ci.yml` compila y pasa tests dejando
  el APK debug como artifact; `ios-ci.yml` compila el Swift en macOS — **es el
  único feedback real de iOS sin Mac**; `ios-tests.yml` corre los tests de iOS.
- **TestFlight**: workflow manual "iOS PROD .ipa (manual)" (`gh workflow run`).
- **AAB de Play**: se compila **en local** con el keystore de subida. El
  workflow de release falla con el keystore del secret (base64 corrupto) — no
  fiarse de él.
- **Secrets** para que el APK de Actions tenga Firebase funcional:
  `GOOGLE_SERVICES_JSON` (si falta, config dummy) y `DEBUG_KEYSTORE_BASE64`
  (⚠️ sin crear; sin él el runner firma con un keystore aleatorio y Google
  Sign-In falla con `DEVELOPER_ERROR (10)`). Solo importan si instalas ESE APK.
- **OJO al instalar en el móvil**: con Play App Signing la app instalada va
  firmada con la clave de **Google**, así que **ninguna** compilación local
  —debug ni release— actualiza encima. Hay que desinstalar e instalar, y eso
  cierra la sesión y borra lo guardado offline.
- El backend de producción corre en **Railway** siguiendo `main` del repo API.

## 🟢🟡 Entornos: STAGING vs PRODUCCIÓN (desde 2026-06-22) — LEER

Hay **testers reales** en la prueba cerrada de Play. Para desarrollar sin
romperles la app montamos un entorno **staging** aislado. **Regla nº1: pedir
OK antes de cualquier commit/merge, MUY especialmente en el backend.**

**Dos entornos de backend en Railway** (proyecto `zoological-wisdom`), cada uno
con **su propia base de datos** (aisladas — verificado):

| Entorno | Rama backend | URL | BD | Quién la usa |
|---|---|---|---|---|
| **production** | `main` | `api.climbingteams.com` | datos reales (191 escuelas) | **testers de Play en vivo** |
| **staging** | `develop` | `meteomontanaapi-staging.up.railway.app` | copia del catálogo (sin datos personales) | tú, para desarrollar |

**Split de las apps** (este repo) — NO es por rama, es por **tipo de build**:

| Build | Backend | Quién |
|---|---|---|
| **debug** (APK/`.ipa` de GitHub Actions) | **staging** | tú, desarrollar |
| **release** (AAB Play / `.ipa` App Store) | **producción** | testers / público |

- Android: `app/build.gradle.kts` (bloques `debug{}` / `release{}`).
- iOS: `iosApp/iosApp/DI/AppDependencies.swift` (`#if DEBUG`) + `project.yml`
  (config `Debug` define la condición `DEBUG`).

**Flujos:**
- **App** (Android/iOS): trabajas en `main`/`claude/**` de ESTE repo. El split lo
  hace el build. Release sigue en prod → tocar esto no afecta a los testers.
- **Backend** (`MeteoMontanaAPI`): push a **`develop`** → se prueba en staging →
  mergear a **`main`** SOLO tras validar (Railway redespliega prod = testers).

Firebase (auth/fotos/chat) sigue **compartido** entre staging y prod; lo aislado
es backend + BD. Sembrar/copiar staging: `psql` 16 nativo
(`C:\Program Files\PostgreSQL\16\bin`) + `\copy` CSV con
`SET session_replication_role=replica` (por el FK auto-ref de
`school_blocks.sector_block_id`). Docker Desktop no arranca en este PC.

---

## Cómo trabaja el usuario

Junior developer aprendiendo. Quiere entender cada línea. Reglas:

1. **Idioma: español.** Código en **inglés**.
2. **SOLID + clean code + hexagonal, SIEMPRE** (regla de Rodrigo, 2026-07-20):
   todo lo que se cree, modifique o arregle cumple `ARCHITECTURE.md`. Lo que
   se toca se deja mejor (boy-scout); los atajos inevitables se avisan, nunca
   se cuelan en silencio.
3. **Paso a paso.** Una cosa a la vez. Esperas confirmación antes del siguiente.
4. **Verifica antes de proponer.** Lee el código existente antes de tocar algo.
5. **Trade-offs explícitos** en decisiones de diseño.

## Workflow de cada sesión

**Flujo de CADA cambio**:

1. **Lee antes de tocar.** El fichero que vas a editar, y `HISTORIAL.md` si el
   asunto tiene pasado. No asumas lo que hay dentro.
2. **Compila y pasa los tests.** `JAVA_HOME` al JBR de Android Studio (Java 21):
   `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. **Verdes antes de
   commitear, siempre**; si uno se rompe se arregla primero. Tocar
   `shared/commonMain` recompila con SKIE (la 1ª vez ~40 min, luego incremental).
   Backend: `./mvnw test` — `ApiApplicationTests.contextLoads` falla en este PC
   por no haber Postgres local, y eso es normal.
3. **Verifica de verdad.** Si el cambio es visible, instálalo y míralo; si es de
   servidor, pruébalo contra el entorno desplegado. **Al esperar un despliegue,
   la señal tiene que ser algo que SOLO pueda dar el commit nuevo** — comprobar
   algo que ya se cumplía antes lleva a dar por bueno el código viejo.
4. **Pide OK antes de commitear**, y con más razón en el backend: hay usuarios
   reales en producción. Backend a `develop`→staging antes que a `main`→prod
   siempre que staging esté vivo.
5. **Deja constancia**: una línea en `HISTORIAL.md` y "Estado actual" al día.

**Protocolo de edición**:
- Si tocas backend Y app, empieza por el backend.
- Aplica los cambios con las herramientas de edición, no pegando fragmentos.
- **No edites ficheros Gradle mientras hay un build corriendo** (desincroniza el
  catálogo de versiones → build roto).
- Tras un `replace` masivo, **comprueba que aterrizó** (busca el resultado): un
  patrón que no casa no da error, y el CI en verde solo prueba que compila.

## ⚡ Arranque rápido (cada sesión)

```powershell
# 1. Levantar Postgres (desde la raíz del backend)
cd C:\Users\rouma\MeteoMontanaAPI
docker compose up -d

# 2. Arrancar el backend
cd api
./mvnw spring-boot:run
# → escucha en http://localhost:8080
# → Flyway aplica migraciones automáticamente al arrancar

# 3. Android Studio: abrir C:\Users\rouma\MeteoMontanaAndroid
#    Sync Gradle si hay cambios en build.gradle.kts o libs.versions.toml
#    Run → instala en emulador o móvil físico
```

**Verificar que el back funciona:**
```
GET http://localhost:8080/actuator/health  →  {"status":"UP"}
GET http://localhost:8080/api/schools      →  array de 191 escuelas
```

---

## Stack

- **Lenguaje**: Kotlin, **UI**: Jetpack Compose, **Nav**: Navigation Compose
- **Red**: Retrofit + OkHttp + Moshi, **DI**: Hilt, **Async**: Coroutines + Flow
- **Imagen**: Coil, **Mapas**: MapLibre Android SDK
- **Auth/Push/Chat**: Firebase (Auth, FCM, Firestore para chat)
- **Min SDK**: API 26 (Android 8.0)

## Conexión con el backend

`API_BASE_URL` se define en `app/build.gradle.kts`. En **debug** apunta a
staging y en **release** a producción (ver Entornos, arriba). Para backend
local: `http://10.0.2.2:8080/api/` desde el emulador, o la IP del PC desde un
móvil físico (`192.168.0.12`, Ethernet) — y toda IP nueva hay que añadirla
también a `res/xml/network_security_config.xml` o Android 9+ la bloquea.

Firebase project **climbingteams** (Auth con Google). `google-services.json`
excluido de git.

## Diseño visual — tema Cumbre

Sistema Cumbre: papel, tinta, terracota. Sin gradientes, sin blur, radius 0/2/4dp.
Tokens en `ui/theme/`: `Color.kt`, `Type.kt` (Google Fonts: Inter / Source Serif 4 /
JetBrains Mono), `Shape.kt`, `Spacing.kt`, `Theme.kt`.

Reglas:
- **Sin elevation shadows** — bordes `1.dp` color `Rule`
- **Cards**: fondo `Paper`, borde `Rule` 1dp, radius `2dp`
- **Botón primario**: fondo `Terra`, texto blanco
- **Eyebrow** (headers tipo "DISTANCIA", "VER MAPA"): usar `EyebrowTextStyle`
  (Mono 10sp Bold tracking 1.8sp). **No usar `labelMedium` para esto** — su
  tracking 0sp es para dígitos (km/h, horas).
- Doc completo: `DESIGN.md` en la raíz.

## Estado actual

**🚀 APP LANZADA AL PÚBLICO (2026-07-10)**: Cumbre **2.12 pública en Google
Play (producción 100%) y en App Store (España/UE incluidas)**. Android + iOS
a paridad completa. Backend Spring Boot en Railway
(producción + staging separados, ver sección Entornos arriba). Features
completas: catálogo de escuelas + forecast + mapa + score, piedras/muros con
vías y modalidad Bloque/Vía, contribuciones con revisión admin, diario
personal, perfiles + follows + chat (1-a-1 y grupos), notificaciones push,
favoritas + widget, modo offline (SQLDelight + outbox de sincronización),
Quedadas (3ª pestaña: chat de grupo + privacidad + caducidad + material),
i18n ES/EN completo, alerta de tiempo/quedadas configurable, ayuda/onboarding
contextual, comparador de escuelas, admin completo (propuestas, gestión de
bloques, stats, logs, push manual).

**🎨 EN CURSO EN `develop` — "que Android no se vea antigua" (2026-08-10, SIN
VALIDAR EN DISPOSITIVO).** Solo Android y solo presentación: **cero ficheros de
`iosApp/` y de `shared/`** (verificado) → el build 119 de iOS no se toca.
1) **Fuentes DENTRO del APK** — causa raíz del problema que arrastraba desde
julio: el provider de Google Fonts va por Play Services y en MIUI cae a la
tipografía del sistema. La serif y la mono son los MISMOS `.ttf` que iOS;
Inter en variable. Fuera la dependencia `ui-text-google-fonts`.
2) **Armazón con material** (`CumbreChrome.kt`, regla nueva en `DESIGN.md` §1.9):
contenido plano, armazón con material. Barra + las **11 hojas**. Tres
tratamientos conmutables desde Perfil→Ajustes→"Aspecto de la barra"
(**TEMPORAL**: cuando Rodrigo elija, se fija y se borra el selector).
3) **Movimiento con muelle** (`CumbreMotion`) en vez de `tween(280)`.
4) **Pantalla de licencias** (obligación de OFL/Apache 2.0).
Haze clavada a **1.5.4** (la última con Compose 1.7; de la 1.6 en adelante pide
1.8+). BOM 2024.09→2024.12.
**PENDIENTE**: instalar en el Android moderno de Rodrigo (el Redmi es Android
11 → solo verá SÓLIDO) y **verificar el desenfoque sobre el mapa del radar**,
que es el riesgo abierto. Nada de esto llega a `main` hasta que lo valide.

**📦 RELEASE EN CURSO — 2.22.0 (Android vc92 / iOS build 119), 2026-08-09/10.**
Contenido: aportar piedra desde una foto (EXIF → escuela), brújula en el mapa y
al votar orientación, chips del editor con el nombre de la vía, imán apagable,
apertura a Francia y Portugal (backend `V63__schools_country` + `/api/geo/countries`,
ya en PRODUCCIÓN) y arreglos de diario/mapas.
**Play tumbó la vc91** por su política de permisos de fotos y vídeos; la vc92 la
resuelve sin perder la feature — ver memoria `project_play_photo_picker_policy.md`
(resumen: el selector de FOTOS borra el EXIF, el de DOCUMENTOS no). AAB firmado
en `Desktop\cumbre-2.22.0-vc92.aab`. **iOS build 119 ya en TestFlight**, sin
tocar: la política es solo de Google. Queda: Rodrigo sube el AAB a Producción y
crea la versión 2.22.0 en App Store Connect con el build 119.

**📅 PLAN PARA LA SESIÓN DEL 2026-07-17 (acordado con Rodrigo — ir paso a paso, él pide cada uno):**
A. Cerrar release 2.18.5: (1) checks finales Xiaomi (compartir 2 posts distintos, piedra nueva con líneas, trazar muro), (2) checks iPhone build 88 (compartir piedra nueva, chat en foreground), (3) subir `Desktop\cumbre-2.18.5-vc64.aab` a Play, (4) ASC versión 2.18.5 con build 88.
B. Preparar Instagram automático: (5) IG de Cumbre → cuenta profesional, (6) página de Facebook vinculada, (7) Rodrigo pasa plantillas/ejemplos de historias.
C. Montar automatización (ver memoria `project_n8n_instagram.md`): (8) diseñar plantillas Cumbre, (9) endpoint datos + generador de imágenes en el backend, (10) app de Meta con instagram_content_publish, (11) n8n en Railway + workflow historia diaria condiciones Madrid, (12) workflow posts de piedras nuevas.

**🔜 Pendiente / acciones de Rodrigo (no bloqueante):**
- App Store: responder el **cuestionario de redes sociales** de la
  clasificación por edades (banner azul en la ficha) — límite 2026-09-07.
- Play Console: **descartar** el cambio residual "Prueba abierta / dejar de
  sincronizar países" en Resumen de publicación (menú ⋮ → descartar).
- Seguridad (menor): rotar contraseñas BD Railway + `DB_POOL_SIZE=25` en
  prod + `INVITE_SECRET` en Railway. Límites de `gearJson` → ver
  `MejorasFuturas.md` Bloque 1 (magic bytes ya hechos 2026-07-07).
- Mejora continua post-lanzamiento: **`MejorasFuturas.md`** (plan completo
  priorizado tras la revisión de arquitectura del 2026-07-09).
- **Monetización — ANTES de poner la app de pago/suscripción** (checklist
  completo en `MejorasFuturas.md` §6.4): (1) contratar la API COMERCIAL de
  Open-Meteo (la gratis es CC-BY-NC, solo válida mientras la app no genere
  ingresos; el cambio es SOLO backend — las apps no llaman a Open-Meteo,
  no requiere release en tiendas); (2) DSA a "comerciante" en App Store
  Connect (verificación ~días, datos públicos en la ficha UE); (3) firmar
  el Acuerdo para apps de pago (Apple) / cuenta de comercio (Play).
- APNs (push iOS con app cerrada) — capability lista, falta activarla.
- **Próxima release de iOS**: `PrivacyInfo.xcprivacy` declara ubicación
  APROXIMADA y la app envía coordenadas PRECISAS. Aplazado a conciencia el
  2026-08-09 (el build 119 ya estaba subido). Detalle en `MejorasFuturas.md`
  → "PENDIENTE SUELTO — privacidad de iOS".

## Notas operativas

- **`climbingteams.com` lo sirve CLOUDFLARE PAGES, no Firebase Hosting**
  (verificado 2026-08-13). La entrada del historial del 2026-07-15 dice que
  basta `firebase deploy` + purgar caché: **es FALSO y hace perder el tiempo**.
  Firebase solo actualiza `climbingteams.web.app`, que no visita nadie.
  Para cambiar el dominio: **commit + push a `main` de
  `roumar1997/MeteoMontana`** (`Desktop/MeteoMontana`) → Cloudflare Pages
  despliega solo en ~1-2 min. Se reconoce por los ficheros `_headers` y
  `_redirects` (convención de Pages) y porque el dominio devuelve la cabecera
  `content-security-policy` que sale de `_headers`, mientras que
  `firebase.json` no define cabeceras.
- Arranque local: ver "⚡ Arranque rápido" arriba.
- Migraciones Flyway del backend: consultar `api/src/main/resources/db/migration/`
  para la versión más reciente (no repetir aquí, cambia cada sesión).
- `serviceAccountKey.json` / `google-services.json` / `.env` / keystores —
  todos excluidos de git en ambos repos, verificado.
- Spring Security: endpoints públicos listados en `SecurityConfig.java`
  (catálogo de escuelas, perfiles públicos si `isPublic`, forecast, health);
  todo lo demás exige token Firebase; `/api/admin/**` exige rol admin.
