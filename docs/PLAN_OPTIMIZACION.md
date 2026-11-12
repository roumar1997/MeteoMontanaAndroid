# Plan de optimización (después de la 7.2)

Auditoría del 2026-11-10, solo lectura (Chopper = Android, Robin = iOS). S/M/L = esfuerzo.

## Fase 1 — Lo que más se nota

| # | Plataforma | Qué | Dónde | Esfuerzo |
|---|---|---|---|---|
| 1 | iOS | La guía se recalcula entera en cada pintado (5 pasadas por todas las vías) → guardarla y recalcular solo si cambian datos/filtro | `SchoolBlocksExplorerModel.swift:83-90` | M |
| 2 | iOS | «Sube y baja»: `GuideTopo` reserva 180 y luego crece; `GuideHeroPhoto` salta; `withAnimation` en todo el sector → altura final fija + animar solo la flecha | `SchoolGuideView.swift:60,127,452` | S |
| 3 | Android | Ficha y guía en `Column+verticalScroll` (se pinta TODA la escuela) → LazyColumn con keys; arregla también el salto de `jumpToSector` (delay 160 ms) | `SchoolDetailScreen.kt:337`, `SchoolGuideSectors.kt:182`, `SchoolBlocksTab.kt:129` | L |
| 4 | Android | Sin perfil de arranque (baseline profile) → arranque y primeros scrolls lentos | `app/build.gradle.kts` | M |
| 5 | Ambas | Fotos a tamaño completo sin caché en memoria (Coil sin size; iOS `UIImage(data:)` en hilo principal) → reducir + caché | `ImageCache.swift:26`, 41 `AsyncImage` Android | M |

## Fase 2 — Batería, memoria y arranque

- Android: GPS cada 5 s mientras el mapa vive → `requestLocationUpdates` con ciclo de vida (`UserLocation.kt:33`) · S
- Android: MapLibre y sincronizaciones en `Application.onCreate` → diferir (`MeteoMontanaApp.kt:26`) · S
- Android: `UserBadgeStore` parsea JSON en hilo principal (`UserBadges.kt:65`) · S
- Ambas: hadas/dragones animando a 60-120 fps siempre → 30 fps, pausar fuera de pantalla y con «Reducir movimiento» (`TitleEffectHalo`) · S
- iOS: foto aérea del sector en hilo principal y sin límite en memoria; capturas duplicadas (`GuideOverviewMap.swift:48`, `SchoolGuideView.swift:125`) · S/M
- iOS: temporizador de grabar aproximación no se para al cerrar deslizando (`ApproachRecordView.swift:285`) · S
- Android: radar decodifica todos los fotogramas; avatar de push a tamaño completo (`RadarViewModel.kt:131`, `PushService.kt:107`) · S

## Fase 3 — Fluidez fina y cierres

- Android: listas sin `key` (chat, comunidad, guardadas, quedadas, estadísticas, tiempo); chat anima scroll entero; lista de escuelas reanima todo al llegar notas · S
- Android: salto al marcapáginas cada vez que vuelves a la pestaña (`SchoolBlocksTab.kt:142`) · S
- Android: `!!` que pueden cerrar la app — el más probable `JournalSectorsScreen.kt:118` (vía borrada); `PdfFinisher.kt:40`, `AssistantViewModel.kt:457`, `SectorHoursSection.kt:164` · S
- iOS: `AsyncImage` sin caché en quedadas, notas, perfil → un `CachedImage` común · S/M
- iOS: cálculos en el pintado (`sectorIntro`, restricciones) y modelos con muchos `@Published` → submodelos / `@Observable` · S–L

## Cómo medir que mejora
- Android: Macrobenchmark (arranque, lista, ficha, guía) antes/después + Maestro sin ANR.
- iOS: Instruments (Time Profiler) en una escuela grande abriendo sectores en la guía.
