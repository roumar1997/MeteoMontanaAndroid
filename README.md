# Cumbre

App de escalada para España, Francia y Portugal: escuelas con su previsión y
las condiciones de la roca, piedras y vías con topos, diario personal,
quedadas, chat y un asistente que entiende preguntas en lenguaje natural.

Está publicada en **Google Play** y en el **App Store**.

## Qué hay en este repo

| Carpeta | Qué es |
|---|---|
| `app/` | App Android: Kotlin + Jetpack Compose, Hilt, MapLibre |
| `shared/` | Código común Kotlin Multiplatform (dominio, datos, presentadores) que usan Android **e** iOS |
| `iosApp/` | App iOS: SwiftUI sobre `shared` (se compila en el CI de GitHub) |
| `scripts/` | Utilidades: freno de tamaño de ficheros y herramientas para partir ficheros grandes |
| `config/` | Configuración de los frenos (lista de ficheros grandes, hoy vacía) |
| `docs/` | Diseños de funciones, planes y documentos de tienda |

El **backend** (Spring Boot + PostgreSQL, en Railway) vive en otro repo:
`roumar1997/MeteoMontanaAPI`.

## Arquitectura en una línea

Hexagonal: la UI solo habla con view models y casos de uso; el dominio no sabe
nada de red ni de base de datos; los adaptadores (Ktor, SQLDelight, Firebase)
implementan puertos. Las normas completas están en
[ARCHITECTURE.md](ARCHITECTURE.md). Ningún fichero pasa de **600 líneas**, y lo
comprueban el CI y el pre-commit.

## Compilar y probar

Requisitos: el JDK 21 que trae Android Studio (`JAVA_HOME` apuntando a su JBR)
y un `google-services.json` real en `app/` (no está en git).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- El APK **debug** habla con **staging** y el **release** con **producción**.
- Para ver datos reales en un debug: `./gradlew assembleDebug -PdebugApi=prod`.
- iOS se compila en GitHub Actions (`ios-ci.yml`). TestFlight sale del workflow
  manual «iOS PROD .ipa».
- Antes de subir algo que toque `shared/`: `./gradlew :shared:compileCommonMainKotlinMetadata`.
  Las APIs que solo existen en la JVM rompen iOS.

Activa el freno de tamaño en tu copia local una vez:

```bash
git config core.hooksPath .githooks
```

## Documentos

| Documento | Para qué |
|---|---|
| [COMO_TRABAJAMOS.md](COMO_TRABAJAMOS.md) | Las normas del proyecto: ramas, pruebas, publicación, paridad |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Capas, límites y quién puede depender de quién |
| [REFERENCIA.md](REFERENCIA.md) | Mapa del código y de los endpoints |
| [DESIGN.md](DESIGN.md) | Sistema visual (colores, tipografía, espaciado) |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Cómo publicar en Railway, Play y App Store |
| [HISTORIAL.md](HISTORIAL.md) | Bitácora de cada sesión: causas raíz y decisiones |
| `docs/` | Diseños de funciones (`*_DESIGN.md`), planes y textos de tienda |

## Licencia

Ver [LICENSE](LICENSE). Las fuentes incluidas tienen sus propias licencias
(OFL / Apache 2.0), que la app muestra en su pantalla de licencias.
