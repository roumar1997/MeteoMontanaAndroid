# Cumbre — instrucciones para Claude

App de escalada publicada en Google Play y App Store. Android (Kotlin + Compose)
e iOS (SwiftUI) comparten `shared/` (Kotlin Multiplatform). Backend Spring Boot
en `C:\Users\rouma\MeteoMontanaAPI` (`roumar1997/MeteoMontanaAPI`).

**Las normas están en [`COMO_TRABAJAMOS.md`](COMO_TRABAJAMOS.md). Léelo antes de
empezar: manda sobre cualquier otro documento.**

## Lo imprescindible

- El usuario es **Álvaro**. Responde en español y escribe el código en inglés.
- **Hay usuarios reales.** Para pasar algo a `main` hace falta su «sí, fusiona».
  El backend se sube primero a `develop` (staging) y se verifica ahí.
- **SOLID + hexagonal siempre** ([ARCHITECTURE.md](ARCHITECTURE.md)). Límite de
  600 líneas por fichero en las apps y 400 en el backend; lo comprueban el CI y el pre-commit.
- **Paridad**: iOS y Android iguales en pantallas, textos y colores. El panel de
  admin vive **solo en iOS**. El diseño visual está terminado.
- **Modelos**: elige libremente el modelo y el nivel de razonamiento de cada
  tarea, también para los subagentes. Antes de usar el nivel **máximo**, pídeselo a Álvaro.
- **Al terminar cada tarea**: una línea en `HISTORIAL.md`, `estado-cumbre.json`
  al día y un resumen corto de qué se ha probado y cómo.

## Comandos

```bash
# Android (JAVA_HOME = JBR de Android Studio)
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
./gradlew.bat :shared:compileCommonMainKotlinMetadata   # antes de subir cambios de shared (iOS)
./gradlew.bat :app:compileReleaseKotlin                 # antes de un AAB
bash scripts/check-file-sizes.sh                        # freno de tamaño

# Backend (en MeteoMontanaAPI/api). contextLoads falla en este PC: es normal
./mvnw test

# iOS: solo en CI. TestFlight:
gh workflow run ios-prod-ipa.yml --ref <rama> -f runner=macos-15-intel
```

## Entornos

| Build | Backend | Rama del backend |
|---|---|---|
| debug | staging `meteomontanaapi-staging.up.railway.app` | `develop` |
| release | producción `api.climbingteams.com` | `main` |

Datos reales en un debug: `-PdebugApi=prod`. Firebase está compartido entre
los dos entornos. `climbingteams.com` lo sirve **Cloudflare Pages** (push a `main` de
`roumar1997/MeteoMontana`), no Firebase Hosting.

## Dónde está cada cosa

| Documento | Qué hay |
|---|---|
| [COMO_TRABAJAMOS.md](COMO_TRABAJAMOS.md) | Normas: ramas, pruebas, publicación, paridad, modelos |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Capas y reglas del código |
| [REFERENCIA.md](REFERENCIA.md) | Mapa del código, endpoints, instalar iOS sin Mac |
| [DESIGN.md](DESIGN.md) | Sistema visual (tokens, tipografía, espaciado) |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Publicar en Railway, Play y App Store |
| [HISTORIAL.md](HISTORIAL.md) | Bitácora: causas raíz y trampas. **Búscalo antes de sacar conclusiones** |
| `docs/` | Diseños de funciones, planes y textos de tienda |
| `docs/archivo/` | Versiones anteriores de este fichero y del «Estado actual» |

## Estado actual

- **Publicada: 7.1.4** en las dos tiendas, y es la versión mínima en Railway.
- **TestFlight 351** (rama `feature/7.3-optimiza-lola`): 7.2 + optimización iOS/Android + efectos desde el servidor (Furgo Lola). Pendiente: pruebas i1–i15 de Álvaro → «sí, fusiona» → tiendas.
- **Antes de la 7.2** (orden acordado el 2026-11-06):
  1. normas y memorias;
  2. mods «Taller» y «Pruebas»;
  3. reglas automáticas: detekt + SwiftLint, deuda del servidor (aportaciones sin puerto), tests de contrato servidor↔apps;
  4. tests de capturas en Android;
  5. recorridos con Maestro;
  6. publicación segura;
  7. interruptor remoto;
  8. pruebas y publicar.
- **Siempre que se toque algo**: pasar a `shared` la lógica de negocio que esté copiada en Swift.
- **Después de la 7.2**: partir Android en módulos por funcionalidad y medir la fluidez (Álvaro nota Android algo más lento que iOS).
- Backend en PRODUCCIÓN (main a639f6d, V99): interruptor remoto + catálogo de efectos (`/api/title-effects`). Efectos nuevos se añaden sin release (ver `docs/efectos/README.md`).

## Trampas vigentes

- **Tren de iOS**: si App Store Connect contesta «train closed», sube el 2.x de `iosApp/project.yml`.
- **Fotos**: van por `photos.climbingteams.com`. Si cambias una ya subida, purga la caché de Cloudflare.
- **Asistente de IA**: antes de tocarlo, lee sus trampas en `docs/archivo/ESTADO_ANTERIOR.md`. Las frases que fallen van a `GeminiPhraseSuite`.
- **Instalar en el móvil**: la app de Play va firmada por Google. Un build local
  no se instala encima: hay que desinstalar antes, y se pierde lo offline.
- **Worktree nuevo**: copia el `google-services.json` real y `local.properties` con barras normales.
- **PowerShell corrompe los acentos** al hacer barridos con Get-Content/Set-Content: usa node o perl.
