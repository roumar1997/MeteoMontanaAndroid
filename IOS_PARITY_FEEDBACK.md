# iOS — Feedback de paridad y mejoras pendientes

> Documento VIVO. Objetivo: la app iOS debe ser **EXACTAMENTE IGUAL** que
> Android. Aquí está, por cada feature: **qué debe hacer** (spec Android) y el
> **feedback de Rodrigo** con su estado. Al trabajar un punto, actualizar su
> estado aquí. Si se acaban los tokens, la siguiente sesión sigue desde aquí.
>
> Estados: ⬜ pendiente · 🔧 en progreso · ✅ hecho (a validar por Rodrigo) ·
> 🟦 necesita bridge nativo (mapas/foto/chat) → sesión con Mac.
>
> Ronda de feedback: **2026-06-16** (tras primer `.ipa` con paridad masiva).

---

## Cuenta / Perfil

### 1. Login al arrancar — ✅ OK ("perfecto")

### 2. Perfil real (avatar, nombre, bio, grado, badges) — ✅ OK ("me gusta")

### 3. Mi diario — 🔧 REHACER (varias cosas)
**Qué debe hacer (Android `JournalEntriesScreen` + `ProfileScreen`):**
- Al **añadir bloque**: el formulario empieza eligiendo **escuela**; si esa
  escuela existe en la app, **autocompleta con datos reales** (sectores y vías
  catalogados) — sugiere vías reales (grado+tipo) y al elegir una autocompleta
  el grado. (Hoy iOS es solo campos de texto libres.)
- El perfil/diario permite **navegar por "bloques"** (ver todos los bloques
  registrados) y por **"escuelas"** (ver cada escuela y qué bloques tienes en
  cada una), además del **grado máximo**.
- **Grado tope automático**: el "tope" del perfil se calcula SOLO según tu
  máximo del diario. **BUG actual**: el diario tiene un 7b+ pero el perfil
  muestra "TOPE 6A". El maxGrade no se está calculando/mostrando bien
  (revisar `JournalStats.maxGrade` y cómo lo pinta el perfil; probablemente el
  perfil muestra `PrivateProfile.topGrade` editable en vez del max real del
  diario, o el orden de grados está mal).
- Quiere ver **seguidores y seguidos** desde el perfil (en iOS ya existe
  `FollowListView` para perfiles públicos, pero falta acceso desde el perfil
  propio — añadir contadores tappables en `AccountView`).

**Estado:** ✅ hecho (a validar):
- **Autocompletado del diario**: `AddBlockSheet` reescrito con buscador de
  escuela (SearchSchools), sugerencias de SECTOR (ZONE catalogados + historial)
  y de VÍAS reales (grado+tipo) de los bloques de la escuela; al elegir una vía
  autocompleta el grado. Grado por menú (lista fija de grados). Se expuso
  `GetBlocksUseCase` en `IosDependencyContainer` (+ `KtorBlockRepository`).
- **maxGrade arreglado**: el badge "TOPE" del perfil ahora usa
  `JournalStats.maxGrade` (máximo REAL del diario), no el `topGrade` editable.
- **Navegación bloques/escuelas**: `AccountView` añade una stats row
  (BLOQUES→JournalView · ESCUELAS→`JournalSchoolsView` · MÁXIMO) tappable.
- **Seguidores/Seguidos**: contadores tappables en `AccountView`
  (getFollowStatus del propio uid) → `FollowListView`.

### Perfil público vs privado — ⬜ explicar + implementar
**Qué hace en Android:** el flag `isPublic` del perfil controla la
privacidad. Si tu perfil es **público**, cualquiera puede ver tu perfil, tu
diario y tus stats, y al seguirte el follow es inmediato. Si es **privado**,
tu perfil sale **bloqueado** (`PublicProfile.locked`) para quien no te sigue, y
al pulsar "Seguir" se manda una **solicitud** (`FollowStatus.requestPending`,
estado "SOLICITADO") que tú aceptas/rechazas en "Solicitudes de seguimiento".
- En iOS: el use case `UpdateProfileRequest.isPublic` existe pero el toggle NO
  está en `EditProfileView` (se omitió por el `Boolean?` boxed de SKIE).
  Falta: toggle público/privado + respetar `locked` en `PublicProfileView`.

