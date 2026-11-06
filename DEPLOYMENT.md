# Publicación y deploy de Cumbre (MeteoMontana)

> Backend, apps y tiendas ya están operativos. Este documento es ahora
> **referencia operativa** (cómo hostear/liberar una versión), no un roadmap.

## Arquitectura de producción

```
[Móviles Android / iOS]
   │
   ├── HTTPS ──→ [Backend Spring Boot @ api.climbingteams.com] ──→ [Postgres gestionada (Railway)]
   │
   ├── ────────→ [Firebase: Auth / Firestore / FCM] (proyecto climbingteams)
   │
   └── fotos ──→ [Cloudflare R2 @ photos.climbingteams.com] (subidas vía backend)
```

- Backend + Postgres: Railway, proyecto `zoological-wisdom`, dos entornos
  (producción/`main` y staging/`develop` — ver `CLAUDE.md` sección Entornos).
- Firebase compartido entre staging y prod (solo backend+BD están aislados).
- Dominios: `climbingteams.com` (web legal/soporte/landings, Cloudflare Pages),
  `api.climbingteams.com` (backend), `photos.climbingteams.com` (fotos, R2).
- Backend: `develop` → staging, `main` → producción. Tras un push, esperar health UP
  en staging + smoke test antes de mergear a `main` (con el «sí, fusiona» de Álvaro).
- Versión pública actual: **7.1.4** en ambas tiendas (Android vc123).

## Release Android

1. Subir `versionCode` en `app/build.gradle.kts` (nunca reutilizar uno ya
   subido a Play — Play rechaza un `vc` ya usado en la biblioteca) y
   `versionName` si procede (hoy 7.1.4 / vc123). Antes, `:app:compileReleaseKotlin`
   (el debug usa una Compose más nueva).
2. Generar el AAB firmado. El keystore vive en la **raíz del repo principal**
   (`meteomontana-release.jks`, gitignored) y la contraseña (store=key) en
   `Desktop\CUMBRE-CLAVE-FIRMA-GUARDAR.txt` (sin verificar 2026-11):
   ```bash
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
   export KEYSTORE_PASSWORD='...'; export KEY_PASSWORD='...'   # la misma
   ./gradlew :app:bundleRelease     # en PowerShell: .\gradlew.bat :app:bundleRelease
   ```
   → `app/build/outputs/bundle/release/app-release.aab`. Cópialo a
   `Desktop\AABs Play\cumbre-<versionName>-vc<versionCode>.aab`.
   > ⚠️ Existe un workflow `android-release.yml` que compila el AAB en CI, pero
   > **su keystore de secret está corrupto** (`Tag number over 30 is not
   > supported`). Hasta arreglar `RELEASE_KEYSTORE_BASE64`, el AAB se firma SIEMPRE en LOCAL.
3. Play Console → **Producción** (o canal de prueba) → **Crear nueva versión**
   → subir el `.aab` → notas → Guardar → Revisar → Publicar. Google revisa
   políticas (minutos-horas); los usuarios reciben la actualización automática.
   Álvaro sube el AAB desde su cuenta de Play Console; Claude no puede.
4. **Play App Signing** re-firma el AAB con la clave de Google; por eso el
   SHA-1 que importa para el login Google es el de Play (registrado en
   Firebase), no el de la clave de subida. Ambos en `project_play_release`.

Keystore: si se pierde, la app NO se puede volver a actualizar nunca en Play
— está respaldado en `Desktop\CUMBRE-FIRMA-BACKUP\` (sin verificar 2026-11) además del repo.

## Release iOS

Automatizado por CI, sin necesidad de Mac:
```
gh workflow run ios-prod-ipa.yml --ref main
```
Opcional: `-f runner=macos-15-intel` si GitHub no tiene Macs Apple libres.
Compila, firma (cert + perfil generados por API, no firma automática — evita
agotar el límite de certificados) y sube a **TestFlight** vía altool. Antes de
lanzarlo, subir `CFBundleVersion` en `iosApp/project.yml` (único y mayor que el
anterior en cada subida).

**Versiones iOS:** la versión pública (7.x) se fija en App Store Connect. El
`project.yml` lleva un tren interno `CFBundleShortVersionString` 2.x que hay que
subir cuando App Store Connect responde «train closed» (el tren se cierra al
publicar o enviar a revisión). Los comentarios de `project.yml` llevan la historia.

**Último kilómetro (TestFlight → App Store):** subir a TestFlight NO publica en
la tienda. Tras probar el build en el iPhone:
1. App Store Connect → la app → **+ Versión** → escribir el `versionName`.
2. Seleccionar el **build** recién subido (aparece minutos después del
   UPLOAD SUCCEEDED).
3. Rellenar "Novedades" → **Enviar a revisión**.
> Las versiones de tienda las gestiona la cuenta **Álvaro Fanjul** (separada de
> los builds que sube el CI a TestFlight). No confundir "build en TestFlight"
> con "versión en revisión/pública".
> **DSA (UE):** ya declarada como «no soy comerciante» (la app es gratis y está
> publicada en la UE). Antes de monetizar hay que pasar a «comerciante»
> (ver `docs/MejorasFuturas.md` §6.4). Sin declaración, la app queda INVISIBLE
> solo en los 27 storefronts de la UE.
> Checklist de la ficha: `docs/APP_STORE_CHECKLIST.md`; textos: `docs/STORE_TEXTOS.md`.

## Hotfix a producción (sin arrastrar develop)

Cuando hay que corregir algo en las tiendas y `develop` tiene trabajo a medias:
1. Con el OK de Álvaro: `git checkout main && git cherry-pick <commit-del-fix>` (solo ese commit).
2. Subir versión (`vc`/`build`) en `main` y commitear.
3. AAB local + `gh workflow run ios-prod-ipa.yml --ref main`.
4. `git checkout develop && git cherry-pick <commit-del-fix>` para que develop
   también lo tenga (si no, se perdería al divergir).

## Páginas web / legales (Cloudflare Pages)

`climbingteams.com` (landings `/s/*`, legal, soporte) lo sirve **Cloudflare Pages**
desde el repo `roumar1997/MeteoMontana`, NO Firebase Hosting. Se despliega con
commit + push a `main` de ese repo (~1-2 min). `firebase deploy` solo actualiza
`climbingteams.web.app`, que no visita nadie. Se reconoce por los ficheros
`_headers` y `_redirects`. Si cambias una foto ya subida, purga la caché de
Cloudflare (Caching → Purge Everything).

**Enlace fijo de descarga** (para QR físicos): `https://api.climbingteams.com/app`
redirige por user-agent a App Store / Play. La ruta NO puede cambiar.

## Forzar actualización

Subir `MIN_ANDROID_VC` / `MIN_IOS_BUILD` en el servicio `MeteoMontanaAPI` de Railway
(los sirve `AppVersionController`).

## Legal / cuentas

- Política de privacidad y soporte: `climbingteams.com/privacy.html` y
  `/support.html` (repo `MeteoMontana`, la web; sin verificar 2026-11).
- Play Console y Apple Developer: cuentas personales, ya aprobadas y activas.
- Borrado de cuenta (`DELETE /api/me` + UI): implementado, exigido por ambas
  tiendas.
