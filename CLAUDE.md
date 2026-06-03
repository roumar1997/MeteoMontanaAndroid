# MeteoMontana Android — contexto para Claude

App Android nativa (Kotlin + Jetpack Compose) que replica la PWA MeteoMontana
y se conecta al backend Spring Boot en `C:\Users\rouma\MeteoMontanaAPI`.

## Workflow de cada sesión

**Primer mensaje**: el usuario dirá *"léete CLAUDE.md y sigamos por donde lo
dejamos"*. Lees este archivo, miras **Estado actual** al final, y arrancas
desde ahí sin repetir el plan entero.

**Antes de cerrar**: actualiza **Estado actual** y **Bitácora reciente**.
Marca con `[x]` lo completado en el mapa de fases.

## Cómo trabaja el usuario

Junior developer aprendiendo. Quiere entender cada línea. Reglas:

1. **Idioma de comunicación: español.** Código en **inglés**.
2. **Paso a paso.** Una cosa a la vez. Esperas confirmación antes del siguiente.
3. **El usuario escribe el código.** Tú das snippets explicados.
4. **Formato de explicación**: Contexto → Código completo → Línea a línea →
   Conceptos nuevos → Pregunta de senior al final.
5. **Verifica antes de proponer.** Lee el código existente antes de tocar algo.
6. **Trade-offs explícitos** en decisiones de diseño.

## Stack decidido

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose (declarativa, moderna, equivalente a SwiftUI)
- **Navegación**: Navigation Compose
- **Red**: Retrofit + OkHttp (HTTP client estándar Android)
- **JSON**: Gson o Moshi
- **Auth**: Firebase Auth SDK para Android (Google Sign-In)
- **Push**: FCM (Firebase Cloud Messaging) — ya está configurado en el back
- **Mapas**: MapLibre Android SDK (mismo motor que la PWA)
- **DI**: Hilt (inyección de dependencias, estándar Android moderno)
- **Async**: Kotlin Coroutines + Flow
- **Imagen**: Coil (carga de imágenes async)
- **Min SDK**: API 26 (Android 8.0) — cubre >95% de dispositivos activos

## Conexión con el backend

Base URL del back:
- **Local (emulador Android)**: `http://10.0.2.2:8080/api`
  (el emulador no puede usar `localhost` porque eso es el propio emulador;
  `10.0.2.2` es el alias al host).
- **Local (dispositivo físico en misma red)**: `http://<IP-del-PC>:8080/api`
  (necesitarás el firewall de Windows abierto al puerto 8080).
- **Producción**: pendiente de desplegar (Railway/Render/Fly.io, ver
  `MeteoMontanaAPI/DEPLOY.md`).

Firebase project: **climbingteams**
- Auth provider: Google
- Config en `google-services.json` (se descarga de Firebase Console →
  Project settings → Add app → Android, package `com.meteomontana.android`).

**Auth**: el back valida Firebase ID tokens en el header
`Authorization: Bearer <token>`. La app Android obtiene el token con:
```kotlin
FirebaseAuth.getInstance().currentUser?.getIdToken(false)
```

### Endpoints REST disponibles (back ya completo, 11/11 fases)

**Públicos (sin token)**:
| Endpoint | Descripción |
|---|---|
| `GET /api/schools[?region&style&rockType&lat&lon&radioKm]` | Catálogo con filtros |
| `GET /api/schools/{id}` | Detalle de una escuela |
| `GET /api/schools/{id}/notes` | Notas comunitarias |
| `GET /api/schools/{id}/photos` | Fotos (URLs firmadas 60min) |
| `GET /api/schools/{id}/forecast` | Tiempo + score por hora (cache 30min en back) |
| `GET /api/users/{uid o username}` | Perfil público (oculta privados con 404) |
| `GET /actuator/health` | Healthcheck |