**Estado:** ✅ hecho (a validar): toggle "PERFIL PÚBLICO" en `EditProfileView`
(se pasa `KotlinBoolean(bool:)` a `UpdateProfileRequest.isPublic`) con texto
explicativo de público/privado; `PublicProfileView` respeta `locked` (perfil
privado bloqueado: solo nombre/avatar + candado + botón para solicitar seguir,
oculta bio/grado/stats).

---

## Lista de escuelas

### 1. Filtros (distancia/favoritas/orden) — 🔧 colocación
**Feedback:** "me gusta, pero fíjate cómo está colocado en Android para su
mejor uso." → Revisar `SchoolFiltersBar.kt` y replicar la **disposición/orden
exacto** de los filtros (en iOS están todos en una fila de chips; Android los
agrupa de cierta forma — secciones/orden). Clavar el layout.
**Estado:** ✅ hecho (a validar): `FilterChips` de iOS reescrito como secciones
apiladas igual que `SchoolFiltersBar.kt` — eyebrow + fila de chips por sección,
en el orden DISTANCIA · ESTILO · TIPO DE ROCA · FAVORITOS · ORDENAR POR.

### 2. Distancia "· N KM" — 🔧 default 50 km
**Feedback:** "me gusta lo de la distancia, y que al abrir la app esté en
**50 km por defecto**." (Hoy iOS arranca con distancia = Todas; Android por
defecto 50 km como la PWA.) → Cambiar `maxDistanceKm` inicial a `50.0`.
OJO: si no hay permiso de ubicación aún, no esconder todo — pedir ubicación.
**Estado:** ✅ hecho — `maxDistanceKm` inicial = 50; el filtro de distancia solo
aplica si hay ubicación (si no, muestra todas), así no queda vacío sin permiso.

### 3. Estrella favorita en lista — ✅ OK

### 4. Comparar escuelas — 🔧 BUG de gesto (alta prioridad; Rodrigo lo quiere
también en Android)
**Qué debe hacer:** mantener pulsada una escuela entra en **modo selección**
(borde terra), tocar otras las añade/quita (máx 3), barra inferior "N
SELECCIONADAS · COMPARAR" abre la comparativa. En modo selección, un tap NO
debe navegar al detalle.
**BUG actual:** al mantener pulsado y soltar, **entra en el detalle de la
escuela** en vez de seleccionarla para comparar (el `NavigationLink` gana al
`LongPressGesture`). → Arreglar: cuando hay selección activa, el tap toggla en
vez de navegar; el long-press no debe disparar la navegación. Probablemente
quitar el `NavigationLink` y navegar programáticamente, o usar
`.highPriorityGesture`/estado de modo-selección.
**Estado:** ✅ hecho — quitado el `NavigationLink`; ahora es `Button` +
`navigationDestination(item:)`. Tap: si hay selección activa togglea, si no
navega. Long-press entra en selección. **A validar por Rodrigo.** (Pendiente:
portar la mejora de comparar a Android.)

### 5. Modo oscuro — ✅ OK
### 6. Badge notificaciones — ✅ OK
### 7. Donate dialog — ✅ OK

---

## Detalle de escuela

### 1. Hero / veredicto / índice — ✅ OK

