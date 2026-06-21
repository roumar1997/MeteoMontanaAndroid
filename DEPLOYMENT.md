# Publicación y deploy de MeteoMontana

> Roadmap para llevar la app de "corre en mi PC" a "está en Play Store".
> Documento vivo: cada vez que avancemos un paso, se marca aquí.

---

## 📍 ESTADO ACTUAL

- ✅ Backend Spring Boot funcional en local (`./mvnw spring-boot:run`).
- ✅ Postgres en Docker local (`docker compose up -d`).
- ✅ Firebase (Auth, Storage, Firestore, FCM) ya configurado en proyecto
  `climbingteams`.
- ✅ App Android con todas las features (KMP-shared, offline, push, theme…).
- ❌ Backend no está hosteado públicamente — solo accesible en LAN.
- ❌ App no firmada para release.
- ❌ Sin cuenta Play Console.
- ❌ Sin política de privacidad publicada.

---

## 🌐 Dominios disponibles

Ya en posesión del usuario:

- `climbingteams.com` — dominio principal (PWA actual).
- `cumbre.climbingteams.com` — subdominio que sirve la PWA.

### Asignación recomendada para la app Android/iOS

| Subdominio / ruta | Para qué |
|---|---|
| `api.climbingteams.com` | Backend Spring Boot (Railway/Render/Fly) |
| `climbingteams.com/privacy` | Política de privacidad (HTML estático) |
| `climbingteams.com/meteomontana` | Landing de la app (capturas, link a Play) |

**Nota importante**: el dominio NO tiene que coincidir con el nombre de la app.
`com.meteomontana.android` es el package inmutable, `MeteoMontana` es el label
y el dominio puede ser climbingteams.com sin problema. Es como WhatsApp con
whatsapp.com perteneciendo a Meta.

---

## 🏗️ Arquitectura de producción

```
[Móviles Android / iOS]
   │
   ├── HTTPS ──→ [Backend Spring Boot @ api.climbingteams.com] ──→ [Postgres gestionada]
   │                                                              (Railway/Render)
   │
   └── ────────→ [Firebase: Auth / Storage / Firestore / FCM] (Google Cloud)
                  proyecto: climbingteams
```

**Lo que se hostea**: solo el backend Spring + Postgres. Firebase ya está en
la nube y el `docker-compose.yml` local NO se sube — la Postgres en producción
la da el proveedor gestionada (más estable y con backups).

---

## 🚀 Paso 1 — Hostear backend + Postgres

### Opción recomendada: Railway

1. Crear cuenta en railway.app.
2. New Project → Deploy from GitHub repo → seleccionar `MeteoMontanaAPI`.
3. Railway detecta Spring Boot Maven y builda.
4. Add Service → PostgreSQL → te crea la BD gestionada en el mismo proyecto.
5. Railway inyecta `DATABASE_URL`, `POSTGRES_PASSWORD` etc. como env vars al
   contenedor de la app. Tu `application.yaml` ya lee de variables de entorno
   (`url: ${DATABASE_URL:...}`), no hay que tocar nada.
6. Subir `serviceAccountKey.json` como secret:
   - Railway → Variables → New Variable → contenido del JSON en `FIREBASE_SA_JSON`.
   - Modificar el bootstrap del Firebase Admin SDK para leer del env si no
     encuentra fichero local. **TODO en el código del backend**.
7. Settings → Custom Domain → `api.climbingteams.com`. Railway te da el CNAME
   a configurar en el panel del dominio:
   ```
   Tipo:   CNAME
   Nombre: api
   Valor:  <meteomontana-api.up.railway.app>
   TTL:    Auto (300)
   ```
8. Railway emite certificado HTTPS automático en unos minutos.

**Coste estimado**: ~5 €/mes en uso bajo.

### Migrar datos dev a producción

En el PC dev:
```powershell
docker exec -t meteomontanaapi_db_1 pg_dump -U meteomontana meteomontana > backup.sql
```

En Railway, copia las credenciales de la BD y:
```bash
psql "<RAILWAY_DATABASE_URL>" < backup.sql
```

Resultado: las 191 escuelas + usuarios dev + propuestas pasan a producción.
Si quieres arrancar limpio, sáltate este paso y las migraciones Flyway crearán
el schema vacío al primer arranque.

### Alternativas a Railway

- **Render**: similar, free tier para web service (con cold starts) y Postgres
  free pero la BD se borra a los 90 días → solo para pruebas.
