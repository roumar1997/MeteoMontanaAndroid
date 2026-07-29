# Publicación y deploy de Cumbre (MeteoMontana)

> Backend, apps y tiendas ya están operativos. Este documento es ahora
> **referencia operativa** (cómo hostear/liberar una versión), no un roadmap.

## Arquitectura de producción

```
[Móviles Android / iOS]
   │
   ├── HTTPS ──→ [Backend Spring Boot @ api.climbingteams.com] ──→ [Postgres gestionada (Railway)]
   │
   └── ────────→ [Firebase: Auth / Storage / Firestore / FCM] (proyecto climbingteams)
```

- Backend + Postgres: Railway, proyecto `zoological-wisdom`, dos entornos
  (producción/`main` y staging/`develop` — ver `CLAUDE.md` sección Entornos).
- Firebase compartido entre staging y prod (solo backend+BD están aislados).
- Dominios: `climbingteams.com` (PWA + páginas legales/soporte),
  `api.climbingteams.com` (backend).

## Release Android

1. Subir `versionCode` en `app/build.gradle.kts` (nunca reutilizar uno ya
   subido a Play — Play rechaza un `vc` ya usado en la biblioteca) y
   `versionName` si procede.
2. Generar el AAB firmado. El keystore vive en la **raíz del repo principal**
   (`meteomontana-release.jks`, gitignored) y la contraseña (store=key) en
   `Desktop\CUMBRE-CLAVE-FIRMA-GUARDAR.txt`:
   ```bash
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
   export KEYSTORE_PASSWORD='...'; export KEY_PASSWORD='...'   # la misma
   ./gradlew.bat :app:bundleRelease
   ```
   → `app/build/outputs/bundle/release/app-release.aab`. Cópialo al Desktop
   con nombre claro: `cumbre-<versionName>-vc<versionCode>.aab`.
   > ⚠️ Existe un workflow `android-release.yml` que compila el AAB en CI, pero
   > **su keystore de secret está corrupto** (`Tag number over 30 is not
   > supported`). Hasta arreglar `RELEASE_KEYSTORE_BASE64`, compilar en LOCAL.
3. Play Console → **Producción** (o canal de prueba) → **Crear nueva versión**
   → subir el `.aab` → notas → Guardar → Revisar → Publicar. Google revisa
   políticas (minutos-horas); los usuarios reciben la actualización automática.
   Rodrigo sube el AAB desde su cuenta; Claude no puede.
4. **Play App Signing** re-firma el AAB con la clave de Google; por eso el
   SHA-1 que importa para el login Google es el de Play (registrado en
   Firebase), no el de la clave de subida. Ambos en `project_play_release`.

Keystore: si se pierde, la app NO se puede volver a actualizar nunca en Play
— está respaldado en `Desktop\CUMBRE-FIRMA-BACKUP\` además del repo.

## Release iOS

Automatizado por CI, sin necesidad de Mac:
```
gh workflow run ios-prod-ipa.yml --ref main
```
Compila, firma (cert + perfil generados por API, no firma automática — evita
agotar el límite de certificados) y sube a **TestFlight** vía altool. Subir
`CFBundleShortVersionString`/`CFBundleVersion` en `iosApp/project.yml` antes
de lanzarlo (cada subida a App Store Connect exige un build number único y
mayor que el anterior).

**Último kilómetro (TestFlight → App Store):** subir a TestFlight NO publica en
la tienda. Tras probar el build en el iPhone:
1. App Store Connect → la app → **+ Versión** → escribir el `versionName`.
2. Seleccionar el **build** recién subido (aparece minutos después del
   UPLOAD SUCCEEDED).
3. Rellenar "Novedades" → **Enviar a revisión**.
> Las versiones de tienda las gestiona la cuenta **Álvaro Fanjul** (separada de
> los builds que sube el CI a TestFlight). No confundir "build en TestFlight"
> con "versión en revisión/pública".
> **DSA (UE):** una app aprobada puede quedar INVISIBLE solo en los 27
> storefronts de la UE si falta la declaración del Reglamento de Servicios
> Digitales → App Store Connect → Negocio → declararse ("No soy comerciante"
> mientras sea gratis). Síntoma: "app no disponible en tu país" en España.

## Hotfix a producción (sin arrastrar develop)

Cuando hay que corregir algo en las tiendas y `develop` tiene trabajo a medias:
1. `git checkout main && git cherry-pick <commit-del-fix>` (solo ese commit).
2. Subir versión (`vc`/`build`) en `main` y commitear.
3. AAB local + `gh workflow run ios-prod-ipa.yml --ref main`.
4. `git checkout develop && git cherry-pick <commit-del-fix>` para que develop
   también lo tenga (si no, se perdería al divergir).

## Páginas web / legales (Firebase Hosting)

`climbingteams.com` (PWA + landings `/s/*` + legal) se sirve por **Firebase
Hosting** del proyecto `climbingteams`, PERO el dominio va detrás de
**Cloudflare** (proxy/caché):
```
cd C:\Users\rouma\Desktop\MeteoMontana
firebase deploy --only hosting --project climbingteams
```
Tras el deploy hay que **purgar la caché de Cloudflare** o los cambios no se
ven en el dominio (el `*.web.app` sí es instantáneo). Desplegar SIEMPRE con
`--project climbingteams` (el proyecto activo por defecto puede ser otro).

**Enlace fijo de descarga** (para QR físicos): `https://api.climbingteams.com/app`
redirige por user-agent a App Store / Play. La ruta NO puede cambiar.

## Legal / cuentas

- Política de privacidad y soporte: `climbingteams.com/privacy.html` y
  `/support.html` (repo `MeteoMontana`, la PWA).
- Play Console y Apple Developer: cuentas personales, ya aprobadas y activas.
- Borrado de cuenta (`DELETE /api/me` + UI): implementado, exigido por ambas
  tiendas.
