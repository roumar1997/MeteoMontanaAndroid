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
   ViewModel, y el ViewModel a través de use cases. Si un VM inyecta un
   `Ktor*Api`, es deuda marcada (quedan: SchoolList, Radar, Chat, Admin).
2. **El dominio (`shared/domain`) es puro**: sin Ktor, sin Firebase, sin
   tipos de plataforma, sin anotaciones de framework. Las únicas excepciones
   toleradas están documentadas en el propio código (p. ej. `descriptionText`
   por colisión con NSObject en iOS).
3. **Los efectos de plataforma** (MediaStore, cámara, GPS, BLE…) viven en
   `data/local` (Android) o detrás de un puerto con bridge (patrón de
   KMP_MIGRATION.md §22) — nunca inline en un composable.
4. **Backend hexagonal**: los use cases de `application/` dependen de puertos
   de `domain/port`, no de entidades JPA. (El subsistema social/feed viola
   esto hoy — deuda conocida, ver MejorasFuturas.md.) El mapeo excepción→HTTP
   vive SOLO en `GlobalExceptionHandler`; el dominio no conoce HTTP.

## 2. Tamaños y responsabilidad única

- **Un fichero = una razón de cambio.** Si al describir un fichero necesitas
  la palabra "y" más de una vez, toca dividirlo.
- Umbral de alarma: **>600 líneas** en un fichero de UI o **>400** en un
  servicio de backend → pararse y preguntarse qué responsabilidades mezcla.
- Un composable/función con **>12 parámetros** está pidiendo un state-holder
  (`@Stable` class con `mutableStateOf`, como `ProposalMapBridge`).

## 3. El módulo del mapa de escuela (refactor 2026-07-19)

Referencia de cómo queremos organizar features grandes — y espejo 1:1 con iOS:

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

## 4. Paridad Android ↔ iOS

- iOS replica Android **verbatim** (pantallas, textos, orden, colores).
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
- Todo bug de producción deja un **test de regresión** con el porqué en el
  comentario (patrón: JournalViaKeyTest, FeedContractTest).
- Los CONTRATOS backend↔apps tienen test en los DOS lados con la misma
  muestra dorada (FeedContractTest .java/.kt). Cambios de API: solo aditivos
  y nullable (expand-contract) — renombrar/borrar/cambiar tipo rompe apps
  instaladas.
- La validación de entrada del backend es declarativa (`@Valid` + `@Size`):
  todo String que acaba en columna lleva tope.

## 6. Ramas y entornos (fase "dejar perfecta", desde 2026-07-19)

- Trabajo diario → rama **`develop`** de los dos repos. CI la vigila.
- `main` = lo publicado en tiendas. **Nada a main/prod** sin validar en
  staging (espejo sanitizado de prod) y sin OK explícito de Rodrigo.
- Apps debug → staging; release → producción. Ver CLAUDE.md §Entornos.
