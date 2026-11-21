# Rocódromos: notas de paridad para la fase iOS

> Estado: 2026-11-19. **iOS hecho (fase C, sin compilar en Mac: lo valida ios-ci; ver tabla al final)**. Android v1 hecho (rama `feature/rocodromos-v1`, sin subir). iOS debe quedar **igual** en pantallas,
> textos y colores. Textos: `TEXTOS.md` (mismas claves, mismos signos). Iconos: `icons/*.svg` (originales con
> `currentColor`, trazo 2, extremos redondeados, caja 24; en iOS van como assets vectoriales). Lógica común: `shared`
> (`GymsModule` ya colgado de `IosDependencyContainer.gyms`). **No reimplementes en Swift** nada de lo que aparece como
> «sale de shared».

## 0. Interruptor y capas

- Todo está detrás de `gyms_enabled` (`FeatureFlags.isGymsEnabled`): una clave que el servidor no manda cuenta como
  **apagada**. Apagado: no existe el selector, ni el Plan B, ni «Crear quedada aquí»; Escuelas es la de siempre.
- Android lee el interruptor con `LocalFeatureFlags.current.isGymsEnabled` (se refresca en cada `ON_START`).
- Todas las peticiones llevan `X-Client-Features: gyms` (ya en `ApiHttpClient`).

## 1. Escuelas: selector «Roca · Rocódromos · Muros gratis»

- Va **debajo de la cabecera** (iconos y «Aportar») y **encima** del buscador/mapa de escuelas. Tres cápsulas `CumbreChip`
  (terracota la elegida, papel + borde `outline` las demás), texto mono en mayúsculas (`ROCA`, `ROCÓDROMOS`, `MUROS GRATIS`).
  Si no caben en una línea **pasan a la siguiente** (FlowRow): no se cortan en pantalla estrecha ni con letra grande.
- «Roca» deja Escuelas exactamente como hoy. «Rocódromos» y «Muros gratis» **sustituyen todo el cuerpo** de Escuelas (se
  ocultan escuelas, buscador de vías, mapa de escuelas, filtros de roca y su nota del tiempo); la cabecera se queda.
- La capa elegida vive en el ViewModel (no se guarda entre arranques; arranca en «Roca»). Cambiar de capa **quita**
  «Gratis» y el filtro de cadena y pone el modo (`GymMode.ROCODROMOS` = comercial + municipal; `FREE_WALLS` = muro gratis).
- El catálogo se pide **la primera vez** que se elige una capa de rocódromos (copia local al instante, luego la red).

## 2. Capa de rocódromos (mapa y lista)

Orden vertical: buscador → cápsulas de filtro (scroll horizontal) → fila «contador · Mapa | Lista» → separador → contenido.

- **Buscador** `CumbreSearchField`, placeholder `gym_search_hint`. Filtra por nombre, ciudad, provincia y cadena sin
  tildes (`GymFilterEngine`, shared).
- **Cápsulas**, en este orden: `Gratis` (se oculta en «Muros gratis»), `Bloque`, `Cuerda`, `Abierto ahora`, `Favoritos`,
  `Cadena` (menú con «Todas las cadenas» + las cadenas del modo; muestra el nombre elegido) y `Distancia` (menú:
  «Sin límite», 10, 25, 50, 100 km; muestra «N km»). Una ficha con el dato **desconocido no entra** cuando el filtro lo exige.
- **Contador** en mono mayúsculas (`gym_count_gyms` / `gym_count_free`). **Mapa | Lista** son dos cápsulas; arranca en **Mapa**.
- **Orden** de la lista: por distancia si hay ubicación, si no por nombre (shared). Sin ubicación no hay distancia ni filtro.
- Estados: cargando (indicador), sin red **y** sin copia (`gym_load_error` + «Reintentar»), lista vacía (`gym_empty` +
  «Quitar filtros»). Con copia local y sin red se ve la copia (no hay error). Pull-to-refresh: **no** hay (no lo pide la maqueta).
- **Fila de la lista** (`GymListRow`): icono del tipo 52 (radio 12) · nombre serif 18 negrita (2 líneas) · «Cadena · Dirección»
  (o ciudad) · etiquetas (modalidad, De pago/Gratis, estado de apertura; **sin** etiqueta de tipo ni de cadena porque ya
  las dicen el icono y el subtítulo) · distancia a la derecha y estrella terracota si es favorito. Separador de 1 dp.
  Al final de la lista, la píldora discontinua «Añadir un muro que falta».
- **Mapa**: marcadores por tipo y agrupación **de shared** (`GymClusterer`, rejilla de 64 px, sin agrupar desde zoom 13).
  Al tocar un grupo, la cámara abarca a sus miembros; al tocar el mapa vacío se cierra la tarjeta. Encuadre: la
  primera vez, centrado en el usuario a zoom 9,5 (o España a zoom 5 sin ubicación); después, al cambiar **qué fichas
  se ven** (filtro/capa) se encuadran todas con margen de 48 dp. Botón de ubicación arriba a la derecha (pide permiso si falta).
  Estilo del mapa: papel claro (OSM) o oscuro según el tema, sin selector de satélite.
- **Marcadores**: círculo de 38 (46 si está seleccionado, con aro del color al 33 %) con borde blanco de 3 y sombra, y dentro el
  icono en blanco: comercial `ic_gym` (terracota), municipal `ic_municipal` (azul), muro gratis `ic_free_wall` (verde) con la
  etiqueta «GRATIS» debajo (píldora verde con borde blanco, 9 mono negrita). La punta del marcador es la **base** del icono.
  Grupo: círculo de 40 (48 con 100+) en tinta de marca con el número en blanco.
- **Tarjeta de cristal** al tocar un marcador (`GymMapCard`): misma que sector/parking (radio 24, fondo del tema al 92 %,
  canto blanco al 55 %, sombra 12). Fila: icono del tipo 52 · nombre serif 21 · etiquetas (tipo, cadena, modalidad, precio,
  estado) · distancia · botón ✕. Debajo, «Cómo llegar» (primario, terracota, con `ic_directions`) y «Ver ficha». Con la
  tarjeta cerrada, abajo del mapa sale la píldora «Añadir un muro que falta». Todo deja libre la cápsula de pestañas
  (`LocalTabBarInset`).

## 3. Colores (tokens, nunca hex sueltos)

| Tipo | Relleno (blanco encima) | Texto/borde | Fondo suave |
|---|---|---|---|
| Comercial | `TerraFill` / `TerraFillDark` | `Terra` / `TerraDark` | texto al 14 % |
| Municipal | `GymMunicipal` `#2563C7` / `GymMunicipalFillDark` `#2B64C4` | `GymMunicipal` / `GymMunicipalTextDark` `#7BA3EE` | texto al 14 % |
| Muro gratis | `GymFree` (= `Ok`) / `GymFreeFillDark` | `Ok` / `OkDark` | texto al 14 % |

Contrastes del blanco sobre el relleno: terracota 4,6 (oscuro) a 5,2 (claro) · municipal 5,7 · gratis 6,2. Las etiquetas de **modalidad** usan el
azul municipal; «De pago» el terracota; «Gratis» y «Abierto» el verde; el resto, `onSurfaceVariant`.
En iOS: crear `gymMunicipal` en `CumbreTheme.swift` (claro y oscuro) con esos valores.

## 4. Ficha (`GymDetailScreen`)

Pantalla del overlay (ruta `gyms/{gymId}`; en iOS, un push). De arriba abajo:

1. Fila de dos botones de icono repartidos por todo el ancho: **volver** y **favorito** (estrella; rellena y terracota si lo es).
   Separador de 1 dp.
2. Etiquetas (tipo, cadena, modalidad, precio, estado), **nombre** serif 27, «Dirección · distancia» (sin dirección: «Ciudad, Provincia»).
3. Acciones en una fila de pesos iguales: «Cómo llegar» (primaria), «Web» y «Instagram» solo si la ficha los trae.
   Web: añade `https://` si falta. Instagram acepta `@nombre`, `nombre` o la URL entera.
4. **Modalidad** (etiquetas azules; sin dato: «Sin confirmar»).
5. **Horario** con `ic_clock` (una línea por tramo: «L–V 10:00–23:00»; «24:00» se escribe «00:00»; sin horario:
   «Horario sin confirmar»). Las etiquetas de días (L–V, S–D, S, D, Todos los días) están en `TEXTOS.md`; el resto de
   combinaciones usa los nombres cortos del idioma del sistema.
6. **Precio orientativo** con `ic_price`: «Entrada suelta ~12 €», «Bono 10 ~90 €» y debajo «Revisado el <fecha>» (fecha
   local, formato medio) + «(sin verificar)» si la ficha es `UNCONFIRMED`. **Sin fecha de revisión no se enseña el precio**
   (sale «Sin dato de precio todavía»). Importes: sin decimales si es entero, si no dos con coma (ES) o punto (EN).
   Muro gratis: «Entrada gratuita» (sin fecha).
7. **Servicios**: cuatro filas (icono 20 + nombre + valor a la derecha): Autoseguro, Alquiler de material, Niños,
   Principiantes. Valor: «Sí» / «No» / «Sin confirmar» (**un nulo nunca es «No»**).
8. Fiabilidad en una línea: «Confirmado por la comunidad» (COMMUNITY/VERIFIED) o «Sin confirmar»; si la fuente contiene
   «OpenStreetMap», debajo «© OpenStreetMap» (ODbL).
9. **Quién va hoy**: tarjeta `surface` con radio 14: hasta 4 avatares de 30 solapados + «N en «Aquí» ahora» (o «Nadie ha
   marcado «Aquí» todavía») + botón «Unirme» (terracota) / «Ya no estoy» (neutro). **Unirme** abre el mismo aviso de
   privacidad que «Aquí» en una escuela; «Ya no estoy» actúa directo. Al marcar/quitar se vuelve a pedir la lista.
   No hay chat de rocódromo ni «Aquí» obligatorio.
