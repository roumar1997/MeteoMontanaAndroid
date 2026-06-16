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

**Estado:** ⬜ pendiente (autocompletado escuela/vías necesita exponer
GetBlocks/sectores; navegación bloques/escuelas usa `JournalStats.bySchool`;
arreglar maxGrade; añadir followers/following al perfil propio).

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

**Estado:** ⬜ pendiente (añadir toggle isPublic en editar perfil; ocultar
contenido en perfiles privados bloqueados).

---

## Lista de escuelas

### 1. Filtros (distancia/favoritas/orden) — 🔧 colocación
**Feedback:** "me gusta, pero fíjate cómo está colocado en Android para su
mejor uso." → Revisar `SchoolFiltersBar.kt` y replicar la **disposición/orden
exacto** de los filtros (en iOS están todos en una fila de chips; Android los
agrupa de cierta forma — secciones/orden). Clavar el layout.
**Estado:** ⬜ pendiente.

### 2. Distancia "· N KM" — 🔧 default 50 km
**Feedback:** "me gusta lo de la distancia, y que al abrir la app esté en
**50 km por defecto**." (Hoy iOS arranca con distancia = Todas; Android por
defecto 50 km como la PWA.) → Cambiar `maxDistanceKm` inicial a `50.0`.
OJO: si no hay permiso de ubicación aún, no esconder todo — pedir ubicación.
**Estado:** ⬜ pendiente.

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
**Estado:** ⬜ pendiente. (Y portar la mejora a Android cuando esté.)

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
**Estado:** ⬜ pendiente (identificar qué textos exactos salen con ??).

### 3. Condiciones: % lluvia sí, mm no — 🔧 BUG
**Feedback:** sale el % de probabilidad de lluvia pero **los mm no** (ya
probado). → Revisar la celda de condiciones / la fila horaria: mostrar los mm
de precipitación (`precipitation` / `precip24h`) correctamente. Comparar con
`ForecastBody.kt` / `ConditionsGrid` de Android.
**Estado:** ⬜ pendiente.

### 4. "CÓMO LLEGAR" → debe ser botón COMPARTIR — 🔧 cambiar
**Feedback:** no le gusta "CÓMO LLEGAR" ahí. En su lugar debe haber un
**botón de compartir** (como Android: comparte la escuela/condiciones con
alguien). El "CÓMO LLEGAR" llegará **cuando se añadan los mapas**.
**Qué hace Android:** botón compartir genera una imagen/tarjeta de condiciones
(`ShareConditionsImage.kt`) o texto y la comparte por el share sheet.
**iOS:** sustituir `DirectionsButton` del detalle por un botón Compartir
(`UIActivityViewController` / `ShareLink` de SwiftUI) con texto de condiciones
(imagen como mejora posterior).
**Estado:** ⬜ pendiente.

---

## Pendiente de bridges nativos (sesión con Mac) — 🟦
- Mapas (MapLibre): mapa de escuela, panel mapa en lista, "+ PROPONER", topo.
- Subir fotos (Firebase Storage): foto de perfil, foto en notas, proponer.
- Chat (Firestore): lista de chats + conversación.
- Push notifications (FCM token).
- "CÓMO LLEGAR" se reactiva con los mapas.
