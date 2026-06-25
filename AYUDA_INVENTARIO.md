# Inventario de pantallas para la AYUDA contextual (Cumbre)

> Estudio exhaustivo de TODO lo que hace cada pantalla, para diseñar las hojas de
> ayuda "?" por sección y los coach-marks de primera vez. Cada pantalla lista
> **qué puede hacer el usuario** (lenguaje de usuario = copy de la ayuda) y marca
> con ⭐ los **gestos no obvios** (candidatos a coach-mark/destacar en la ayuda).
> Referencias de código entre paréntesis.

---

## A. NAVEGACIÓN GENERAL (`MainScreen.kt`)
- **Barra inferior**: 3 pestañas — **Tiempo**, **Escuelas**, **Radar**.
- **Iconos del header** (en Escuelas/Tiempo): 🔍 buscar usuarios · 💬 chats (con badge de no leídos) · 🔔 notificaciones (con badge) · 🌙/☀️ modo claro/oscuro · 👤 perfil.
- Las pantallas tipo "hoja" (perfil, chat, notificaciones, comparar, día…) suben como **sheet** y se navegan deslizando; el detalle de escuela y admin son pantalla completa.

---

## B. ESCUELAS (lista) — `SchoolListScreen.kt`
**Qué puedes hacer:**
- Ver todas las escuelas con su **índice 0-100** del día (color: verde bueno / ámbar / rojo).
- **Buscar** por nombre.
- **Filtrar** por: DISTANCIA (50–500 km), ESTILO (Todas/Bloque/Vía), TIPO DE ROCA (multiselección + "Todas" limpia), MOSTRAR (Todas/Favoritos/Guardados), ORDENAR (Mejor score / Más cercanos).
- ⭐ **Elegir hasta 5 días** (chips de día) para comparar un **tramo**: el badge pasa a score combinado + marca los días de lluvia.
- ⭐ **Estrella** en cada fila → marcar **favorita** (funciona sin conexión).
- ⭐ **Mantener pulsada** una escuela → entrar en **modo comparar** (hasta 3); abajo sale "N SELECCIONADAS · COMPARAR". *(NOTA: en iOS NO funciona — gap a corregir.)*
- **Tirar hacia abajo** (pull-to-refresh) para recargar.
- **+ Enviar escuela** (arriba) → proponer una escuela nueva.
- **VER MAPA** (toggle): abre el mapa de la lista (ver C).
- Estado vacío con "QUITAR FILTROS"; offline pinta lo cacheado.

## C. MAPA DE LA LISTA — `SchoolsMapPanel.kt`
**Qué puedes hacer:**
- Ver las escuelas como **diamantes coloreados por score** (nombre visible al acercar zoom).
- ⭐ Toggle **Topográfico / Satélite**.
- **Tocar un diamante** → tarjeta con nombre, score, tags (roca/estilo/distancia) y botones **CÓMO LLEGAR** (Google Maps) y **VER DETALLE ▸**.
- Punto azul = tu ubicación.

---

## D. DETALLE DE ESCUELA — `SchoolDetailScreen.kt`
**Barra superior:** ← atrás · 🗺 CÓMO LLEGAR · ➤ COMPARTIR · ⭐ favorito · ⭐ **GUARDAR OFFLINE** (descarga escuela+bloques+previsión+tiles para verla sin conexión).
**Avisos:** banner "SIN CONEXIÓN · datos del [fecha]" y "PREVISIÓN DE hace Xh · REINTENTAR".

### D1. Previsión (`ForecastBody.kt`)
- **Veredicto** grande SÍ/NO + **ÍNDICE /100**.
- ⭐ **"¿POR QUÉ ESTE ÍNDICE?"** → despliega los factores (temp, humedad, viento, nubes…).
- Banda **ROCA SECA/HÚMEDA** + tiempo de secado estimado.
- **Tiempo actual** (temp, cielo, viento, humedad).
- ⭐ Grid **"PRÓXIMAS 16 HORAS"** (`HourlyScoreGrid`): hora, icono, score, temp, lluvia, viento.
- Cuadrícula de **8 condiciones** (humedad, viento, lluvia 24/72h, nubes, rocío, prob. lluvia, roca).
- **PRÓXIMOS 7 DÍAS** + **★ MEJOR DÍA**.