10. «Crear quedada aquí» (neutro, ancho completo) → formulario de quedada con el rocódromo ya puesto (ver §6).
11. «¿Algo no es así? Corregir»: píldora **discontinua** a ancho completo → hoja de corrección (§5).

No hay foto grande (el modelo no trae foto de rocódromo; la maqueta la enseña como «Foto de la comunidad», pendiente de
una fase posterior). No hay lista de quedadas del rocódromo en la ficha (no hay endpoint por `gymId`; pendiente).

## 5. Hojas de corrección y alta

Hoja modal a altura completa (sin media altura), fondo `cumbreSheetSurface`, contenido con scroll y 16 dp de margen.

- **Corregir datos**: título serif 22, nombre del rocódromo debajo, «¿Qué no es correcto?» (mono mayúsculas) + chips
  multiselección en este orden: Es gratis, Es de pago, Está cerrado, Es de bloque, Es de cuerda, Ubicación mal, Otro;
  «Comentario» (campo de 3+ líneas, radio 14, ejemplo «Ahora hay entrada libre los domingos…»); «Añadir foto» (cápsula;
  con foto elegida pasa a «Cambiar foto» y se marca) + «opcional»; **Enviar** (primario a ancho completo, desactivado
  hasta elegir una opción o escribir un comentario) y debajo «La revisará un admin». Al enviar: «Enviado. La revisará un
  admin.» y la hoja se cierra sola a 1,5 s; si falla: «No se pudo enviar. Inténtalo de nuevo.» en rojo, sin cerrar.
- Debajo, bloque suave terracota con «＋ Añadir un muro que falta» que abre la hoja de alta.
- **Añadir un muro** (también desde la capa de rocódromos, píldora discontinua): tipo en chips (Muro gratis por defecto,
  Municipal, Comercial), modalidad (Bloque, Cuerda; opcional), «Nombre» (una línea, ejemplo «Muro del parque…»),
  «Ubicación: lat, lon» (5 decimales) y «Se coloca en el centro del mapa · se publica tras revisión», foto opcional y Enviar
  (desactivado sin nombre). **El punto** es el centro del mapa si está abierto, si no la ubicación del usuario, si no Madrid;
  desde la hoja de corrección, la ubicación del rocódromo. (Maqueta: «Colocar en el mapa»; v1 no deja arrastrar un pin.)
- Foto: selector de **documentos** del sistema (nunca el selector de fotos ni `READ_MEDIA_IMAGES`: Play lo prohíbe); se
  comprime hornéando la rotación EXIF y se sube primero (`uploadGymReportPhoto`), luego el reporte con la URL.
- Cuota 5 reportes/día en el servidor: el error genérico cubre ese caso (no hay texto propio).
- **Admin: solo iOS** (cola de reportes). Android no lo tiene ni lo tendrá.

## 6. Quedadas en rocódromo

- «Crear quedada aquí» abre el formulario de quedadas con el nombre propuesto «Quedada en <rocódromo>» (`gym_meetup_default_name`),
  el lugar fijo en lugar del buscador de escuela (etiqueta `gym_meetup_place_label`, sin selector) y envía `gymId` (con
  `schoolId` vacío). Sin puntuaciones por día (no hay escuela).
- En la **lista de quedadas**: el lugar sale de `placeName` (si no, `schoolName`) en el eyebrow de la fila y en el detalle; en el
  detalle no sale el botón «ver escuela» (no hay escuela); el mapa de quedadas **ignora** las de rocódromo y la carga de
  puntuaciones salta los `schoolId` vacíos. Compartir usa el mismo nombre de lugar.
- Las versiones ya publicadas no ven estas quedadas (filtro por `X-Client-Features`, ver `COMPAT.md`).

## 7. Plan B

- En la ficha de una **escuela**, justo bajo la tarjeta HOY (y no en la pestaña de piedras), si `ShouldSuggestGymsUseCase`
  dice sí (`gyms_enabled` y `!current.dryRock`: el criterio de roca mojada de siempre, 30 km, 5 resultados) y hay resultados:
  tarjeta `surface` con borde y radio 14: «ROCÓDROMOS CERCA» (eyebrow terracota), «La roca está mojada. Plan B bajo techo.» y
  hasta 5 filas (icono del tipo 34 · nombre serif 16 · distancia). Tocar una fila abre la ficha del rocódromo.
- No sale si no hay resultados, ni con el interruptor apagado.

## 8. Cómo se probó en Android

- Unitarios: `GymsViewModelTest` (capas, filtros, favoritos con deshacer, sin red con/sin copia, orden por distancia),
  `GymPresentationTest` (textos, horario, precio con fecha, servicios, enlaces) y los de `shared` (filtros, horario,
  clusterer, Plan B).
- Capturas (Roborazzi, `GymScreenshotTest`): filas de la lista, tarjeta del mapa, ficha completa y ficha con todo a NULL,
  en claro, oscuro, letra al doble y 360 dp. Las referencias se graban en Linux (CI), como el resto.
- **No probado en un móvil**: mapa, agrupación, hojas, selector de documentos, aviso de «Aquí» ni el Plan B reales.
  Hacerlo con `gyms_enabled` encendido contra staging.

## 9. Deuda anotada

- Falta la foto de la comunidad en la ficha y la lista de quedadas de ese rocódromo (necesita endpoint).
- El horario se interpreta siempre en hora de Madrid (`GymOpeningHours`); un rocódromo en Canarias saldría con una hora de desfase.
- La agrupación del texto de días de `hoursLines` vive en Android (`GymPresentation`); si iOS la necesita idéntica, pasarla a
  `shared` con las etiquetas inyectadas.

## 10. Estado iOS (fase C)

| Punto | Estado | Dónde |
|---|---|---|
| Selector Roca / Rocódromos / Muros gratis (oculta escuelas y su nota) | hecho | `Gyms/GymsLayerView.swift`, `SchoolListView.swift` |
| Interruptor `gyms_enabled` (apagado = nada de esto existe) | hecho | `FeatureFlagsModel` en selector, Plan B y admin |
| Mapa con marcadores por tipo, agrupación de shared, tarjeta de cristal | hecho (marcador centrado en su punto, no con la base en el punto) | `GymsMapView.swift`, `GymMarkerRenderer.swift`, `GymListRow.swift` |
| Lista con filtros, contador, Mapa/Lista, estados vacío/error | hecho (sin pull-to-refresh propio) | `GymsLayerView.swift`, `GymsViewModel.swift` |
| Ficha completa, favoritos, «Quién va hoy» con aviso de privacidad | hecho | `GymDetailView.swift`, `GymDetailSections.swift` |
| Corregir y Añadir muro (foto con PhotosPicker, 1,5 s) | hecho | `GymReportSheets.swift`, `GymReportViewModel.swift` |
| Plan B bajo la tarjeta HOY de la escuela | hecho | `GymsNearbyBanner.swift`, `SchoolForecastViews.swift` |
| Quedada en rocódromo (crear con gymId, placeName, sin «Ver escuela») | hecho | `CreateMeetupView.swift`, `MeetupRows.swift`, `MeetupDetailView.swift` |
| Admin: reportes (aplicar/rechazar) y editar ficha | hecho (SOLO iOS) | `AdminGymsView.swift`, `GymAdminEditSheet.swift` |
| Lógica común movida a shared (horario agrupado, precio, distancia, enlaces) | hecho; Android la usa | `GymFormatting.kt` |
| Textos ES/EN e iconos | hecho | `Localizable.strings`, `Assets.xcassets/gym_ic_*` |

Diferencias conocidas: el mapa de iOS no recuerda el encuadre al cambiar de capa si se destruye la pantalla de Escuelas; «Mi ubicación» reutiliza el texto ya existente; el mapa muestra el punto azul del usuario como el resto de mapas de iOS.

## 9. Ronda del emulador (2026-11-19, Android hecho; iOS hecho el 2026-11-20, pendiente de validar en el iPhone)

Cambios respecto a las secciones 1 a 4. **iOS ya los lleva** (ver la tabla al final de esta sección).

1. **Selector**: solo `ROCA | ROCÓDROMOS`, dos segmentos de la fila entera con el mismo ancho (mismo control que `ExploreTabSwitcher`). Se quita «Muros gratis». Los muros de calle son el filtro «De calle (gratis)» (`GymFilters.onlyStreet`; `GymMode` borrado de shared) y el tipo se llama **«Muro de calle»** en lista y ficha.
2. **Mapa = el de Escuelas**: satélite por defecto, botón de capas (satélite/topo) y botón de ubicación arriba a la derecha. Nada de estilo propio.
3. **Filtros = los de Escuelas**: orden fijo: buscador, `Mapa | Lista` (segmentado), fila plegable `FILTROS (n) · N rocódromos ⌄` + BORRAR + chip `★ FAVORITOS` (sin chip de días). Panel plegado por defecto con secciones DISTANCIA (mismos valores que Escuelas: Todas, 50…500 km; **arranca en 50 km** y, como Escuelas, un radio sin ningún rocódromo dentro se ignora: `GymFilterEngine.effectiveMaxDistanceKm`), MODALIDAD, MOSTRAR (Gratis, De calle, Abierto ahora, Incluir cerrados) y CADENA. Orden: por cercanía. BORRAR vuelve a 50 km y sin chips. El número de «FILTROS (n)» cuenta lo movido respecto a la base (`gymActiveFilterCount`).
4. **Nombre bajo el marcador**: desde zoom 10,5, texto 11 bold con halo blanco (recortado a 18 letras). Reparto sin solapes: ver `GymLabelPlanner` (un nombre solo sale si su caja no pisa el círculo de otro marcador ni otro nombre; el seleccionado manda). El marcador se baja el alto del nombre para que el círculo siga en su sitio.
5. **Agrupación** (`GymClusterer`, shared): celda 40 px (antes 64) y sin agrupar desde zoom **11** (antes 13). Tocar un grupo encuadra sus miembros.
6. **Favoritos**: chip `★ FAVORITOS` en la fila de filtros (filtra) y estrella tocable (40 dp) en la tarjeta de cristal y en cada fila de la lista.
7. **Cerrados**: `Gym.closed` (campo `closed` de `GymDto`, golden `gyms.json`). Fuera por defecto; el filtro «Incluir cerrados» los enseña con la etiqueta roja «Cerrado» (sin horario).
8. **Entrenos**: ficha con botón «He entrenado aquí hoy» (pasa a «Entreno de hoy apuntado» + «Quitar»); marcar «Aquí» ya apunta el entreno en el servidor (PRESENCE) y la app solo relee. Tarjeta «Rocódromo» en el perfil propio (semana, mes, año, más visitado, racha en semanas) con `GymStatsCalculator` de shared sobre las sesiones locales + `GET gyms/sessions`; funciona sin red. Contrato: `gym-sessions.json`. `GymStatsDto` y `KtorGymSessionApi.stats` ya existen para el perfil público de otros usuarios (sin pantalla todavía).