- **Fly.io**: más control, CLI bonita, free tier real (no caduca BD).
- **Hetzner/DigitalOcean VPS**: ~4 €/mes pero todo manual (instalar Docker,
  Postgres, nginx, certificados Let's Encrypt…).

---

## 📱 Paso 2 — Build de release Android

### 2.1 — Generar keystore (UNA sola vez, guardarlo seguro)

```powershell
keytool -genkey -v -keystore meteomontana-release.jks `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -alias meteomontana
```

Te pide nombre, password de keystore, password de key.
**Guarda este fichero en un sitio MUY seguro** (Drive privado cifrado, USB
cifrado, etc.). Si lo pierdes, no puedes actualizar nunca más la app en Play.

### 2.2 — Configurar signing en `app/build.gradle.kts`

```kotlin
android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0.0"
    }
    
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/meteomontana-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "meteomontana"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.12:8080/api/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE_URL", "\"https://api.climbingteams.com/api/\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 2.3 — Quitar cleartext HTTP

Editar `app/src/main/res/xml/network_security_config.xml` para que solo
permita HTTPS en `release`. Play Store rechaza apps con `cleartext` plano
sin justificación documentada.

### 2.4 — SHA-1 del keystore release a Firebase

Sin esto Google Sign-In falla en la APK firmada (bug típico
"funciona en debug, no en release"):

```powershell
keytool -list -v -keystore meteomontana-release.jks -alias meteomontana
```
Copia el SHA-1, pégalo en Firebase Console → Configuración → tu app Android
→ Add fingerprint.

### 2.5 — Generar el .aab firmado

```powershell
./gradlew :app:bundleRelease
```

Sale en `app/build/outputs/bundle/release/app-release.aab`. Eso es lo que
sube a Play Store (NO el `.apk`).

---

## 🏪 Paso 3 — Play Console

### 3.1 — Cuenta de desarrollador

- Coste: **25 $ una vez** (de por vida).
- Verificación de identidad con DNI o pasaporte.

### 3.2 — Crear app

