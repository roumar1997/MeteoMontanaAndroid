# ARCHITECTURE.md — Reglas de arquitectura de Cumbre

> Documento NORMATIVO (no histórico): dice dónde va cada cosa y por qué.
> Si un cambio viola una regla de aquí, o se corrige el cambio o se corrige
> la regla — pero nunca se ignora en silencio. La arquitectura que no está
> escrita se erosiona.

## 1. El mapa de capas (las dos apps)

```
UI (Compose / SwiftUI)  →  ViewModel  →  UseCase (shared)  →  Repositorio (shared)  →  API/BD
                                              ↑
                                    domain/model (puro, compartido)
```

Reglas de dependencia (la flecha SOLO apunta hacia dentro):

1. **La UI no llama APIs ni repos directamente** — siempre a través del
   ViewModel, y el ViewModel a través de use cases. Ningún VM inyecta un `Ktor*Api` (cerrado
   en 2026-11; lo vigila `ArchitectureRulesTest`).
2. **El dominio (`shared/domain`) es puro**: sin Ktor, sin Firebase, sin
   tipos de plataforma, sin anotaciones de framework. Las únicas excepciones
   toleradas están documentadas en el propio código (p. ej. `descriptionText`
   por colisión con NSObject en iOS).
3. **Los efectos de plataforma** (MediaStore, cámara, GPS, BLE…) viven en
   `data/local` (Android) o detrás de un puerto con bridge (patrón de
   docs/KMP_MIGRATION.md §22) — nunca inline en un composable.
3.b **Gotcha de Kotlin/Native al crear un puerto nuevo**: si la clase que lo
   implementa ya lleva `@Throws(Exception::class)` en un método, la INTERFAZ
   debe llevarlo también — si no, Android compila pero el build de iOS falla
   con *"Member overrides different '@Throws' filter"*. Pasó al dar puerto a
   las cachés (`ProfileCache`/`SchoolCatalogCache`, 2026-07-27). La convención
   general es que los puertos NO llevan `@Throws` (lo llevan los use cases,
   que son los que cruzan a Swift): si el implementador lo lleva, iguala la
   interfaz.

4. **Backend hexagonal**: los use cases de `application/` dependen de puertos
   de `domain/port`, no de entidades JPA. (Deuda conocida 2026-11: la revisión de
   aportaciones — `ReviewContributionUseCase`, `BlockMaterializer`, `LineReconciler` —
   usa la capa de persistencia directamente; hay que darle un puerto.) El mapeo excepción→HTTP
   vive SOLO en `GlobalExceptionHandler`; el dominio no conoce HTTP.

## 2. Tamaños y responsabilidad única

- **Un fichero = una razón de cambio.** Si al describir un fichero necesitas
  la palabra "y" más de una vez, toca dividirlo.
- **Límite duro**: 600 líneas por fichero en las apps (Kotlin y Swift) y 400 en el
  backend. Lo bloquean el CI y el pre-commit (`scripts/check-file-sizes.sh`).
  Si un fichero se pasa, se parte por responsabilidades; no se sube el límite.
- Un composable/función con **>12 parámetros** está pidiendo un state-holder
  (`@Stable` class con `mutableStateOf`, como `ProposalMapBridge`).
- **iOS: todo ViewModel NUEVO recibe sus dependencias por `init` con default**
  (`init(container: IosDependencyContainer = AppDependencies.shared.container)`,
  ver `SchoolMapViewModel`). En la app no cambia nada; en un test se le pasa un
  container con fakes. Prohibido resolver `AppDependencies.shared` inline en
  las propiedades de un VM nuevo (los antiguos se migran boy-scout, sin big-bang).

## 3. El módulo del mapa de escuela (refactor 2026-07-19)

Referencia de cómo queremos organizar features grandes — y espejo 1:1 con iOS. (Tabla de 2026-07: desde entonces algunos ficheros se han partido más; los nombres sirven de patrón, no de inventario.)

| Fichero (Android) | Responsabilidad única | Espejo iOS |
|---|---|---|
| `SchoolMap.kt` (~450) | ORQUESTADOR: toggle, deep-links, cablea flujos y ficha | `SchoolMapSection` |
| `SchoolMapView.kt` | El mapa MapLibre: cámara, estilos, capas, fullscreen | `MapLibreView.swift` |
| `MapMarkerFactory.kt` | Bitmaps de pines + caché de iconos (anti-corrupción atlas) | `MarkerRenderer.swift` |
| `MarkerPlacer.kt` | Sincroniza datos → anotaciones; marker→Block en taps | (dentro de MapLibreView) |
| `MapMiniCards.kt` | Mini-fichas, buscador, botonera, leyenda (Compose puro) | (varias) |
| `FeedPublishSheet.kt` | Hoja de publicar + foto de celebración | `FeedPublish.swift` |
| `MapFlowState.kt` | `ProposalMapBridge` (puente flujo↔mapa) + `WallEditState` | — |
| `data/local/CelebrationGallery.kt` | MediaStore (plataforma) | `FeedCelebrationPhoto.swift` |

