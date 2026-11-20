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

## 11. Ronda 2 del emulador (2026-11-20, Android hecho; PENDIENTE PARA iOS)

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