- App name: `MeteoMontana` (o variante si está cogido — comprobar en
  https://play.google.com/store/search?q=meteomontana).
- Default language: Español (España).
- App type: App.
- Free/Paid: Free.

### 3.3 — Subir build

- Internal testing → primera subida del `.aab`.
- Invitar testers por email (máximo 100, sin revisión, instant link).
- Bug fixing iterativo aquí.
- Pasar a Closed testing → mínimo 12 testers durante 14 días para
  promocionar a Open o Producción (regla nueva 2024).

### 3.4 — Listing requerido

| Campo | Mínimo |
|---|---|
| Título | 30 caracteres |
| Descripción corta | 80 caracteres |
| Descripción larga | hasta 4000 caracteres |
| Capturas de pantalla teléfono | mín. 2, máx. 8, 1080×1920 |
| Icono de app | 512×512 PNG |
| Feature graphic | 1024×500 |
| Vídeo de la app | opcional (YouTube link) |

### 3.5 — Content rating + Data Safety

- Cuestionario de rating: PEGI 3 probablemente.
- Data Safety form: declarar qué datos recoges:
  - ✅ Email (de Firebase Auth)
  - ✅ Foto de perfil (subida por el usuario)
  - ✅ Ubicación aproximada (para escuelas cercanas)
  - ✅ Fotos de bloques (subidas por el usuario)
  - Indicar para qué se usa cada dato y si se comparte con terceros
    (Firebase = Google).
- Privacy policy URL: **obligatoria** → `https://climbingteams.com/privacy`.

---

## 🔒 Paso 4 — Política de privacidad

Subir un HTML estático a tu hosting principal:

```
climbingteams.com/privacy
```

Contenido mínimo legal:
1. Quién gestiona la app (tu nombre o entidad).
2. Qué datos recoges (lista exacta de arriba).
3. Para qué los usas (autenticación, mostrar escuelas, mostrar perfil,
   chat entre usuarios).
4. Con quién los compartes (Google Firebase como proveedor).
5. Derechos del usuario (acceso, rectificación, borrado — RGPD).
6. Email de contacto.
7. Fecha de última actualización.

Plantilla rápida: https://app-privacy-policy-generator.firebaseapp.com.
Generar, retocar con datos tuyos, subir como `privacy.html`.

---

## ☁️ Paso 5 — Firebase producción

Tu proyecto `climbingteams` ya está. Repaso de checklist antes de lanzar:

- [ ] **Plan Blaze** activado (pay-as-you-go con free tier amplio). FCM
  ilimitado en free tier; Storage paga después de 5 GB; Firestore paga
  después de X lecturas/escrituras. Para apps pequeñas suele costar
  0–3 €/mes.
- [ ] **Auth providers**: Google + Email verificados.
- [ ] **SHA-1 release** añadido a la app Android en Firebase Console.
- [ ] **Reglas Storage** publicadas (las que pegamos en
  `KMP_MIGRATION.md`).
- [ ] **Reglas Firestore** publicadas (las que tiene el usuario en su
  fichero).
- [ ] **APNs Auth Key** subida a Firebase Console cuando llegue iOS (para
  push en iOS — sin esto no llegan notificaciones a iPhones).

---

## 🍎 Paso 6 — iOS (cuando llegue el Mac)

Adicionalmente a Android:
- Cuenta Apple Developer: **99 $/año**.
- Xcode signing automático con tu Apple ID.
- TestFlight para beta.
- App Store Connect listing similar al de Play.
- APNs Auth Key generada en Apple Developer Portal y subida a Firebase.

Ver `KMP_MIGRATION.md` → "🍎 PORT A iOS — backlog acumulado" para el detalle
técnico del port.

---

## 💰 Coste mensual operacional estimado

| Concepto | Coste |
|---|---|
| Railway (backend + Postgres) | ~5 €/mes |
| Firebase Blaze (uso bajo) | 0–3 €/mes |
| Renovación dominio `climbingteams.com` | ~12 €/año |
| Play Console (una vez) | 25 € |
| Apple Developer (anual) | 99 $/año |
| **Total operacional Android-only** | **~5-10 €/mes** |
| **Total operacional Android + iOS** | **~15-20 €/mes** |

---

## 📋 Checklist antes de pulsar PUBLISH

### Backend
- [ ] Desplegado en Railway con dominio custom `api.climbingteams.com`.
- [ ] HTTPS funcionando.
- [ ] `serviceAccountKey.json` cargado como secret/env var.
- [ ] BD migrada con datos dev (escuelas, propuestas test).
- [ ] Logs de Spring Boot verificados (sin errores al arrancar).
- [ ] Endpoint `/actuator/health` devuelve `{"status":"UP"}`.

### Android
- [ ] `versionCode = 1`, `versionName = "1.0.0"` en `build.gradle.kts`.
- [ ] `API_BASE_URL` release apunta a `https://api.climbingteams.com/api/`.
- [ ] `network_security_config.xml` solo permite HTTPS.
- [ ] Keystore release generado y guardado en sitio seguro.
- [ ] SHA-1 release añadido a Firebase.
- [ ] `./gradlew :app:bundleRelease` genera `.aab` sin errores.
- [ ] App instalada desde `.aab` (vía `bundletool`) y funciona end-to-end.
- [ ] Privacy policy publicada en `climbingteams.com/privacy`.
- [ ] Listing Play Console completo (textos, capturas, icono).

### Play Console
- [ ] Cuenta de desarrollador pagada y verificada (25 $).
- [ ] App creada en consola.
- [ ] `.aab` subido a Internal testing.
- [ ] Mínimo 5 testers añadidos para feedback inicial.
- [ ] Tras 1-2 semanas → Closed testing.
- [ ] Tras 14 días con ≥12 testers → Producción o Open testing.

---

## 🧪 Iterar versiones en Prueba cerrada (Alpha) — flujo confirmado 2026-06-20

Estado: la app está en **Prueba cerrada – Alpha** en Play Console. Versión
`cumbre1.1` en estado **"Disponible para determinados testers"**. Lista de
testers "cumbre testers" con **14 usuarios**. Ya se han subido 3 `versionCode`
(el próximo build debe ser **≥ 4**).

### Qué significa "Disponible para determinados testers"
- La versión **ya pasó la revisión de políticas de Google** y está publicada
  en el canal de prueba cerrada → los testers de la lista pueden instalarla
  desde Play en cuanto se unan al enlace de prueba.
- Esa revisión es de **políticas**, NO un test de calidad/bugs de la app.
  Los bugs los encuentran los testers (y tú). Google solo comprueba que no
  viole sus normas.
- Si no quieres que la instalen aún, simplemente **no repartas el enlace de
  prueba** todavía (pestaña Testers → "cómo se unen los testers").

### Mejorar la app y subir una versión nueva al canal Alpha
Puedes iterar tantas versiones como quieras antes de pasar a producción; no
se "gasta" nada por subir builds nuevos.

1. En `app/build.gradle.kts`: **subir `versionCode`** (p. ej. 3 → 4) y, si
   quieres, `versionName` (`1.0.0` → `1.0.1`). ⚠️ **Nunca reutilizar un
   `versionCode` ya subido** — Play lo rechaza.
2. Generar el bundle firmado:
   ```powershell
   $env:KEYSTORE_PASSWORD='tu-password'
   $env:KEY_PASSWORD='tu-password'
   ./gradlew :app:bundleRelease
   ```
   → `app/build/outputs/bundle/release/app-release.aab`.
3. Play Console → **Prueba cerrada – Alpha** → **"Crear nueva versión"** →
   subir el `.aab` → notas de la versión → **Guardar → Revisar → Publicar**.
4. Google la revisa otra vez (minutos a pocas horas). Al quedar "Disponible
   para determinados testers", los testers reciben la **actualización
   automática** desde Play.

### Recordatorio para promocionar a producción
Regla 2024 de Google: **mínimo 12 testers opted-in durante 14 días seguidos**
en prueba cerrada antes de poder promocionar a producción. Con 14 en la lista
ya se cumple el número; lo que corre es el reloj de los 14 días desde que
están dentro.

---

## 🔁 Pipeline de release recomendado

A medio plazo, automatizar con GitHub Actions:

1. Push a `main` → CI corre tests y build.
2. Push de tag `v1.x.x` → GitHub Action firma el `.aab` con secrets de
   GitHub y lo sube a Play Console internal track automático.
3. Tras testing manual, promocionar a producción desde Play Console.

Equivalente para iOS con Fastlane + TestFlight cuando se llegue.

---

## ✅ Estado actual (2026-06-10)

### Producción operativa

| Pieza | Estado | URL / Detalle |
|---|---|---|
| Backend Spring Boot | ✅ desplegado en Railway | `https://api.climbingteams.com` |
| Postgres | ✅ gestionada Railway | Con 191 escuelas + datos dev migrados |
| HTTPS | ✅ certificado válido Railway | Cloudflare proxy DESACTIVADO (DNS only) |
| Healthcheck | ✅ UP | `/actuator/health` |
| GET schools | ✅ funciona | `/api/schools` devuelve las 191 |
| Firebase Admin SDK | ✅ inicializado | Via env var `FIREBASE_SA_JSON` |
| Dominio | ✅ propietario `climbingteams.com` | Subdominio `api.climbingteams.com` apuntando con CNAME |

### Configuración Railway aplicada

**Servicio backend (MeteoMontanaAPI)**:
- Root Directory: `api`
- Build: Dockerfile multi-stage en `api/Dockerfile`
- Variables:
  - `FIREBASE_SA_JSON` (JSON entero del service account)
  - `DATABASE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
  - `DATABASE_USERNAME` = `${{Postgres.PGUSER}}`
  - `POSTGRES_PASSWORD` = `${{Postgres.PGPASSWORD}}`
- Custom Domain: `api.climbingteams.com` con CNAME a `xxx.up.railway.app`.

**Servicio Postgres**:
- Plan: por defecto trial Railway.
- Contraseña **rotada** después del setup (la inicial se filtró en chat → ya no vale).
- Tablas creadas automáticamente por Flyway al primer boot del backend.
- Datos migrados con:
  ```powershell
  docker exec -t meteomontana-postgres pg_dump -U meteomontana -d meteomontana --data-only --exclude-table=flyway_schema_history --inserts > backup.sql
  Get-Content api\backup.sql | & "C:\Program Files\PostgreSQL\16\bin\psql.exe" "<URL-Railway>"
  ```

### ✅ Completado (2026-06-10)

1. ~~**Apuntar Android a producción**~~ ✅ — `API_BASE_URL` release = `https://api.climbingteams.com/api/`
2. ~~**Generar keystore release**~~ ✅ — `meteomontana-release.jks` en raíz del repo (en `.gitignore`). Alias `meteomontana`. Guardar la contraseña en sitio seguro.
3. ~~**SHA-1 release a Firebase**~~ ✅ — `14:99:3A:66:0C:2C:EC:54:2F:91:73:1C:8E:7C:FB:BD:4E:37:75:D4` añadido a Firebase Console.
4. ~~**Build firmado**~~ ✅ — `assembleRelease` funciona. APK probado en móvil físico: login, escuelas, detalle, propuesta. Todo operativo contra producción.

### Pendiente para Play Store

5. **Quitar cleartext HTTP** del `network_security_config.xml` (Play Store lo requiere).
6. **Plan Blaze de Firebase** (necesario para FCM y Storage en producción).
7. **Play Console** (25 $ una vez): cuenta + listing + capturas + privacy policy en `climbingteams.com/privacy` + Data Safety form.

### Flujo de trabajo habitual

**Desarrollo día a día**: botón Run de Android Studio → build debug → instala automático en el móvil/emulador. El debug apunta a `http://192.168.0.12:8080/api/` (backend local).

**Cuando quieras probar contra producción** (sin publicar):
```powershell
$env:KEYSTORE_PASSWORD='tu-password'
$env:KEY_PASSWORD='tu-password'
./gradlew :app:assembleRelease
& "C:\Users\rouma\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 12bf0837 uninstall com.meteomontana.android
& "C:\Users\rouma\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 12bf0837 install app\build\outputs\apk\release\app-release.apk
```
Nota: hay que desinstalar primero si tenías el debug instalado (firmas distintas).

**Cuando quieras subir a Play Store** (cuando llegue el momento):
```powershell
$env:KEYSTORE_PASSWORD='tu-password'
$env:KEY_PASSWORD='tu-password'
./gradlew :app:bundleRelease
```
Sale `app/build/outputs/bundle/release/app-release.aab` → ese fichero es el que sube a Play Console.

### Pendiente Android (mejoras pre-publicación)

Sin urgencia, pero conviene antes de Play Store:
- **#1 Fluidez**: medir en build release y perfilar si sigue lenta.
- **#6 Vías en vez de bloques** en `AddBlockSheet` (al añadir entrada de diario, mostrar vías existentes con su grado + tipo, autocompletar el grado).
- **#8 Stats mensuales**: mover el cálculo a endpoint backend cacheado en vez de llamar a `archive-api.open-meteo.com` desde Android.
- **#9 Foto crop**: el `TopoPhotoCanvas` usa aspect 4:3 fijo → ajustar para respetar aspect real.
- **#2 Push de seguidor**: ya se manda con deep link pero podría incluir avatar en la notificación nativa.

### Pendiente backend (mejoras pre-publicación)

- **Plan Blaze de Firebase** antes de salir a Play Store (FCM ilimitado solo en Blaze).
- **Variables opcionales en Railway** que NO he activado:
  - `RESEND_API_KEY` + `RESEND_FROM` (los emails de aprobado/rechazado solo se mandan si están definidas).
  - `FIREBASE_STORAGE_BUCKET` (usa el default `climbingteams.firebasestorage.app` si no se pone).

#### 🔒 Rate limiting — HACER ANTES DE PUBLICAR (no hay ninguno hoy)

**Por qué:** hoy cualquiera puede martillear la API (endpoints públicos y de
escritura) sin límite. Con el pool de Postgres en **5 conexiones**, un bot o un
pico puede **tumbar la API o disparar el coste** (es el tipo de caída ya visto).
El forecast está cacheado (Caffeine 90 min), el resto no.

**Límites calibrados al uso real de la app** (abrir la app = ráfaga de ~30-50
peticiones en segundos; escrituras pocas y lentas). Devolver **HTTP 429 +
`Retry-After`** al superarlos:

| Tipo de endpoint | Límite | Clave |
|---|---|---|
| Lecturas públicas (catálogo, blocks, forecast, perfil público) | **100 req/min** | por **IP** |
| Autenticadas de lectura (me, journal, favoritos…) | **120 req/min** | por **uid** |
| Escrituras (POST/PUT notas, journal, contribuciones) | **20 req/min** | por **uid** |
| Subida de fotos / proponer piedra (caras) | **8-10 req/min** | por **uid** |
| Push / chat notify | **30 req/min** | por **uid** |

> Clave por **uid** (no IP) en lo autenticado: varios escaladores comparten IP en
> el wifi de rocódromos/albergues. NO limitar `/actuator/health` (Railway lo usa
> para healthchecks).

**Cómo hacerlo — dos capas (empezar por la 1):**

1. **Cloudflare delante de `api.climbingteams.com`** (gratis, 0 código, ~10 min de
   DNS): proxy naranja + regla "IP > 100 req/min → bloquear 1 min". Trae **DDoS
   gratis**. Cubre el grueso del riesgo de bots aunque el backend esté saturado.
   Limitación: es por IP, no distingue usuarios → de ahí la capa 2.
2. **Bucket4j en Spring** (preciso, por uid): añadir dependencia `bucket4j-core`,
   un `Filter` que corre DESPUÉS de `FirebaseTokenFilter`, elige la clave (uid si
   hay sesión, si no IP), aplica el bucket por tipo de ruta y responde **429 +
   Retry-After**. Token-bucket **en memoria** (hay 1 sola instancia en Railway →
   no hace falta Redis; si algún día se escala a varias instancias, mover el
   contador a Redis). Añadir un test del filtro.

**App (cliente):** manejar el **429 con elegancia** (reintento con backoff o aviso
"espera un momento"), no como error genérico. Hoy lo mostraría como error feo.

**Riesgo del cambio:** límites mal calibrados (demasiado bajos) molestarían a
usuarios reales → por eso son generosos (≈2× la ráfaga normal). Vigilar que
`/actuator/health` y los webhooks queden excluidos.

### 🔑 google-services.json — cómo tenerlo en cualquier PC (Android)

El módulo `app` necesita `app/google-services.json` (Firebase, proyecto
**climbingteams**) para compilar con login/push reales. Está **gitignored** (no
se sube). Si una sesión NO lo encuentra en `app/`, hay 3 vías para recuperarlo:

1. **Copiarlo del PC donde sí está** (Rodrigo lo descargó de Firebase Console →
   Project settings → tu app Android → `google-services.json`).
2. **Reconstruirlo desde el APK instalado en el móvil** (lo que se hizo el
   2026-06-21 cuando no estaba en este PC): `adb pull` del `base.apk`
   (`adb shell pm path com.meteomontana.android`) y `aapt2 dump resources` para
   leer `google_app_id`, `project_id`, `default_web_client_id`, etc.
3. **Pegar el contenido de abajo** en `app/google-services.json` (son los valores
   reales del proyecto; NO son secretos de alto riesgo: viajan dentro de cada APK
   publicado y la API key va restringida por package+SHA-1). Sirve para COMPILAR;
   para que el **login** funcione en un APK, además la SHA-1 de la clave que firma
   ese APK debe estar registrada en Firebase Console.

```json
{
  "project_info": {
    "project_number": "977545428920",
    "project_id": "climbingteams",
    "storage_bucket": "climbingteams.firebasestorage.app"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:977545428920:android:0b5455c96caa0411c6d94d",
        "android_client_info": { "package_name": "com.meteomontana.android" }
      },
      "oauth_client": [
        { "client_id": "977545428920-ijvn4t70oqihoj9epncib6n49aeaqigq.apps.googleusercontent.com", "client_type": 3 }
      ],
      "api_key": [ { "current_key": "AIzaSyA7zIqwBTtPGurizg8z97lVAyf8XtWwoco" } ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            { "client_id": "977545428920-ijvn4t70oqihoj9epncib6n49aeaqigq.apps.googleusercontent.com", "client_type": 3 }
          ]
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

> El `client_info.android_client_info` no incluye la SHA-1: el `default_web_client_id`
> se genera del `oauth_client` tipo 3 (web), suficiente para compilar. El login
> Google valida la SHA-1 contra las registradas en Firebase Console (no contra el
> JSON). En el CI, este fichero lo aporta el secret **`GOOGLE_SERVICES_JSON`**.

### Notas para futuras sesiones

- La contraseña de Postgres en producción **NO es la del chat anterior**, fue rotada.
- El `serviceAccountKey.json` está EXCLUIDO del git (`.gitignore`). Hay una copia local en
  `MeteoMontanaAPI/api/src/main/resources/serviceAccountKey.json` y otra en la raíz del repo.
- Para acceder a Postgres producción desde tu PC: panel Postgres en Railway → tab Connect →
  "Public Networking" URL. NUNCA pegues esa URL en logs o chat.
- El backend Railway tarda ~30s en arrancar tras un redeploy.

### Plantilla mensaje para retomar en otra sesión

```
Sigo desde donde lo dejé en DEPLOYMENT.md → "Estado actual (2026-06-10)".

Producción funcionando: api.climbingteams.com con 191 escuelas, HTTPS,
Firebase OK. Quiero ahora:
- [ ] Apuntar Android al dominio nuevo y generar keystore release
- [ ] Probar APK release end-to-end con login + propuesta
- [ ] Sacar a Play Console internal testing

Lee CLAUDE.md, KMP_MIGRATION.md y DEPLOYMENT.md antes de empezar.
Modelo recomendado: Sonnet (mecánico) salvo bugs raros.
```
