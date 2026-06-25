# Plan — Ayuda / onboarding para el usuario nuevo (Android + iOS)

Basado en [AYUDA_INVENTARIO.md](AYUDA_INVENTARIO.md). Tres capas, de mayor a menor
prioridad. Fuente de copy ÚNICA y compartida para garantizar paridad.

## Arquitectura (clave: una sola fuente de verdad)
- **`shared/commonMain` → `help/HelpCatalog.kt`**: catálogo de contenidos de ayuda
  como datos puros Kotlin (`HelpTopic` por pantalla, con `HelpItem`s). Lo usan
  Android e iOS → mismo texto en ambas plataformas, se edita en un sitio.
- **Android**: `ui/components/HelpSheet.kt` (ModalBottomSheet) + botón "?" reutilizable.
- **iOS**: `HelpSheet.swift` + botón "?" en la toolbar.

## Fase 1 — Hojas de ayuda "?" por pantalla  ← EMPEZAMOS AQUÍ
Un icono "?" en las pantallas clave abre una hoja con "Qué puedes hacer aquí".
Pantallas con "?": Escuelas (lista), Detalle de escuela, Perfil, Chats, Tiempo,
Proponer, (Diario/Mapa heredan del detalle/perfil).
- Bajo demanda, cero molesto, descubrible.

## Fase 2 — Estados vacíos que enseñan
Reescribir los estados vacíos para que expliquen + tengan acción:
- Diario vacío, Escuelas guardadas vacío, Chats vacío, Notificaciones vacío,
  Mis propuestas vacío, Seguidores/Siguiendo vacío.

## Fase 3 — Coach-marks de primera vez (solo gestos no obvios)
Overlay que sale UNA vez (persistido) sobre 4-5 gestos: mantener pulsado=comparar,
elegir días=tramo, marcar vía ✓=diario, guardar offline, "¿por qué el índice?".
- Más trabajo; va al final.

## Estado
- [x] Inventario completo (AYUDA_INVENTARIO.md)
- [x] Fix iOS long-press comparar
- [ ] Fase 1: catálogo shared + HelpSheet Android + iOS + "?" en pantallas clave
- [ ] Fase 2: estados vacíos
- [ ] Fase 3: coach-marks