Patrones que este refactor fija como CASA:

- **State-holder en vez de prop-drilling**: estados+callbacks relacionados van
  en una clase `@Stable` con `mutableStateOf`. Los listeners registrados una
  sola vez (factory de MapLibre) leen por referencia y siempre están frescos —
  nada de `rememberUpdatedState` en cadena.
- **Nada de estado global a nivel de fichero**: si algo necesita estado entre
  funciones, es una clase de instancia (`MarkerPlacer`), no un `val` top-level
  mutable.
- **Lógica de dominio fuera de composables**: si un `remember {}` calcula algo
  con reglas de negocio (claves del diario, decisiones de publicación), se
  extrae a función pura con test (`matchedLineIds`).

### 3.b Ficha de escuela con pestañas y lista de vías (2026-10-14)

Segundo ejemplo de cómo organizar una feature, esta vez con la lógica en `shared` para que iOS y Android no la
escriban dos veces:

| Pieza | Responsabilidad única | Dónde |
|---|---|---|
| `SchoolLinesExplorer` | PURA: filtrar (texto, grados, beta), ordenar, podio y agrupar por sector | `shared/domain/usecase/schools` (+ test) |
| `LoadSchoolLinesUseCase` / `LoadBetaLineIdsUseCase` | descargar todas las páginas (sin conocer el tamaño de página del servidor) | idem |
| `NoteOrdering` · `VoteOrdering` | orden de notas y de barras de votos (estable, sin depender del orden de inserción) | `shared/domain/usecase/notes` y `community` |
| `SchoolBlocksExplorerModel` | SOLO estado de pantalla + llamar a los use cases (iOS) | `SchoolBlocksExplorerModel.swift` |
| `SchoolBlocksExplorerView` | SOLO pintar | `SchoolBlocksExplorerView.swift` |
| `SchoolDetailHeader` · `SchoolDetailTabs` · `GradePill` | cabecera, pestañas y píldora de grado | ficheros propios |

Regla que sale de aquí: **si una vista de iOS ordena, filtra o agrupa con reglas de negocio, eso va a shared con test**
(y Android lo reutiliza). Un `SchoolDetailView` por encima de 600 líneas se parte, como se hizo aquí (660 → ~550).

## 4. Paridad Android ↔ iOS

- Las dos apps son iguales en pantallas, textos, orden y colores. **Se hace primero
  en iOS**; cuando Álvaro lo valida, Android lo copia a partir de capturas concretas.
  Excepción: el panel de admin solo existe en iOS.
- La lógica compartible vive en `shared/` (KMP). Lo que iOS replica a mano
  (espejos: GradeColor, TopoShared, MarkerRenderer, TopoParse, serialización
  de bloques) lleva SIEMPRE un comentario "espejo de X.kt" en ambos lados y
  **tests en las dos plataformas con los mismos casos** (GradeColorTests /
  TopoSharedTests ↔ sus equivalentes Kotlin).
- Al organizar una feature nueva en ficheros, usar los MISMOS nombres de pieza
  en las dos plataformas (tabla de arriba como ejemplo).

## 5. Tests (la red de seguridad)

- **Tests verdes antes de cada commit, siempre.** Un refactor va: diseño →
  mover → compilar → tests → commit. Pasos pequeños; nada de big-bang.
- **Todo error arreglado** (no solo los de producción) deja un **test de regresión** con el porqué en el
  comentario (patrón: JournalViaKeyTest, FeedContractTest).
- Los CONTRATOS backend↔apps tienen test en los DOS lados con la misma
  muestra dorada (FeedContractTest .java/.kt). Cambios de API: solo aditivos
  y nullable (expand-contract) — renombrar/borrar/cambiar tipo rompe apps
  instaladas.
- La validación de entrada del backend es declarativa (`@Valid` + `@Size`):
  todo String que acaba en columna lleva tope.

## 6. Ramas, entornos y proceso

No se repiten aquí: están en [COMO_TRABAJAMOS.md](COMO_TRABAJAMOS.md).