### 2. Tildes salen como "??" — 🔧 BUG encoding (alta prioridad)
**Problema:** en alguna(s) cadena(s) del detalle las tildes/ñ aparecen como
"??" (p. ej. "PRÓXIMAS", "MAÑANA", etc.). → Revisar de dónde viene: puede ser
(a) texto que llega del backend mal codificado, o (b) un string en el código
iOS guardado sin UTF-8, o (c) cómo se renderiza. Localizar el/los textos
afectados y corregir (asegurar UTF-8 en los `.swift` y/o en la respuesta).
**Investigado 2026-06-16**: los `.swift` SÍ están en UTF-8 correcto
(SchoolDetailView.swift verificado: "PRÓXIMAS", "ÍNDICE", "ROCÍO" bien). Por
tanto el "??" NO viene de los literales del código iOS → viene del **backend**
(o del cliente Ktor decodificando mal la respuesta). Sospechas: (a) algún campo
del backend (scoreLabel, factor.display, factor.name, drying.message, region)
con acentos corruptos; (b) Ktor cayendo a ISO-8859-1 si la respuesta no trae
charset=utf-8 en Content-Type. **Siguiente paso**: que Rodrigo diga QUÉ texto
exacto sale con "??" (¿una etiqueta fija o un dato de escuela/factor?). Si es
dato del backend, mirar `MeteoMontanaAPI`; si es del cliente, forzar UTF-8 en
`ApiHttpClient`.
**Mitigación 2026-06-16**: añadido fallback UTF-8 en el cliente Ktor
(`ApiHttpClient.kt` → bloque `Charsets { responseCharsetFallback = UTF_8 }`).
Si el "??" venía de que el backend no mandaba `charset=utf-8` y Ktor caía a
ISO-8859-1, esto lo arregla. Si los bytes ya están corruptos en la BD (como
pasó con la V20 — "Alcañiz→Alca??z"), haría falta una migración en el backend.
**Estado:** 🔧 mitigado en cliente — validar en el próximo `.ipa`. Si persiste,
Rodrigo dice el texto exacto y se mira si es dato de BD (→ migración backend).

### 3. Condiciones: % lluvia sí, mm no — 🔧 BUG
**Feedback:** sale el % de probabilidad de lluvia pero **los mm no** (ya
probado). → Revisar la celda de condiciones / la fila horaria: mostrar los mm
de precipitación (`precipitation` / `precip24h`) correctamente. Comparar con
`ForecastBody.kt` / `ConditionsGrid` de Android.
**Estado:** ✅ hecho — `ConditionsGrid` ya mostraba mm (LLUVIA 24H/72H). Faltaban
los mm **por hora**: añadidos en el grid de 16h (`HoursGrid`) y en las filas
hora-a-hora de `DayDetailView`. **A validar.**

### 4. "CÓMO LLEGAR" → debe ser botón COMPARTIR — 🔧 cambiar
**Feedback:** no le gusta "CÓMO LLEGAR" ahí. En su lugar debe haber un
**botón de compartir** (como Android: comparte la escuela/condiciones con
alguien). El "CÓMO LLEGAR" llegará **cuando se añadan los mapas**.
**Qué hace Android:** botón compartir genera una imagen/tarjeta de condiciones
(`ShareConditionsImage.kt`) o texto y la comparte por el share sheet.
**iOS:** sustituir `DirectionsButton` del detalle por un botón Compartir
(`UIActivityViewController` / `ShareLink` de SwiftUI) con texto de condiciones
(imagen como mejora posterior).
**Estado:** ✅ hecho — `ShareConditionsButton` (ShareLink con resumen de texto)
sustituye al "CÓMO LLEGAR" en el detalle. `DirectionsButton` se conserva para
reusarlo con los mapas. Mejora futura: compartir IMAGEN-tarjeta (como
`ShareConditionsImage.kt`). **A validar.**

---

## Pendiente de bridges nativos (sesión con Mac) — 🟦
- Mapas (MapLibre): mapa de escuela, panel mapa en lista, "+ PROPONER", topo.
- Subir fotos (Firebase Storage): foto de perfil, foto en notas, proponer.
- Chat (Firestore): lista de chats + conversación.
- Push notifications (FCM token).
- "CÓMO LLEGAR" se reactiva con los mapas.