### D2. Mapa de la escuela (`SchoolMap.kt`)
- Toggle **Topográfico / Satélite**.
- Markers: **P** azul = parking · piedra (polígono terra) · **Z** verde = sector/zona · triángulo = la escuela · línea terra = **muro**.
- ⭐ **Tocar una ZONA (Z)** → colapsa/expande las piedras de ese sector.
- **Tocar una piedra/parking/muro** → abre su ficha (ver D3).
- ⭐ **+ PROPONER** (abajo dcha) → añadir parking/piedra/sector o corregir posición (ver D4).

### D3. Ficha de piedra / muro (`BlockDetailDialog.kt`)
**Qué puedes hacer:**
- Ver la(s) **foto(s)/caras** con las **vías dibujadas** encima.
- Lista de **vías**: número, grado (color por grado), tipo de inicio (PIE/SIT/LANCE/TRAV), nombre.
- ⭐ **Marcar una vía como hecha** (○ → ✓): la añade a tu **diario** (funciona offline). Vuelve a tocar para desmarcar.
- **→ CÓMO LLEGAR**.
- ⭐ **+ AÑADIR VÍAS** / **✎ EDITAR / CORREGIR VÍAS**.
- ⭐ **+ ASIGNAR / CAMBIAR SECTOR**.
- Solo admin: **✎ EDITAR** (mover/renombrar) y **🗑 BORRAR**.
- Diferencia **PUNTO** (piedra) vs **MURO** (línea con numeración a lo largo).

### D4. Proponer / Editar (`ProposeContributionFlow.kt`, `AddLinesFlow.kt`)
**Proponer (+ PROPONER):**
- Elegir tipo: **PARKING / PIEDRA / SECTOR / CORREGIR posición**.
- ⭐ **Pulsar en el mapa** para fijar las coordenadas (banner "PULSA EN EL MAPA").
- PIEDRA: elegir modalidad **BLOQUE/VÍA**, añadir **caras (fotos)** y **vías** (nombre+grado+tipo), y ⭐ **dibujar las líneas arrastrando** sobre la foto.
- ⭐ MURO: **geometría MURO** + **trazar el muro** tocando puntos en el mapa (DESHACER/LISTO) + **sentido de numeración** (izq→der / der→izq) + **reordenar caras/vías**.
- CORREGIR: tocar el marker a mover → tocar la nueva posición → ACEPTAR.
- Final: "PROPUESTA ENVIADA · la revisa un admin" (si eres admin, se publica al instante).

**Editar vías/muro (`AddLinesFlow`):** + AÑADIR FOTO, reordenar fotos, re-trazar el muro, editar/añadir/borrar vías.

### D5. Notas (`NotesSection.kt`)
- Leer notas de la comunidad (con foto si tiene; tap → foto a pantalla completa).
- ⭐ **Publicar una nota** (texto + 📷 foto opcional).

---

## E. TIEMPO — `WeatherScreen.kt`
**Qué puedes hacer:**
- Ver la **previsión de tu ubicación** actual.
- ⭐ Chips arriba: alternar entre **Ubicación** y cada **escuela favorita**.
- **Grid de comparación de favoritas** (score medio por día de la semana).
- **Tocar un día** → detalle de ese día.
- Si falta permiso: botón **DAR PERMISO**.

## F. RADAR — `RadarScreen.kt`
- Radar de lluvia embebido (Windy). Pinch para zoom, arrastrar para mover.

---

## G. PERFIL (Cuenta) — `ProfileScreen.kt`
**Qué puedes hacer:**
- Ver tu **avatar, nombre, @usuario, email** y píldoras **TOPE [grado] / ADMIN / PREMIUM**.
- ⭐ **Seguidores / Siguiendo** (tappables) → sus listas.
- ⭐ Stats tappables: **BLOQUES / VÍAS / ESCUELAS** y **MÁX BLOQUE / MÁX VÍA** → abren tu diario filtrado.
- ⭐ **+ AÑADIR BLOQUE** (registrar vía/bloque a mano).
- Menú: **Editar perfil**, **Escuelas guardadas (offline)**, **Alerta de tiempo**, **Mis propuestas**, **Solicitudes de seguimiento** (si privado), **Panel admin** (con aviso de pendientes).
- **Cerrar sesión** y **Eliminar cuenta**.
- Offline: banner "SIN CONEXIÓN · datos guardados".

### G1. Editar perfil (`EditProfileScreen.kt`)
- ⭐ **Tocar la foto** → cambiarla (recorte circular).
- Campos: **@usuario**, **nombre**, **bio** (máx 150), grado máx (automático, solo lectura).
- ⭐ Switch **Perfil público** (otros te ven por @usuario).

