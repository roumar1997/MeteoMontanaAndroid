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

### Pendiente para terminar la publicación

1. **Apuntar Android a producción** (lo más inmediato):
   ```kotlin
   // app/build.gradle.kts
   release {
       buildConfigField("String", "API_BASE_URL",
                        "\"https://api.climbingteams.com/api/\"")
       isMinifyEnabled = true
       isShrinkResources = true
       // signingConfig = signingConfigs.getByName("release")
   }
   ```
2. **Generar keystore release**:
   ```powershell
   keytool -genkey -v -keystore meteomontana-release.jks `
     -keyalg RSA -keysize 2048 -validity 10000 `
     -alias meteomontana
   ```
   Guardar en sitio seguro (Drive privado cifrado). Si lo pierdes, no puedes actualizar en Play.
3. **SHA-1 release a Firebase** Console → Settings → tu app Android → Add fingerprint.
4. **Quitar cleartext HTTP** del `network_security_config.xml`.
5. **Build firmado**:
   ```powershell
   ./gradlew :app:bundleRelease
   ```
6. **Probar APK release** en móvil físico: login + ver escuelas + propuesta + chat.
7. **Plan Blaze de Firebase** (necesario para FCM y Storage en producción).
8. **Play Console** (25 $ una vez): cuenta + listing + capturas + privacy policy en `climbingteams.com/privacy` + Data Safety form.

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