### Estado iOS de la ronda (2026-11-20)

| Punto | Estado | Dónde |
|---|---|---|
| 1. Selector `ROCA \| ROCÓDROMOS` (dos mitades, sin «Muros gratis»), filtro «De calle (gratis)», tipo «Muro de calle» | hecho | `GymsLayerView.swift`, `GymFilterRow.swift` (`GymSegmentedPair`), `Localizable.strings` |
| 2. Mapa = el de Escuelas (satélite por defecto, capas, ubicación) | hecho | `GymsMapView.swift`, `GymsViewModel.mapStyle` |
| 3. Filtros = los de Escuelas (`FILTROS (n)` · N rocódromos, BORRAR, 50 km, panel plegado, orden por cercanía) | hecho | `GymFilterRow.swift`, `GymsViewModel.swift` |
| 4. Nombre bajo el marcador (`GymLabelPlanner`, ahora en shared) | hecho | `GymMarkerRenderer.swift`, `GymsMapView.labelledIds` |
| 5. Agrupación nueva (celda 40, sin agrupar desde zoom 11) | hecho (viene de shared) | `GymClusterer` |
| 6. Favoritos (chip ★ FAVORITOS y estrella en tarjeta y lista) | hecho | `GymFilterRow.swift`, `GymFavoriteStar` |
| 7. Cerrados (`closed`, «Incluir cerrados», etiqueta «Cerrado»); admin: interruptor «Cerrado definitivamente» | hecho | `GymTags`, `GymAdminEditSheet.swift`, `GymAdminEdit.closed` |
| 8. Entrenos: «He entrenado aquí hoy»/Quitar en la ficha y tarjeta «Rocódromo» en el perfil (tras `gyms_enabled`) | hecho | `GymDetailView.swift`, `GymStatsCard.swift`, `AccountView.swift` |

Cambios en shared de esta pasada: `GymLabelPlanner` y `LabelCandidate` pasan de `app` a `shared` (públicos); `GymAdminEdit`/`GymAdminUpdateDto` llevan `closed`.
Diferencia conocida: en iOS el nombre se coloca proyectando a Mercator (mundo de 512 pt a zoom 0), no con la proyección del mapa; el reparto solo usa posiciones relativas, así que coincide con Android.

## 11. Ronda 2 del emulador (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Rocódromos funciona **igual que Escuelas**: buscador, botón «VER MAPA / OCULTAR MAPA» (mapa plegable de 320 dp, cerrado al arrancar, `MapToggleBar`), fila «FILTROS (n) · N rocódromos · FAVORITOS» y debajo la **lista** por cercanía (50 km por defecto). Ya no hay «Mapa | Lista» ni carrusel de favoritos (el favorito es el filtro ★).

1. **Panel de filtros compacto**: dos filas deslizables (distancia; Bloque · Cuerda · Gratis · De calle · Abierto ahora · Incluir cerrados) y botón «Ver N rocódromos» (plural `gym_see_results`) que lo pliega. Elegir distancia lo pliega solo. Sin secciones con título.
2. **Fila FILTROS/BORRAR** (`filtersRowMode`, lógica pura con test): si no cabe se oculta el contador; después BORRAR queda en «✕» y FAVORITOS en su estrella. Afecta también a Escuelas (misma fila).
3. **Abierto ahora**: `GymOpeningHours` devuelve `GymOpenStatus.AlwaysOpen` para `kind == FREE` (shared). Texto `gym_open_always` («Abierto · 24 h»). Hay que añadir el caso en los `switch` de Swift.
4. **Cadenas**: filtro quitado de la UI y de `gymActiveFilterCount`. El campo `chain` de `GymFilters` sigue en shared (buscador por cadena y subtítulo de la fila).
5. **Sin agrupar**: `GymClusterer.singles(gyms)` (shared) y el mapa ya no agrupa; `GymLabelPlanner.MIN_ZOOM` baja de 10,5 a 9,0 (el nombre se oculta si choca, nunca el marcador).
6. **Tarjeta de la escuela en el mapa de Roca** (`MarkerPreviewCard`): tarjeta de cristal como la de rocódromos (radio 24, anillo con la nota a la izquierda, nombre serif 21, chips roca/estilo/distancia, «Cómo llegar» + «Ver detalle»).
7. **Notas en lista**: la nota en cuadrado pasa a píldora/círculo en la cabecera de grupo de Vías/Bloques, la tarjeta del asistente y los chips de nota de los días en quedadas.
8. **«Añadir un muro que falta»** ya no está en «Corregir datos»; vive en la capa de rocódromos (píldora bajo el mapa y al final de la lista).
9. **Plural** de «veces» en la tarjeta Rocódromo del perfil: `gym_stats_top_value` pasa a `plurals` (1 vez / N veces; es/en).
10. **Quedadas**: «Crear quedada» tiene segmento Escuela | Rocódromo (buscador de rocódromos del catálogo, sin tildes), **grado orientativo** MÍN/MÁX opcional y, si la disciplina es Vía, chip «Cordada (1 persona)». Campos nuevos en `CreateMeetupRequest`/DTO: `gradeMin`, `gradeMax`, `partnersWanted` (solo se envían si tienen valor). Contrato en `BACKEND-PENDIENTE.md`.
11. **Preparado, sin dato todavía**: `GymListRow(capacity = …)` es un hueco para el «aforo %» (no se pinta sin dato) y el orden futuro «Más vacío» se añadirá a `GymSort` cuando exista el dato.

## 12. Ronda 3 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Contrato final del servidor en staging (`BACKEND-PENDIENTE.md`; muestras en `app/src/test/resources/contract/`, copiadas idénticas del backend: `gyms.json`, `gym-report.json`, `gym-sessions.json`, `meetups.json`; `ApiContractTest` las comprueba). **Todo lo de lógica sale de shared: no reimplementar en Swift.**

1. **Nueva quedada en 3 pasos** (maqueta A, `maquetas-2/1-crear-quedada`). Sustituye al formulario antiguo para escuela y rocódromo. Lógica en `shared`: `MeetupDraft` (qué pasos están completos, toque de grado, material, límite, petición final), `MeetupGearItem`, `MeetupGear`. Android: `CreateMeetupScreen` (host), `MeetupPlaceStep`, `MeetupDetailsStep`, `MeetupFinalStep`, `MeetupWizardParts`.
   - Paso 1 «¿Dónde vais a escalar?»: segmento Escuela | Rocódromo, buscador (rocódromos: «Cerca de ti» por distancia si hay ubicación, si no por texto; escuelas: resultados al escribir 2 letras), lugar elegido como tarjeta (tocarla lo quita y pliega/despliega resultados) y días (14 próximos, varios; con escuela, la nota del día debajo). **Sin hora**: el servidor no tiene campo (la maqueta enseña «18:30»; se omite).
   - Paso 2 «¿Qué vais a hacer?»: Bloque | Vía (tarjetas; sin elegir = cualquiera), «¿Cuántos buscáis?» (− n +, 1 a 20, defecto 2; `partnersWanted`), chip «Cordada (1 persona)» (pone 1 y modalidad Vía; `isRopeTeam`), **grado orientativo** (escalera deslizable 4 a 8a+; dos toques fijan el rango, se envía en minúsculas, `GradeRange.toggle`), **material mínimo** según modalidad (Vía: Cuerda, Gri-gri, Cintas ×N (12 por defecto); Bloque: Crashpads ×N (2); `requiredGear`, máx. 4, qty 1 a 50) y tarjeta oscura «Resumen».
   - Paso 3 «¿Quién puede unirse?»: privacidad (tarjetas Abierta / Solo seguidos / No mixto), límite (− n +; bajar de 2 = «Sin límite»), nombre opcional (si no, «Quedada en <lugar>») y foto (recuadro discontinuo). Botón «CREAR QUEDADA».
   - Pie: «← ATRÁS» (secundario) + «SIGUIENTE →»; la flecha de la cabecera y el Atrás del sistema también retroceden un paso. Atrás conserva todo (el borrador entero se guarda serializado). Textos: claves `mw_*`, `gear_*`.