### G2. Diario (`JournalEntriesScreen.kt`)
- Filtros **BLOQUES / VÍAS**.
- Cada entrada: fecha, grado, nombre de vía, "Escuela · Piedra N · Sector".
- ⭐ **Tocar una vía** → abre su **piedra** en la escuela (deep-link).
- **Borrar** entrada (solo las tuyas). Vías borradas del catálogo salen en gris "VÍA ELIMINADA".

### G3. Otras del perfil
- **Escuelas guardadas (offline)** (`SavedSchoolsScreen`): abrir / **✕ eliminar** descargas.
- **Mis propuestas** (`MySubmissionsScreen`): estado de cada propuesta (Pendiente/Aprobada/Rechazada + **motivo** si rechazada).
- **Proponer escuela** (`SubmitSchoolScreen`): nombre, región, estilo, roca, ⭐ **pegar coordenadas** (autorrellena lat/lon), ubicación, notas.
- **Alerta de tiempo** (`WeekendAlertScreen`): switch general; ⭐ **"ventana óptima hoy"** + umbral 60/70/80; elegir **días a comparar**, día y hora del aviso; **MIS ESCUELAS** (hasta 3) o **POR CERCANÍA** (radio 25–200 km).

---

## H. SOCIAL
### H1. Perfil público de otro (`PublicProfileScreen.kt`)
- ⭐ **Seguir / Siguiendo / Solicitado** y **MENSAJE**.
- Sus stats y diario (tappables). Si es privado y no le sigues: "Perfil privado".

### H2. Buscar usuarios (`SearchUsersScreen.kt`)
- Buscar por @usuario o nombre → tocar para ver su perfil.

### H3. Seguidores / Siguiendo (`FollowListScreen.kt`)
- Por fila: **Seguir/Siguiendo/Solicitado**; en MI lista de seguidores además **Eliminar**.

---

## I. CHAT
### I1. Lista de chats (`ChatListScreen.kt`)
- ⭐ **✎ Nuevo mensaje** (busca entre seguidores/seguidos) y **+ Nuevo grupo**.
- ⭐ **Deslizar** una conversación: **izquierda = borrar**, **derecha = marcar no leída**.
- Badge de no leídos; **tocar el nombre** → perfil del otro.

### I2. Conversación (`ChatScreen.kt`)
- Escribir y **enviar** (funciona offline en conversaciones existentes; el mensaje aparece al instante y se entrega al reconectar).
- ⭐ **Deslizar una burbuja a la derecha** → **responder citando** ese mensaje.
- **Tocar el nombre** arriba → perfil del otro.

### I3. Grupos (`GroupChatScreen.kt`, `NewGroupScreen.kt`)
- Crear grupo: **nombre** + elegir **miembros**. En el grupo, cada mensaje muestra el **autor**.

---

## J. NOTIFICACIONES — `NotificationsScreen.kt`
- **Marcar leídas** / **Borrar todas** (con confirmación).
- ⭐ **Deslizar** una notificación → borrarla.
- **Tocar** una notificación → te lleva a lo que toca (chat, perfil, solicitud, propuesta…).

---

## K. ADMIN (solo admin) — `AdminScreen.kt`
- 5 pestañas: **PROPUESTAS** (revisar/aprobar/rechazar, filtros por tipo, mini-mapa, VER EN MAPA), **GESTIONAR** (buscar escuela → mapa → editar/mover/borrar bloques), **STATS**, **ACTIVIDAD** (logs), **PUSH** (envío manual).

---

## GAPS / A CORREGIR detectados en el estudio
1. ⭐ **iOS no tiene "mantener pulsado para comparar"** (Android sí) — corregir para paridad.
2. No existe aún ningún sistema de **ayuda "?"** ni **estados vacíos explicativos** ni **coach-marks** — es lo que vamos a construir a partir de este inventario.

---

## Gestos NO obvios (lista corta = candidatos a coach-mark / a destacar)
- Mantener pulsado escuela → comparar.
- Elegir días → comparar tramo.
- Estrella → favorita (offline).
- Guardar escuela offline.
- Tocar el índice → "¿POR QUÉ ESTE ÍNDICE?".
- Tocar ZONA en el mapa → colapsar/expandir piedras.
- + PROPONER (y pulsar en el mapa para coords; trazar muro; dibujar líneas).
- Marcar vía hecha (✓) → diario.
- Deslizar burbuja → responder; deslizar chat → borrar/no leído; deslizar notif → borrar.
- Stats del perfil tappables → diario filtrado.
- Pegar coordenadas en proponer escuela.