**Autenticados (Bearer token Firebase)**:
| Endpoint | Descripción |
|---|---|
| `GET /api/me` | Perfil privado (crea entrada en BD en primer login — JIT provisioning) |
| `PUT /api/me` | Actualizar perfil (username, displayName, bio, topGrade, isPublic) |
| `PUT /api/me/fcm-token` | Registrar token FCM tras login para push |
| `POST /api/schools/{id}/photos` | Subir foto (multipart, max 5MB image/*) |
| `DELETE /api/photos/{photoId}` | Borrar foto propia |
| `POST /api/submissions` | Proponer escuela nueva |
| `GET /api/submissions/me` | Mis propuestas y su estado |

**Solo admin (`users.is_admin = true` en BD)**:
| Endpoint | Descripción |
|---|---|
| `GET /api/admin/submissions` | Cola pending |
| `POST /api/admin/submissions/{id}/approve` | Aprueba (crea School + log + push al autor) |
| `POST /api/admin/submissions/{id}/reject` | Rechaza con motivo |
| `GET /api/admin/logs` | Auditoría |
| `POST /api/admin/push` | Push manual (target uid o broadcast) |

### Qué se queda en Firebase (sin pasar por la API)

- **Auth** — Firebase Auth SDK directo desde la app
- **Storage** — Firebase Storage para fotos (la API también, pero la app
  puede leer URLs firmadas devueltas en los DTOs)
- **FCM** — push directo de Firebase al dispositivo
- **Chat 1-a-1** — Firestore realtime (decisión consciente, ver back CLAUDE.md)

### Lo que aún no tiene endpoint en el back (TODOs)

Si la app los necesita antes de tiempo, los añadimos en el back:
- `POST /api/me/photo` — subir foto de perfil
- `GET /api/users/search?q=...` — búsqueda usuarios
- `POST /api/schools/{id}/notes` + voto en notas
- Follows (`POST/DELETE /api/users/{uid}/follow`, listas)
- Journal (`POST/GET /api/journal`)
- Bandeja de notificaciones en BD (o leer de Firestore — TBD)

## Arquitectura de la app

```
app/
  src/main/java/com/meteomontana/android/
    data/
      api/          — interfaces Retrofit + DTOs
      repository/   — repositorios que combinan API + caché local
    domain/
      model/        — modelos de negocio puros (School, Note, ...)
      usecase/      — casos de uso
    ui/
      screens/      — pantallas Compose (SchoolListScreen, SchoolDetailScreen, ...)
      components/   — componentes reutilizables (SchoolCard, NoteItem, ...)
      theme/        — colores, tipografía, tokens de diseño
    di/             — módulos Hilt
    navigation/     — grafo de navegación
```

## Diseño visual — tema Cumbre (replicar EXACTO de la PWA)

Sistema de diseño **Cumbre**: papel, tinta, terracota. Sin gradientes, sin
blur, casi sin border-radius. Hairlines de papel como bordes.
Fuente: `css/style.css` y `css/tokens.css` de la PWA.

### Colores light mode
```kotlin
// Superficies
val Bg      = Color(0xFFF5F3EE)  // --bg
val Paper   = Color(0xFFEBE7DD)  // --paper
val Paper2  = Color(0xFFF0EAD8)  // --paper-2

// Tinta
val Ink     = Color(0xFF1C1C1A)  // --ink
val Ink2    = Color(0xFF5A574F)  // --ink-2
val Ink3    = Color(0xFF8A8478)  // --ink-3
val Rule    = Color(0xFFD6D2C4)  // --rule (bordes/divisores)

// Accent
val Terra   = Color(0xFFC2410C)  // --terra (acento principal)
val TerraBg = Color(0xFFFDE4D3)  // --terra-bg
val Moss    = Color(0xFF5E6B4F)  // --moss (acento secundario)

// Score states
val Ok      = Color(0xFF3F6B4A)  // --ok  (verde, score bueno)
val Warn    = Color(0xFFB45309)  // --warn (ambar, score medio)
val Bad     = Color(0xFF9A3412)  // --bad  (rojo, score malo)

// Meteorológicos
val Rain    = Color(0xFF2563C7)  // --rain
val Wind    = Color(0xFF4A7C3F)  // --wind
```

### Colores dark mode
```kotlin
val BgDark      = Color(0xFF15140F)  // --bg dark
val PaperDark   = Color(0xFF1D1C17)  // --paper dark
val Paper2Dark  = Color(0xFF211F19)  // --paper-2 dark
val InkDark     = Color(0xFFECE7D8)  // --ink dark
val Ink2Dark    = Color(0xFFA8A397)  // --ink-2 dark
val Ink3Dark    = Color(0xFF6E6A5F)  // --ink-3 dark
val RuleDark    = Color(0xFF2A281F)  // --rule dark
val TerraDark   = Color(0xFFE0612B)  // --terra dark
val MossDark    = Color(0xFF7D8A6A)  // --moss dark
val OkDark      = Color(0xFF7DA068)  // --ok dark
val WarnDark    = Color(0xFFD6904A)  // --warn dark
val BadDark     = Color(0xFFC9543B)  // --bad dark
```

### Score heatmap (colores exactos de tokens.css)
```kotlin
fun scoreColor(score: Int): Color = when {
    score >= 90 -> Color(0xFF5E8B50)  // .s-90 — text blanco
    score >= 80 -> Color(0xFF82A76E)  // .s-80 — text blanco
    score >= 70 -> Color(0xFFB7C089)  // .s-70 — text ink
    score >= 60 -> Color(0xFFE3D599)  // .s-60 — text ink
    score >= 50 -> Color(0xFFE8B878)  // .s-50 — text ink
    score >= 40 -> Color(0xFFD99A5A)  // .s-40 — text ink
    score >= 30 -> Color(0xFFC2410C)  // .s-30 — text blanco
    score >= 20 -> Color(0xFF9A3412)  // .s-20 — text blanco
    else        -> Color(0xFF5A1E08)  // .s-10 — text blanco
}

fun scoreTextColor(score: Int): Color =
    if (score in 40..79) Color(0xFF1C1C1A) else Color.White
```

### Tipografía
- `--font-sans`: Inter → en Android usar `FontFamily.Default` (system-ui)
- `--font-mono`: JetBrains Mono → añadir como font asset, usar en scores y datos numéricos
- `--font-serif`: Source Serif 4 → añadir como font asset, usar en títulos destacados

### Forma / border-radius
```kotlin
// La PWA usa casi cero radius:
// --radius-sm: 0px  --radius: 2px  --radius-lg: 4px
val ShapeNone = RoundedCornerShape(0.dp)
val ShapeSm   = RoundedCornerShape(2.dp)
val ShapeMd   = RoundedCornerShape(4.dp)
```

### Reglas visuales clave (no negociables)
- **Sin blur / elevation shadows** — solo bordes `1.dp` color `Rule`
- **Sin gradientes de fondo** — color plano `Bg`
- **Cards**: fondo `Paper`, borde `Rule` 1dp, radius `2dp`
- **Botón primario**: fondo `Terra`, texto blanco, radius `2dp`
- **Spinner**: borde `Rule`, top `Terra`, igual que `.cumbre-spinner` de la PWA
- **Animación entrada**: fade + translateY(4px→0) en 250ms (`.fade-in` de la PWA)

## Features a migrar desde la PWA

En orden de prioridad:

1. **Catálogo de escuelas** — lista con filtros + mapa
2. **Detalle de escuela** — tiempo + score + notas
3. **Login con Google** — Firebase Auth
4. **Perfil de usuario** — foto, bio, topGrade
5. **Diario personal** — journal de sesiones
6. **Notas comunitarias** — leer y crear notas
7. **Follows** — seguir usuarios
8. **Notificaciones push** — FCM
9. **Favoritos** — guardar escuelas
10. **Chat** — mensajería directa (Firestore realtime)

## Mapa de fases

### Fase 1 — Proyecto base ⬜
- [ ] Crear proyecto Android en Android Studio (Empty Compose Activity)
- [ ] Añadir dependencias: Retrofit, Hilt, Coil, Navigation Compose
- [ ] `google-services.json` desde Firebase Console
- [ ] Estructura de carpetas según arquitectura
- [ ] Theme Compose con tokens Cumbre exactos (colores, tipografía, shapes)

### Fase 2 — Catálogo de escuelas ⬜
- [ ] DTO `SchoolDto` + interfaz Retrofit `SchoolApi`
- [ ] `SchoolRepository` con llamada a `GET /api/schools`
- [ ] `GetSchoolsUseCase` con filtros
- [ ] `SchoolListScreen` — lista con LazyColumn + SchoolCard
- [ ] Filtros básicos: región, estilo, roca

### Fase 3 — Detalle + notas ⬜
- [ ] `SchoolDetailScreen` — info + notas
- [ ] `GET /api/schools/{id}/notes` integrado
- [ ] Score visual (cuando el back lo exponga)

### Fase 4 — Auth Google ⬜
- [ ] Firebase Auth SDK configurado
- [ ] Google Sign-In con Credential Manager
- [ ] Token interceptor en OkHttp (añade `Authorization: Bearer`)
- [ ] Pantalla de login + estado autenticado global

### Fase 5 — Forecast + score ⬜
- [ ] `GET /api/schools/{id}/forecast` integrado
- [ ] Score heatmap por hora (igual que `hourly-heatmap.js` de la PWA)
- [ ] Comparador de favoritos

### Fase 6 — Perfil + diario ⬜
- [ ] `GET /api/me`, `PUT /api/me`
- [ ] `ProfileScreen` — foto, bio, topGrade
- [ ] `JournalScreen` — lista de sesiones + crear sesión

### Fase 7 — Comunidad ⬜
- [ ] `POST /api/schools/{id}/notes` — crear nota
- [ ] Votos up/down en notas
- [ ] `GET /api/users/{uid}` — perfil público
- [ ] Follows

### Fase 8 — Mapa ⬜
- [ ] MapLibre Android integrado
- [ ] Marcadores coloreados por score (igual que la PWA)
- [ ] Click en marcador → SchoolDetailScreen

### Fase 9 — Push + notificaciones ⬜
- [ ] FCM configurado
- [ ] Bandeja de notificaciones
- [ ] Preferencias de notificación

### Fase 10 — Chat ⬜
- [ ] Firestore SDK para Android (chat realtime)
- [ ] `ConversationsScreen` + `ChatScreen`

### Fase 11 — Polish + Play Store ⬜
- [ ] Splash screen, iconos, nombre app
- [ ] Deep links
- [ ] Firma APK + subir a Play Store (internal testing)

## Bitácora reciente

- **2026-06-03**: back terminado al 100% (11/11 fases). Endpoints de
  schools, notes, photos, forecast, users, submissions, admin, push y
  health funcionando localmente. Probado con Postman: `/actuator/health`
  → UP, `/api/schools/albarracin/forecast` → 200 con scores.
- **2026-06-03**: proyecto Android creado en Android Studio con plantilla
  Empty Activity (Compose), package `com.meteomontana.android`,
  minSdk 26, Kotlin DSL. Carpeta `MeteoMontanaAndroid/` con estructura
  estándar (app/, gradle/, gradlew). `MainActivity.kt` y `ui/theme/`
  generados por la plantilla.

## Estado actual

**Fase 1 — Proyecto base (EN CURSO)**

- [x] Proyecto Android creado (Empty Activity + Compose)
- [ ] Esperar a que Gradle sincronice sin errores
- [ ] Añadir dependencias al `app/build.gradle.kts`:
      Retrofit + OkHttp + Moshi/Gson, Hilt, Coil, Navigation Compose,
      Firebase BOM (Auth, Messaging, Storage), Coroutines
- [ ] `google-services.json` desde Firebase Console
      (Project settings → Add app → Android, package
      `com.meteomontana.android`)
- [ ] Sustituir `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`
      por los tokens **Cumbre** exactos (definidos arriba en este archivo)
- [ ] Estructura de carpetas hexagonal (data/, domain/, ui/screens/,
      ui/components/, di/, navigation/)
- [ ] Pantalla de prueba: pegar contra `GET /api/schools` con Retrofit
      y mostrar nombres en una lista. Configurar emulador con
      `http://10.0.2.2:8080/api`.

**Siguientes fases**: cuando Fase 1 esté lista, todas las fases 2-9 se
pueden hacer ya porque el back las soporta. La Fase 10 (chat) usa
Firestore directo. Fase 11 (Play Store) cuando esté pulida.

**Referencia del back**: `C:\Users\rouma\MeteoMontanaAPI`
**Referencia de la PWA**: `C:\Users\rouma\Desktop\MeteoMontana`
**CSS de diseño**: `C:\Users\rouma\Desktop\MeteoMontana\css\style.css`
                   `C:\Users\rouma\Desktop\MeteoMontana\css\tokens.css`

**Notas operativas para el siguiente Claude**:
- Para probar contra el back local hay que arrancarlo antes:
  `docker compose up -d` en `MeteoMontanaAPI/` + `./mvnw spring-boot:run`
  en `MeteoMontanaAPI/api/`.
- En el emulador Android, `localhost` ES el propio emulador. Usa
  `http://10.0.2.2:8080/api` para hablar con tu PC host.
- El back ya tiene CORS configurado para `localhost:5173, 3000,
  127.0.0.1:5500, climbingteams.com`. Las apps móviles NO usan CORS
  (no son navegadores), así que da igual.
- El back valida tokens Firebase contra el proyecto `climbingteams`.
  Si quieres usar otro proyecto Firebase, hay que cambiar
  `serviceAccountKey.json` en el back también.