2. **Detalle de quedada** (`MeetupDetailFacts`): píldoras de modalidad, «Nivel 6A», «Busca N compañeros» o «Cordada (1 persona)» y tarjeta «MATERIAL MÍNIMO» con `gearCoverage`: «✓ Cuerda 1/1 · ⚠ Cintas 9/12» + «Falta material: si puedes, llévalo.» (borde rojo) o «El material está cubierto.»; botón primario «Chat de la quedada» (solo apuntados/organizador; abre el chat de grupo por `conversationId`). El detalle offline (caché SQLDelight) **no** guarda gymId/grado/material (no se tocó el esquema).
3. **Entrenos** (maqueta `2-entrenos-detalle`): tocar la tarjeta «Rocódromo» del perfil (lleva «Ver entrenos ›») abre `GymTrainingScreen` (ruta `gym-training`): Semana | Mes | Año, KPIs (sesiones, rocódromos, ascensiones), botón **«He entrenado en…»** (elige rocódromo y apunta hoy, deja la sesión abierta), sesiones con día («Hoy · viernes 20»), y por sesión: si el rocódromo tiene escala **COLORS**, filas con bolita de color + − n + (clave = nombre del color); si no, rejilla de grados (4, 5, 6a, 6b, 6c, 7a, 7b, 8a + los ya apuntados) con − n +. «Año»: tarjetas por rocódromo (los 3 más visitados) con barras por color o grado desde `GET users/{id}/gym-ascents` (sin red se calcula igual con las sesiones del móvil). Shared: `GymTraining` (periodos, resumen, stats, `adjust`), `GymTrainingPeriod`, `GymAscent`, `GymSession.ascents`, `GymTrainingUseCase`, `KtorGymSessionApi.putAscents/ascentStats`. Primero en el móvil y luego `PUT /gyms/sessions/{id}/ascents` (sustituye todo; si la sesión de hoy aún no tiene id se da de alta con `sessions/today`); el último toque manda.
4. **Escalas**: ficha con sección «Escala de dificultad» (bolitas de color de fácil a difícil + «De fácil a difícil», o «Por grado»). En «Corregir» chip nuevo **«Escala de colores o grados»** (`GymReportIssue.SCALE`): editor Colores | Grados; con colores se tocan los de la paleta `GymScalePresets` (shared) **en orden de fácil a difícil** (número sobre cada bolita; mínimo 2). Se envía `issues:["SCALE"]` + `scale` (las dos juntas o 400).
5. **Llamar**: botón «Llamar» (intent DIAL, no llama solo) en la ficha si hay `phone`; las acciones van en filas de dos (Cómo llegar + Llamar / Web + Instagram).
6. **Roca mojada en Escuelas**: banner «Hoy la roca está mojada cerca de ti · ¿Rocódromo?» (azul lluvia, gota) sobre la lista, solo en «Escuelas» sin días elegidos y con `gyms_enabled`; al tocarlo pasa a la capa Rocódromos. Lógica: `WetRockAdvisor.isMostlyWet` (shared, con test): se mira `dryRock` de hoy de las escuelas que entran en el filtro de distancia; avisa si hay muestra (mín. 3 conocidas, o todas si hay menos) y **más de la mitad** mojadas.
7. **OpenStreetMap**: «© colaboradores de OpenStreetMap» (`gym_osm_credit`, 10 sp) siempre al final de la ficha de rocódromo y en Ajustes (bajo la versión; no hay pantalla «Acerca de»).
8. **Fotos de la comunidad**: «Añadir foto» en la ficha + «Solo fotos tuyas»; va como reporte (`issues:["OTHER"]`, comentario «Foto de la comunidad», foto subida con `uploadGymReportPhoto`) para que el admin la apruebe. **No hay «Denunciar foto»**: el servidor no devuelve fotos aprobadas en `GymDto` (ver BACKEND-PENDIENTE).
9. **Estado / textos de la ficha**: «Sin confirmar» (fiabilidad) y el crédito de OSM van **abajo del todo**, pequeños (11 y 10 sp). Orden de la ficha: cabecera, acciones, modalidad, escala, horario, precio, servicios, «Quién va hoy», «He entrenado aquí hoy», **«Chat del rocódromo»**, «Crear quedada aquí», «Añadir foto», «Corregir», notas.
10. **Chat de rocódromo** (`GymChatScreen`, ruta `gyms/{id}/chat?name=`): el mismo chat abierto de escuela (`school_chats/{id}/messages` en Firestore, que ya acepta cualquier id y texto ≤ 500) con la clave **`gym-{id}`**. Sin «Aquí» obligatorio. Tocar el mensaje de otra persona abre su chat 1-1. No necesita servidor.
11. **Tarjeta de cristal de escuela** (mapa de Roca): enlace pequeño «Corregir» bajo los botones; abre la hoja «Aportar» de siempre (piedra / escuela).
12. **Marcadores de escuela** (propuesta A de `maquetas-3/marcadores`): anillo que se llena según la nota (color de `scoreColor`) sobre pista blanca translúcida, centro crema (`#FBF9F4`) con la nota en mono; **mojada**: anillo azul `#2563C7` + centro `#E3ECFA` + gota azul arriba a la derecha; **seleccionada**: 50 en vez de 40 con aureola terracota; **nombre debajo en etiqueta blanca** redondeada. Los nombres de rocódromo usan la misma etiqueta. Zoom lejano: puntitos como siempre. Android: `ringBitmap` en `SchoolsMapMarkers.kt`.
13. **Lista de escuelas** (propuesta A de `maquetas-3/lista`): tarjetas **blancas** (`#FFFFFF`; oscuro `#211F19`), radio 20, sombra suave y **sin borde gris** (solo al elegir para comparar); nota con el **anillo de progreso** de siempre (`ScoreRing`, ya parametrizado por diámetro). Mojada: anillo azul entero con gota en el centro y etiqueta «Roca mojada» en azul (`RainBlue` #2563C7 / `RainBlueDark` #7BA3EE). La cabecera de grupo de Vías/Bloques usa el mismo anillo (44).
14. **Perfil → tarjeta «Rocódromo» no se refrescaba** (aviso de Álvaro): la pestaña Perfil se queda viva y la tarjeta solo se leía al primer arranque; ahora se relee cada vez que la pestaña se hace visible. Mirar en iOS si `GymStatsCard` tiene el mismo problema (relectura al aparecer).

Diferencias conocidas: Maestro del paso 1 toca el buscador por posición (el texto de ayuda no sale en el árbol de accesibilidad de Compose).

## 13. Ronda 4 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (iOS no debe reimplementarla): `RockDryOutlook` (seca/mojada, franja de 5 días), `GymAscentKeys` (claves de vías y boards), `MeetupGearItem.forPlace` (material según lugar), purga de la caché de quedadas (`MeetupCacheRepository.retainOnly/remove`).

1. **Ficha**: fila superior = volver · chat (icono `Forum`, el de siempre) · favorito. Orden: cabecera, acciones, «He entrenado aquí hoy» y «Crear quedada aquí» ARRIBA, modalidad, escala, horario, precio, servicios, «Quién va hoy». Escala en tarjeta: con colores, cada color con su rango debajo (`gradeRange`); sin colores, chips con la paleta de grados (`gradeChipStyle`). «Estoy aquí» ya va arriba junto al chat (icono `Place`, activo en terracota; la primera vez pide el aviso de privacidad); «Quién va hoy» sigue abajo con su botón.
2. **Hoja «He entrenado aquí hoy»** (`GymTrainSheet`): pestañas Bloques | Vías | Boards, «Guardar entreno» fijo. Bloques: color de la escala (con rango) o grados con color. Vías: grado (paleta de grados) + «Con compañero» / «Autoaseguro» + Encadenada | Proyecto + «Añadir…» + etiquetas «6A ×2». Boards: Kilter (negro con celeste), Tension (madera), Moonboard (amarillo), inclinación 30/40/45 y contadores por grado. Iconos vectoriales nuevos: `ic_style_partner` (el autoaseguro reutiliza `ic_autobelay`); si quedan pobres, Álvaro los pedirá a ChatGPT. Claves en `ascents`: ver BACKEND-PENDIENTE §8.
3. **Perfil**: «Más visitado» pasa a «Tu rocódromo» (se toca y abre su ficha) y «Racha» a «Semanas seguidas». La tarjeta es de degradado (morado, terracota, naranja) con sesiones, bloques y vías del mes, barra con los colores de lo hecho (escala del rocódromo o `gradeChipStyle`) ordenada por dificultad y «Tu tope este mes»; debajo, «Tu rocódromo», «Semanas seguidas» (icono de llama de la app), «Último entreno» y «Apuntar entreno» (abre el selector).
4. **Crear quedada**: desde una ficha el lugar viene fijado (tarjeta «FIJADO», solo se pregunta cuándo). Sin fijar: primero el día, luego Escuela | Rocódromo. Material: en rocódromo solo cuerda y gri-gri si hay vía (nada en bloque); en roca según modalidad. Sin pies de gato, magnesio ni cepillo.
5. **Detalle de quedada y Mi material**: el diálogo «Mi material» en rocódromo solo ofrece cuerda y gri-gri. Hecho: detalle moderno (`MeetupDetailBody.kt`: cabecera de gradiente azul-morado en rocódromo y verde en roca, chips de nivel con colores de grado, material del grupo en verde/terracota, participantes con su material y «Organiza», «Eliminar quedada» discreto), chat con cabecera de cuadradito de color + avatares (`GroupChatHeader.kt`) y hoja «¿Qué llevas tú?» (`MeetupGearSheet.kt`: tarjeta por material con interruptor o contador y «N de M del grupo lo llevan»; solo material de `forPlace`).
6. **Quedadas**: selector `TODAS | ROCA | ROCÓDROMO`, mapa abierto por defecto (mismo mapa que Escuelas, pines de rocódromo terracota y de roca verde), radio 25 km por defecto (primera vez), cabecera sin fondo gris, pull-to-refresh (`PullToRefreshBox`). **Bug del borrado**: la caché local no se purgaba (reaparecía al recargar con la lista vacía); ahora `getMeetups` sin filtros purga lo que el servidor ya no devuelve y `deleteMeetup` borra de la caché. Mirar si iOS tiene el mismo patrón. Hecho: círculo del radio (relleno translúcido + contorno sólido) y chip «Radio 25 km» que abre un menú para cambiarlo (25/50/100/200/500/Cualquiera). Solo en el mapa de la pantalla, no en el de pantalla completa.
7. **Seca/mojada**: la tarjeta del mapa de Roca pide la previsión de la escuela y pinta la banda «Mojada · seca en ~X h» o «seca a partir del día», o «Seca hasta el día · Empeora el día», más la franja de 5 días (azul mojada, ámbar dudosa, verde seca). Umbrales: 1 mm día mojado, 0,2 mm dudoso. La lista de escuelas pinta lo mismo en pastillas compactas (`SchoolDryPill`: «Mojada · seca en ~6 h» / «Seca hasta el viernes» + «Empeora sábado»).
8. **Enlaces web** (Web e Instagram de la ficha) se abren en Custom Tabs (`androidx.browser`); en iOS, `SFSafariViewController`.
9. **Sectores plegables** en Vías/Bloques de una escuela: la lista Vías/Bloques empieza con los grupos de escuela plegados (comprobado en Zarzalejo) y los filtros funcionan (223 a 176 con «Bloque»).
10. **Selector «¿Dónde has entrenado?»** (`GymPickerSheet`): buscador, Hoy | Ayer | Otro día (calendario sin futuro), Favoritos, Recientes («hace 2 semanas», `GymPickerViewModel`) y Cerca; «Seguir · apuntar en X» abre la hoja de entreno de ese día. LÍMITE: el servidor solo tiene `sessions/today`, así que Ayer/Otro día se guardan solo en el móvil hasta que haya `POST /api/gyms/{id}/sessions` con fecha.
11. **Claves route:/board: bonitas** (`GymAscentPills`): vía = grado con su color + icono compañero/autoaseguro + check (encadenada) o «Proyecto» + ×N; board = color de marca + nombre + grado + inclinación + ×N. Nunca la clave en crudo (entrenos, año, perfil).

## 14. Ronda 5 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (no reimplementar en Swift): `ExploreSectors` (sectores plegables y «bloques»/«vías»), `GymAscentKeys` (rejilla `GRID_GRADES`, `toggleGrade`, grados dobles contiguos `6b+/6c`, `gradeOf`, `harder`), `MeetupDraft.toggleBoulder/toggleRoute` (`BOTH`), `GymSummary`/`GymBreakdown` (totales, por rocódromo, grado y mes), `JournalStatsCalculator.rockDays`, `GetGymSummaryUseCase`, y editar a cero borra la sesión (`LocalFirstGymSessionRepository.saveAscents`).

1. **Franja de 5 días**: pastilla por día con icono (sol seca, nube dudosa, gota mojada), hoy recuadrado, resumen en una línea (`RockDryBanner`).
2. **Ficha de rocódromo**: cabecera atrás · chat · mapa (pin, abre el mapa del móvil) · estrella; UNA tarjeta «Estoy aquí / Quién va hoy»; fila de 4 iconos (Cómo llegar, Llamar, Web, Compartir); «He entrenado» y «Crear quedada» lado a lado. Chat de rocódromo: sin «Aquí» no se escribe (barra con candado).
3. **He entrenado**: rejilla de grados (uno, o dos contiguos = doble), «Añadir», lista de lo añadido con papelera; en vías y boards. El servidor acepta `route:partner:sent:6b+/6c`.
4. **Perfil propio y ajeno**: cabecera compacta (sin bio), título con efectos, 4 celdas, «Mis publicaciones», tarjeta de degradado (Total/Roca/Rocódromo + sesiones/rocódromos/ascensiones pulsables) que abre Estadísticas (Todo · Roca · Rocódromo); «Ascensiones» (Todo/Bloques/Vías/Boards). «Tu rocódromo» se elige en Ajustes y ya vive en el servidor (ver punto 10). Deslizar para recargar. Escuelas y Proyectos pasan a la pestaña Todo de Estadísticas.
5. **Crear quedada**: Bloque y Vía a la vez (`BOTH`), rango de grado con colores, paso 3 «Material y detalles» (baldosas, mensaje, privacidad, nombre+foto, resumen). Se quita el límite de participantes.
6. **Detalle de quedada**: cómo llegar y compartir (icono de iOS) arriba.
7. **Chats**: `ChatInputPill` (campo píldora + enviar redondo) en privados, quedada, escuela y rocódromo; `ChatLockedBar`; burbujas de 18 con hora y «· aquí»; separador de día. Sin cámara (no existe en ningún chat).
8. **Vías/Bloques**: sectores plegables dentro de cada escuela; contadores «bloques»/«vías» si solo hay uno.
9. «Rocódromos cerca» en la ficha de escuela: una línea pequeña plegable. Mapas de Quedadas con los estilos de Roca.
10. **Tu rocódromo en el servidor** (iOS hecho): `PUT /api/me/home-gym` con `{"gymId": 5}` (null = quitar) y `GET /api/me/home-gym`, respuesta `{"homeGym": {"id","name","city"} | null}`. `gym-summary` lleva ahora `homeGym` (misma privacidad que el resto del resumen: perfil privado ajeno = 404). Lógica en `shared` (`HomeGymUseCase`, `GymSummary.homeGym`, `HomeGym`). iOS: Ajustes guarda en servidor (y caché local); el perfil propio muestra «Tu rocódromo» (hueco «Elige tu rocódromo» si no hay, abre el selector) y el ajeno «Su rocódromo» (sin hueco si no tiene); tocar abre la ficha. Al abrir el perfil propio: si el servidor lo tiene se copia al móvil; si no y el móvil sí, se sube (migra a quien ya lo había elegido solo en local). Textos nuevos: `home_gym_yours/theirs/pick`.

## 15. Ronda 6 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Logica nueva en `shared` (no reimplementar en Swift): `MeetupGearStatus.lines(meetup)` (material que tiene y que falta; cordada en via sin material pedido = cuerda + gri-gri; la cobertura la da el servidor), con test.

1. **Sectores plegables** (Vias/Bloques de una escuela): cada sector es una TARJETA (radio 16, borde, sombra suave) con nombre, contador en pildora y chevron en circulo (relleno terracota al abrir).
2. **Ficha de rocodromo**: "Servicios" sube justo bajo "He entrenado / Crear quedada"; "Anadir foto" y "Corregir" van lado a lado debajo; el resto (modalidad, escala, horario, precio) despues.
3. **Chat**, un solo diseno para quedada, privado, escuela y rocodromo (maqueta 7A): `ChatTopBar` (volver, avatar, titulo + subtitulo, acciones a la derecha), burbujas con cola (`chatBubbleShape`: ajenas 18/18/18/6, mias 18/18/6/18), campo pildora + enviar redondo, barra con candado. Sin camara (ningun chat envia fotos).
4. **He entrenado**: sin entreno hoy, el boton dice "Has entrenado hoy?"; con entreno, "Entreno apuntado".
5. **Perfil propio**: Ayuda (?) dentro de la pildora de iconos a la derecha (ayuda, material, compartir, ajustes); avatar desplazado a la derecha (`ProfileAvatarStart` = 38 dp) para que dragones/hadas no se corten; vuelve la celda **Escuelas** arriba (5 celdas: Escuelas, Bloques, Vias, Max bloque, Max via); tarjeta de degradado partida en dos mitades iguales **Roca** (dias, bloques y vias con su maximo, escuelas) y **Rocodromo** (sesiones, rocodromos, ascensiones, maximo), cada una pulsable.
6. **Ajustes**: secciones en grupos redondeados (radio 18, borde, separadores) con icono en cuadradito tintado; "Cerrar sesion" tambien redondeado.
7. **Perfil ajeno**: mismo desplazamiento del avatar; SEGUIR en su propia fila (ya no esconde "seguidores"); chat, compartir y los tres puntos en terracota como el propio; celda Escuelas.
8. **Seguidores/Siguiendo**: filas-tarjeta redondeadas (radio 18, borde), botones en pildora, buscador redondeado.
9. **Quedadas**: tarjeta de la lista con cubierta (foto; si no hay, avatar del organizador con su aureola), tarjeta del lugar (rocodromo: cuadrado como en su lista; roca: anillo de nota de la escuela + distancia), pildora "Via de cordada" (tambien en el detalle y en el paso 2), y fila "MATERIAL" con el check de lo que ya hay (verde) y "Falta X" (rojo) antes de apuntarse.

Diferencias conocidas: el clima del lugar en la tarjeta de quedada se limita a la nota del primer dia con prevision. (El avatar junto a cada burbuja en grupos se anadio despues: punto 10.)

10. **Avatar en los chats de GRUPO** (quedada, escuela, rocodromo; NO en el privado 1 a 1; Android e iOS hechos): los mensajes ajenos llevan a la izquierda la foto redonda del emisor (28 dp, con su aureola de titulo, como `PersonAvatar`) y su nombre ENCIMA de la burbuja. En una racha (mensajes seguidos del mismo emisor, sin cruzar de dia): el nombre solo en el PRIMERO y el avatar solo en el ULTIMO (en los demas, un hueco de 28 dp + 6 dp para alinear las burbujas). Mis mensajes: sin avatar ni nombre. Tocar el avatar abre el perfil publico. Logica de rachas en `chatRunPositions` (Android; en iOS `chatRunPositions` de ChatKit.swift). Esto sustituye la "diferencia conocida" de arriba.

## 16. Ronda 7 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Logica nueva en `shared` (no reimplementar en Swift): `GearJson.parse` (mochila `{"cuerda":true,"cintas":12}`, los si/no cuentan 1), `MeetupGearStatus.withMochilas(meetup, mapOf(uid to mochilaJson))` (suma la mochila del organizador y de los apuntados al material cubierto; nunca baja lo que cuenta el servidor) y `AscentTabOrder` (orden de las categorias de Ascensiones), los tres con test.

1. **Chats** (los 4 con las mismas piezas): cabecera blanca con sombra (volver en circulo, avatar 44, titulo serif, subtitulo), campo de escribir en pildora sobre barra blanca (borde terracota al escribir, enviar redondo terracota con sombra, 40 % de opacidad si esta vacio), burbujas ajenas blancas con borde y sombra suave y las mias terracota (ambas con cola), separador de dia en pildora y estado vacio «Rompe el hielo» (icono en circulo + frase). Antes se veia beige sobre beige. El lapiz del campo es decorativo (ningun chat envia fotos).
2. **Chat de quedada**: debajo de la cabecera, pildoras «Como llegar», «Invitar al grupo» y «N personas» (abre la quedada) y una tarjeta redondeada de material con chips (✓ Cuerda en verde, «Falta gri-gri» en rojo) y boton «Mi material». La campana va en la cabecera, en circulo. Plurales: «1 cuerda», «2 cuerdas», «1 cinta».
3. **Material**: la mochila (Mi material del perfil) cuenta como material disponible para quien usa la app; al CREAR una quedada se copia la mochila del organizador a la quedada (igual que al unirse). La mochila de OTRA persona solo se conoce si se copio a la quedada al apuntarse (no hay endpoint para leer mochilas ajenas). Tarjeta de lista, detalle y chat usan `withMochilas`.
4. **Perfil propio**: ayuda y ajustes en terracota como mochila y compartir.
5. **Servicios de la ficha**: cada servicio es una tarjeta con icono en cuadradito de color (autoseguro morado, alquiler azul, ninos ambar, principiantes verde) y valor en pildora (Si verde, No rojo, Sin confirmar gris); horario y precio, en tarjetas con icono de color.
6. **Perfil propio y ajeno (mismo orden)**: 4 celdas (Bloques, Vias, Max bloque con grado en color, Max via) · 3 fichas (Publicaciones, Escuelas, Proyectos con su cuenta; Publicaciones lleva su cuenta de feed/count, ver 16b) · tarjeta de degradado (la mitad ROCA abre Estadisticas en su pestana Roca, la mitad ROCODROMO en la pestana Rocodromo; ruta `journal/stats?uid=&tab=rock|gym`) · fila «Tu/Su rocodromo» + «semanas seguidas» + boton pequeno «+ Entreno» (solo en el propio). Desaparecen la barra «Mis publicaciones», la celda Escuelas suelta y el boton grande de apuntar entreno. Estadisticas > Todo: Escuelas y Proyectos como filas redondeadas con icono de color; Estadisticas > Rocodromo gana la fila «Ver ascensiones» (la tarjeta ya no abre esa pantalla).
7. **Ascensiones**: boton arriba a la derecha para ordenar las categorias (Bloques, Vias, Boards; «Todo» siempre primero) con flechas; se guarda en el servidor (ver 16b) y queda en cache en el movil (`cumbre_profile_prefs`, clave `ascent_tab_order`).

### 16b. Orden de Ascensiones en el servidor y cuenta de Publicaciones (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

- **Orden de Ascensiones**: `PUT/GET /api/me/ascent-order` (`{order:"BOARDS,BLOCKS,ROUTES"}`) y campo `ascentOrder` en `gym-summary` (aditivo). Lógica en `shared` (`AscentOrderUseCase`, `GymSummary.ascentOrder`; `AscentTabOrder.parse` ya tolera null o roto). Al reordenar: se ve ya, se guarda en el móvil y se sube (si falla la red queda «pendiente» y se sube al abrir el perfil). Al abrir el perfil propio manda el servidor; si el servidor no tiene nada y el móvil sí (orden elegido antes), se sube el del móvil. En el perfil AJENO la pantalla Ascensiones usa SU orden (`summary.ascentOrder`) y no enseña el botón de ordenar (solo en la propia). Texto del diálogo: «Los demás las verán en este orden».
- **Publicaciones**: `GET /api/feed/count?uid=` devuelve `{count}` (shared: `GetFeedPostCountUseCase`). Cuenta lo mismo que la lista (sin NEW_BLOCK/NEW_LINE); 0 en privado sin seguir o bloqueado. La ficha «Publicaciones» muestra el número en el perfil propio y en el ajeno (solo la flecha mientras no llega).

## 17. Ronda 8 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Logica nueva en `shared` (no reimplementar en Swift): `GymFilterEngine.apply` (buscando por texto, favoritos antes que el resto y luego distancia; los buscadores de «Apuntar entreno» y de «Nueva quedada» ahora pasan los favoritos), `GymSummaryGym.items/days` (lo hecho en cada rocodromo y sus dias), `GymBreakdown.gymsFiltered / hasGymBreakdown / boardsByKind`, `BoardKind.SYSTEM`; todo con test.

1. **Buscador de rocodromos**: favoritos primero, luego distancia (solo con texto escrito).
2. **Estadisticas > Rocodromo > «Por rocodromo»**: tocar una fila abre `gym-visit/{gymId}?uid=`: tres celdas (sesiones, ascensiones, max. grado), pildora secundaria «Ver ficha del rocodromo», reparto con la escala de ESE rocodromo y «Dias de entreno» (fecha + «N ascensiones»). Sirve para el perfil propio y el ajeno (misma privacidad que `gym-summary`).
3. **Ascensiones agrupadas por rocodromo** (Todo, Bloques y Vias): cabecera por rocodromo (inicial, nombre, cuantas, flecha → visita) y debajo su reparto con SU escala (colores en uno, grados en otro; nunca mezclados). Con un servidor sin el desglose se pinta como antes.
4. **Boards**: la marca YA se guardaba en la clave (`board:<kilter|tension|moonboard>:...`), no hizo falta migracion. Se anade la marca `system` (backend + `BoardKind.SYSTEM`, color verde #3E8E6B, selector con 4 marcas). La pestana Boards se agrupa por tablero (logo + nombre + total) y dentro por grado.
5. **Ordenar**: pildora con icono y texto «Ordenar» arriba a la derecha de Ascensiones; el dialogo lleva botones con texto «Subir» / «Bajar» (grises al no poder mas).
6. **Perfil ajeno**: «Te sigue» (izquierda) y pildora compacta «Siguiendo» / «Seguir» (derecha) en la MISMA fila (32 dp); tocar «Siguiendo» pide «¿Dejar de seguir a X?». Menos espacio vertical: las celdas y la tarjeta de degradado caben en un Pixel 7 sin bajar.
7. **Perfil ajeno, mitad Rocodromo**: abre Estadisticas > Rocodromo con el desglose por rocodromo (→ visita con sus sesiones), respetando la privacidad del resumen.

## 18. Ronda 9 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (no reimplementar en Swift): `GymOrder` (parse/serialize/sort/move: favoritos primero por defecto, el orden manual manda), `AscentEntries` (`grades` y `entries`: ascensiones sueltas de los grados elegidos, más recientes primero, y «Antes» para lo que el resumen cuenta pero no trae con día), `GymSummary.gymOrder`, `GymSummaryDay.items`, `GymOrderUseCase`; todo con test (`GymOrderAndEntriesTest`).

1. **Orden de rocódromos**: en Ascensiones (agrupado por rocódromo) y en Estadísticas > Rocódromo > «Por rocódromo»: favoritos primero por defecto; el botón «Ordenar» abre un diálogo con dos secciones, «Categorías» y «Rocódromos», con «Subir»/«Bajar» (al mover se guardan todos los rocódromos). Se guarda en el servidor (`me/gym-order`, y `gymOrder` en gym-summary para que otros lo vean) con caché local (`gym_order`, con marca de pendiente de subir como el orden de categorías). En perfil ajeno manda el orden de esa persona (mis favoritos para los que no haya ordenado). Solo se ordena en el perfil propio.
2. **Boards plegables**: cada tablero (Kilter, Moon, Tension, System) se pliega/despliega tocando su cabecera (chevron); el estado se recuerda en el móvil (`boards_collapsed`).
3. **Filtro por grado** (Todo, Bloques, Vías y Boards): fila de chips «Todos» + los grados de esa categoría (color de grado); varios a la vez; «Todos» limpia. Con grados elegidos se ve la lista de ascensiones sueltas: grado, qué fue (Bloque, Vía o el board), rocódromo y día (o «Antes»), ×N si hay varias; tocar abre el rocódromo. No hay nombre de vía/bloque guardado (las ascensiones son clave + cantidad), por eso no se muestra. El servidor manda los últimos 60 días por rocódromo; el resto sale como «Antes».
4. **Cabecera del perfil**: el nombre admite 2 líneas con puntos suspensivos (antes se cortaba «Manuela Jara paredes» a «Manuela Jara» sin avisar, mientras el diálogo de dejar de seguir usaba el nombre completo; era el mismo dato).

## 19. Ronda 10 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (no reimplementar en Swift): `BoardProblem` / `NamedBoardProblem` / `BoardProblems` (`cleanName` máx. 60, `add`, `prune` = los nombres nunca superan los recuentos de su board+grado, `namesByKey`, `search` sin mayúsculas ni acentos), `GymSession.boardProblems`, `GymSummary.boardProblems`, `GymSummaryDay.boardProblems`, `AscentEntry.names`; `saveAscents(..., boardProblems: List<BoardProblem>? = null)` (null = no tocar los nombres guardados). Todo con test (`BoardProblemsTest`, contrato `gym-sessions.json`).

1. **Nombre opcional del bloque** (solo Kilter, Tension, Moonboard, System): en «He entrenado» > Boards, campo «Nombre del bloque (opcional)» (60 caracteres, contador 0/60, se recorta). Con nombre, al añadir sale en la fila de lo añadido («40° · Slab Master»). Sin nombre, igual que antes. Los recuentos (`board:kilter:6c:40 ×N`) no cambian: los nombres van aparte.
2. **Ascensiones > Boards**: cada tablero plegable lleva un buscador «Buscar en Kilter por nombre» y, debajo, «Bloques con nombre · N» (grado, nombre, rocódromo y día; tocar abre el rocódromo). Sin texto salen todos los nombrados; con texto, los que coinciden (sin acentos ni mayúsculas); sin coincidencias, «Ningún bloque con ese nombre». Mismo código para el perfil propio y el ajeno (misma privacidad que `gym-summary`).
3. **Chips de grado**: en la lista de ascensiones sueltas, los boards con nombre muestran «Kilter · Slab Master». Lo de más de 60 días (el servidor manda 60 días por rocódromo en `days`) sale sin nombre en esa lista, pero el buscador usa la lista completa (`boardProblems` del resumen, hasta 300).

## 20. Ronda 11 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (no reimplementar en Swift): `GymBreakdown.gymsFilteredWithoutBoards`, `withoutBoards`, `boardCount`, `visitWithoutBoards`, `AscentEntries.entries(..., includeBoards)`; `GymSessionRepository.syncPending()` y la cola de pendientes dentro de `LocalFirstGymSessionRepository` (clave `gym_sessions_pending_v1`), con test (`GymOfflineQueueTest`).

1. **Boards son globales**: un problema de Kilter, MoonBoard, Tension o System es el mismo en cualquier rocódromo. Perfil (propio y ajeno): la fila de fichas pasa a 4 más pequeñas, **Publicaciones · Escuelas · Proyectos · Boards** (cuenta = ascensiones de board de todos los rocódromos; solo con rocódromos activados; en 360 dp las etiquetas bajan a 7 sp y la separación a 4 dp). Tocar Boards abre la pantalla nueva `gym-boards?uid=` («Boards · N»): chips de grado, una sección plegable por tablero con buscador por nombre, «Bloques con nombre» y reparto por grado (mismas piezas que la pestaña Boards de Ascensiones). Misma privacidad que `gym-summary`.
2. **Ascensiones > Todo** ya no reparte los boards por rocódromo: salen solos en una fila final «Boards · todos los rocódromos · N ›» que abre la pantalla Boards; los chips de grado de Todo tampoco los incluyen. La pestaña Boards sigue agregando todos los rocódromos. La visita a un rocódromo (`gym-visit`) no cuenta ni pinta boards.
3. **Sin conexión**: apuntar un entreno, sus ascensiones y los boards con nombre ya se guardaba primero en el móvil, pero lo que fallaba al subir **nunca se reintentaba** (solo se fusionaba en el refresco). Ahora cada alta, cambio o borrado queda marcado como pendiente (persistido) hasta que el servidor lo confirma, y se sube al volver la conexión (`OutboxFlusher`), al abrir la app y al abrir el perfil (`refresh` sube lo pendiente antes de traer lo del servidor, y lo pendiente manda sobre el servidor). Mientras tanto el perfil y las estadísticas lo enseñan desde el móvil. Límite: el servidor solo tiene ruta de «hoy» para dar de alta una sesión, así que un entreno de OTRO día apuntado sin red queda en el móvil (ver BACKEND-PENDIENTE).

### 20b. Ronda 11b (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

- Entrenos de Ayer / Otro día apuntados sin red: al volver la conexión, `syncPending` los da de alta con `POST gyms/{id}/sessions` `{date}` (hoy sigue por `/today`) y sube ascensiones y nombres. iOS: misma ruta en su cola de pendientes (la lógica ya está en shared: `LocalFirstGymSessionRepository.push` y `KtorGymSessionApi.checkInOn`, con `@Throws`).
- Banner «SIN CONEXIÓN» pegado: `AndroidNetworkMonitor` ponía offline en cada `onLost` aunque quedase otra red validada (wifi+datos) o llegase tarde el aviso de la red vieja. Ahora lleva el conjunto de redes validadas y hay conexión mientras quede alguna. iOS usa NWPathMonitor (estado, no eventos): sin cambios.

### 20c. Boards plegados por defecto + grado máximo (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

- Todas las secciones de board (Kilter, MoonBoard, Tension, System), en la pantalla Boards y en Ascensiones > Boards, empiezan **plegadas**; solo se recuerda lo que la persona despliega (pref nueva `boards_expanded`; la antigua de plegados se ignora). La cabecera enseña, junto al número, el **grado máximo hecho en ese board** como pastilla con el color del grado (la misma de «Máx bloque» del perfil). Lógica en shared: `GymBreakdown.maxGrade` (test en `GymAscentKeysTest`).

## 21. Ronda 12 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Lógica nueva en `shared` (no reimplementar en Swift): `BoardAngles` (`anglesFor`, `defaultAngle`, `initial`, `counts`, `filter`, `problemsAt`), `BoardAngleMemory(KeyValueStore)` (última inclinación por board), `GymSession.NO_GYM = 0` / `hasGym`, `GymTrainingUseCase.session(gymId, date)`, `KtorGymSessionApi.boardDays/putBoardDay`, `BoardDayDto`; `GymBreakdown.summarize`, `GymStatsCalculator`, `GymTraining.summary/stats` ya excluyen las sesiones sin rocódromo de rocódromos/rachas/sesiones. Tests: `BoardAnglesTest`, `SoloBoardTest`, `GymOfflineQueueTest`, contrato `gym-sessions.json` > `boardSession`. `GymAscentKeys.BOARD_ANGLES` desaparece (usa `BoardAngles.anglesFor(board)`).

1. **Inclinaciones por board** (selector del registro de boards, en «He entrenado» y en «Solo board»): Kilter y Tension 20°–70° de 5 en 5; **MoonBoard solo 25° y 40°** (los oficiales; Álvaro dijo 25 y 45, y 45° no existe en MoonBoard); System sin inclinación (el selector no sale y la clave va sin ángulo). Los chips ocupan varias filas (FlowRow).
2. **Inclinación por defecto y última usada**: la primera vez Kilter 45°, MoonBoard 25°, Tension 25°. Al elegir una inclinación (o añadir) se recuerda por board en el móvil (`cumbre_board_prefs`, clave `board_last_angle_<board>`) y se preselecciona la próxima vez hasta que se cambie; solo se guarda un valor válido de ese board. Quitar la selección (tocar la elegida) no se recuerda.
3. **«+» en cada tablero de la pantalla Boards** (solo perfil propio): botón redondo terracota en la cabecera de cada tablero (también los vacíos: en tu perfil salen siempre los 4). Abre una hoja «Boards · Kilter» con el tablero fijado (sin selector de marca), inclinación, grado, nombre opcional y «Guardar boards». Apunta HOY, sin rocódromo, y al cerrar recarga la pantalla. Si ya había boards sin rocódromo hoy, los carga y suma (no los pisa).
4. **«+ Entreno» > «Solo board»**: en el selector «¿Dónde has entrenado?» sale arriba una fila «Solo board · Sin rocódromo: tu board de casa o da igual dónde» (respeta Hoy/Ayer/Otro día). Al tocarla se abre la misma hoja con selector de las 4 marcas. No hay que elegir rocódromo.
5. **Backend (aditivo, V119)**: `PUT /api/me/board-sessions/{yyyy-MM-dd}` `{ascents:[{key:"board:..."}], boardProblems:[...]}` sustituye el día (idempotente; `ascents: []` lo borra; solo claves `board:`, fecha no futura y de hace como mucho 366 días) y `GET /api/me/board-sessions[?from&to]` → `[{date,ascents,boardProblems}]`. Detrás de `gyms_enabled`. `gym-summary` suma estos boards a `ascents`, `items`, `maxGrade`, `byMonth` y `boardProblems` (con `gymId: 0`) pero NO a `sessions`, `gyms` ni `byGym`; `gym-ascents` sin `gymId` también los suma. Las apps viejas no se rompen: no conocen los endpoints nuevos y el resto de campos no cambia.
6. **Cliente**: una sesión «Solo board» es una `GymSession` con `gymId = 0`. Funciona sin red con la cola de pendientes ya existente (`0|fecha` → `U`); `push` y `deleteRemote` llaman a `putBoardDay` en vez de `/sessions`; `refresh` une los días del servidor (un servidor sin la ruta no rompe el refresco). Cuentan en la ficha y pantalla Boards y en los totales del perfil, nunca en «Por rocódromo», visitas, rachas ni «sesiones». En la lista de bloques con nombre, la fila de un bloque sin rocódromo no es pulsable y solo enseña el día. Trampa: la hoja debe leer el día existente con `GymTrainingUseCase.session()` (no con `summary`, que excluye `gymId 0`), si no el segundo guardado pisa el primero.
7. **Filtro por ángulo en Boards** (pantalla Boards y Ascensiones > Boards, perfil propio y ajeno): dentro de cada tablero con inclinaciones, fila «Ángulos» **plegada por defecto** (chevron). Abierta: chips «Todos» + cada inclinación registrada con su cuenta («30° · 1»); la elegida se ve también junto al título. Elegir una filtra el reparto por grado, la pastilla de grado máximo y el número de la cabecera; los bloques con nombre salen solo si ese grado del board se hizo en ese ángulo (el nombre no guarda su inclinación: es una aproximación, ver BACKEND-PENDIENTE). El ángulo se saca de la clave en shared (`BoardAngles.counts/filter`). No se recuerda entre aperturas.

## Estado iOS rondas 2 a 13 (§11–§22, 2026-11-21, build 362; validado en ios-ci, pendiente de probar en el iPhone)

| Sección | iOS | Ficheros |
|---|---|---|
| §11 Rocódromos como Escuelas (mapa plegable 320, lista siempre, panel compacto «Ver N rocódromos», sin cadenas, sin agrupar, AlwaysOpen) | hecho | `GymsLayerView.swift`, `GymFilterRow.swift`, `GymsMapView.swift`, `GymPresentation.swift` |
| §11 Quedadas con rocódromo, grado y cordada / §12.1 nueva quedada en 3 pasos / §13.4 lugar fijado / §14.5 Bloque+Vía, material en baldosas | hecho | `CreateMeetupView.swift`, `MeetupWizardSteps.swift` (lógica: `MeetupDraft`) |
| §12.4 Escala (ficha y «Corregir» con editor) · §12.5 Llamar · §12.7 OSM · §12.8 fotos de la comunidad · §12.9 orden de la ficha | hecho | `GymDetailView.swift`, `GymDetailSections.swift`, `GymDetailTopCards.swift`, `GymReportSheets.swift` |
| §12.6 Aviso de roca mojada | hecho | `GymsNearbyBanner.swift` (`WetRockBanner`), `SchoolListView.swift` |
| §12.10 / §13 / §14 / §15 / §16 Chats (ChatTopBar, píldora, candado, burbujas con cola, vacío, avatar y nombre por racha en grupos, chat de rocódromo `gym-{id}`, píldoras y material en el de quedada) | hecho | `ChatKit.swift`, `GymChatView.swift`, `ChatView.swift`, `GroupChatView.swift`, `GroupChatParts.swift`, `SchoolChatView.swift` |
| §13.1–2 / §14.2–3 / §15.2 / §16.5 Ficha (fila de 4 iconos, tarjeta Aquí/Quién va, He entrenado + Crear quedada, servicios en tarjetas de color) y hoja de entreno (Bloques/Vías/Boards, rejilla de grados dobles, papelera) | hecho | `GymDetail*.swift`, `GymTrainSheet.swift`, `GymTrainRoutesBoards.swift` |
| §13.5 / §15.9 / §16.2 Detalle y tarjetas de quedada (cabecera de color, chips de nivel, semáforo de material, lugar y material en la lista), filtro Todas/Roca/Rocódromo | hecho | `MeetupDetailHero.swift`, `MeetupDetailView.swift`, `MeetupRows.swift`, `MeetupsView.swift` |
| §13.10 Selector «¿Dónde has entrenado?» | hecho | `GymPickerSheet.swift` |
| §14.4 / §15.5 / §15.7 / §16.6 / §17.6 / §18.4 Perfil propio y ajeno (cabecera compacta, 4 celdas, fichas, tarjeta de degradado Roca/Rocódromo, Tu/Su rocódromo, + Entreno, Siguiendo compacto con confirmación) | hecho | `AccountView.swift`, `AccountProfileBody.swift`, `PublicProfileBody.swift`, `UsersView.swift`, `ProfileClimbBlocks.swift`, `ProfileClimbModel.swift` |
| §14.10 Tu rocódromo en el servidor (Ajustes + hueco del perfil + migración) | hecho | `AccountSettingsView.swift` (`HomeGymSettingRow`), `ProfileClimbModel.swift` |
| §15.6 Ajustes en grupos redondeados | hecho | `AccountSettingsView.swift` |
| §14.1 / §14.8 / §15.1 Sectores plegables en tarjeta | hecho | `ExploreSectorHeader.swift`, `SchoolListView.swift` |
| §16b / §17 / §18 / §19 / §20 / §20c Estadísticas (Todo/Roca/Rocódromo), Ascensiones por rocódromo, visita, Boards por tablero plegados con grado máximo, orden de categorías y rocódromos (servidor + móvil), filtro por grado, nombres de board, pantalla Boards global, cuenta de Publicaciones | hecho | `GymClimbScreens.swift`, `GymStatsParts.swift`, `ProfileClimbModel.swift` |
| §20 / §20b Cola sin red (`syncPending`) | hecho | `ContributionOutboxFlusher.swift` |
| §21 Solo board, «+» por tablero, inclinaciones por board con la última usada (`BoardAngleMemory` sobre UserDefaults), filtro de ángulos | hecho | `SoloBoardSheet.swift`, `GymTrainRoutesBoards.swift`, `GymPickerSheet.swift`, `GymStatsParts.swift`, `GymClimbScreens.swift` |
| §22 Repetir bloques con nombre (×N, Repeticiones − ×N +), autocompletar con el catálogo compartido (SearchBoardProblemsUseCase, 250 ms), lista agrupada «×N · N días · última» | hecho | `BoardNameAssist.swift`, `GymTrainSheet.swift`, `GymTrainRoutesBoards.swift`, `GymStatsParts.swift` |

Hechos también (2026-11-21, segunda tanda): marcadores de anillo con gota (§12.12), tarjetas blancas con anillo de mojada (§12.13), pastillas seca/mojada y franja de 5 días (§13.7, §14.1), tarjeta de cristal de escuela en el mapa de Roca (§11.6, §12.11), círculo y chip de radio en Quedadas (§13.6; el círculo es solo el contorno, el mapa de iOS no pinta polígonos rellenos), «Rocódromos cerca» plegable (§14.9), hoja «¿Qué llevas tú?» (§13.5), seguidores en tarjetas (§15.8) y contadores «bloques»/«vías» en los grupos. «Corregir» de la tarjeta de cristal abre la hoja «Aportar» (piedra / escuela), como en Android.

## 22. Ronda 13 (2026-11-21, Android hecho; iOS hecho, ver «Estado iOS rondas 2 a 13»)

Repetir el mismo bloque con nombre (vueltas de entreno en Kilter, Moon, Tension, System) y autocompletar el nombre con un catálogo compartido. Lógica nueva en `shared` (no reimplementar en Swift): `BoardProblemRepeats` (`nameKey`, `clampReps`, `addRepeated`, `repeated`, `groupNames`, `history`, `suggestLocal`, `merge`), `RepeatedProblem`, `BoardProblemSuggestion`, `BoardProblemCatalogRepository` + `KtorBoardProblemCatalogRepository`, `SearchBoardProblemsUseCase` (`GymsModule.searchBoardProblems`: `local()` sin red y `search()` con `@Throws`). Tests: `BoardProblemRepeatsTest` (shared) y, en el backend, `GymBoardProblemRulesTest`, `SearchBoardProblemsUseCaseTest`.

1. **Repetir = una ascensión más.** Mismo nombre varias veces (el mismo día, otros días, con o sin rocódromo) es válido: cada repetición suma 1 al recuento de su board+grado y 1 nombre. El servidor ya lo permitía (`GymBoardProblemRules` gasta un recuento por nombre); no hay unicidad que lo impida. Límites: 20 repeticiones por añadido y 100 nombres por sesión (lo que sobra sigue contando en los recuentos; si no, el servidor da 400).
2. **Registro de boards** (`He entrenado` y `Solo board`): bajo el nombre sale la fila «Repeticiones − ×N +» (1 a 20) y el botón añade N ascensiones y N nombres. En «Añadido hoy» las repeticiones del mismo nombre se agrupan: «Fanjulandia ×3».
3. **Autocompletar tipo buscador de usuarios**: mientras se escribe (desde 2 letras, no una lista al enfocar) salen hasta 10 sugerencias bajo el campo, con grado, inclinación y «×N». Lo propio sale al instante (de las sesiones del móvil) y tras 250 ms se pide el catálogo (`GET /api/boards/problems/search?board=&q=`); propias primero, luego las que empiezan por lo escrito y luego las más hechas. Sin red solo lo propio. **Al elegir una sugerencia se rellenan nombre, grado y, si el catálogo la sabe, inclinación** (se pueden cambiar). Cambiar de marca relanza la búsqueda.
4. **Lista de bloques con nombre** (Boards del perfil propio y ajeno): una fila por board + nombre normalizado (sin mayúsculas, acentos ni espacios de más) + grado, con «×N» (total de repeticiones) a la derecha y debajo «rocódromo · N días · última <fecha>». Mismo nombre con otro grado o en otra marca son filas aparte. Buscador y filtro por ángulo siguen funcionando (el ángulo se aplica antes de agrupar). Si el servidor manda el historial recortado, el «×N» solo cuenta lo recibido: ahora manda hasta 1000.
5. **Catálogo compartido (backend V120)**: tabla `board_problem_catalog` (una fila por persona + board + nombre normalizado + grado + inclinación, así guardar dos veces no infla y la popularidad es «cuánta gente»). Se rellena con lo ya guardado (migración, sin inclinación) y en cada guardado de sesión o de «Solo board». La API devuelve solo `{board,name,grade,angle,count}`, nunca quién. La inclinación se deduce de las ascensiones: si ese board+grado solo tiene una inclinación en el guardado, se usa; si hay varias queda sin inclinación.

## 23. Ronda 14 (2026-11-21, Android hecho; iOS pendiente)

Feedback de Álvaro sobre Boards. Lógica nueva en `shared`: `ProblemDay(date, gymId, reps)` y `RepeatedProblem.days` (cada día + rocódromo con sus repeticiones, más reciente primero; test `repeatedKeepsEachDayWithGymAndRepsNewestFirst`). iOS debe leer `days`, no recalcularlo.

1. **Lista «Bloques con nombre»**: el subtítulo ya no lleva el rocódromo, solo «N días · última <fecha>» (el «N días» solo si hay más de una repetición). **Tocar un bloque ya no abre el rocódromo**: abre una hoja de detalle (`NamedProblemSheet`) con marca, grado, nombre, «×N» y una fila por día (más reciente primero) con «N repeticiones» y, pequeño debajo, el rocódromo o «Sin rocódromo». Solo lectura.
2. **Hoja de registro de boards** (el «+» de Boards y «Solo board»): se abre a altura completa (`skipPartiallyExpanded` + `fillMaxHeight` + `imePadding`, el área central hace scroll). El **campo de nombre con sugerencias va justo tras el título**, antes de Inclinación y Grado (con la marca fija; en «He entrenado», tras el selector de marca). Se quita la lista «Añadido hoy · N» de esta hoja (en «He entrenado» se mantiene); siguen la fila «Repeticiones» y «GUARDAR BOARDS · N». Los ángulos de la hoja NO cambian.
3. **Fila «ÁNGULOS» siempre visible** (variante B de la maqueta `maquetas-6/angulos.png`) en cada board con inclinaciones, sustituye a la fila plegable: etiqueta «ÁNGULOS» + fila de chips con scroll horizontal. «Todos» (con «N bloques» debajo) y cada ángulo registrado (ángulo arriba, cuenta pequeña debajo); el elegido va relleno en naranja. Filtra grados, máximo, cuenta y bloques con nombre como antes (`BoardAngleFilter`).

Capturas: `Desktop\rocodromos\capturas-android\ronda14` (03 ángulos, 04 detalle, 05 registro, 06 con teclado).
