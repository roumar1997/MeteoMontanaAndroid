# Rocódromos: tabla de textos (ES / EN)

> Fuente única para Android e iOS. Las claves son las de `app/src/main/res/values*/strings.xml`.
> En iOS se usan **exactamente** estos textos (mismos signos, mayúsculas y puntos suspensivos `…`). Los `%1$s`/`%1$d` son
> los argumentos en el orden indicado. Si cambias una frase aquí, cambia las dos plataformas y el `strings.xml`.
> Esta tabla se generó junto con los `strings.xml` y debe seguir siendo idéntica a ellos.

## Selector y vistas

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_layer_rock` | Roca | Rock | Selector de Escuelas |
| `gym_layer_gyms` | Rocódromos | Climbing gyms | Selector de Escuelas |
| `gym_view_map` | Mapa | Map | Alterna mapa/lista |
| `gym_view_list` | Lista | List | Alterna mapa/lista |
| `gym_search_hint` | Busca rocódromo, ciudad o cadena | Search gym, city or chain | Buscador |
| `gym_count_gyms` | %1$d rocódromos | %1$d climbing gyms | Contador de la lista (modo Rocódromos) |
| `gym_empty` | Ningún resultado con estos filtros | No results with these filters | Lista vacía |
| `gym_clear_filters` | Quitar filtros | Clear filters | Lista vacía |
| `gym_load_error` | No se pudieron cargar los rocódromos. Comprueba la conexión. | Could not load the gyms. Check your connection. | Error sin copia local |
| `gym_retry` | Reintentar | Retry | Error |

## Filtros de calle, favoritos, cerrados, entrenos y perfil (ronda 2026-11-19)

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_filter_street` | De calle (gratis) | Street (free) | Filtro: solo muros de calle |
| `gym_filter_closed` | Incluir cerrados | Include closed | Filtro: enseña los cerrados |
| `gym_filter_show` | Mostrar | Show | Título de sección de filtros |
| `gym_favs_chip` | FAVORITOS | FAVORITES | Chip de la fila de filtros |
| `gym_favs_chip_desc` | Ver solo tus rocódromos favoritos | Show only your favorite gyms | Accesibilidad del chip |
| `gym_closed_tag` | Cerrado | Closed | Etiqueta de rocódromo cerrado definitivamente |
| `gym_train_today` | He entrenado aquí hoy | I trained here today | Botón de la ficha |
| `gym_train_done` | Entreno de hoy apuntado | Today's session logged | Botón de la ficha, ya apuntado |
| `gym_train_undo` | Quitar | Undo | Deshacer el entreno de hoy |
| `gym_stats_title` | Rocódromo | Climbing gym | Tarjeta del perfil |
| `gym_stats_week` | Esta semana | This week | Tarjeta del perfil |
| `gym_stats_month` | Este mes | This month | Tarjeta del perfil |
| `gym_stats_year` | Este año | This year | Tarjeta del perfil |
| `gym_stats_top` | Más visitado | Most visited | Tarjeta del perfil |
| `gym_stats_top_value` | %1$s · %2$d veces | %1$s · %2$d times | Tarjeta del perfil (nombre, visitas) |
| `gym_stats_streak` | Racha | Streak | Tarjeta del perfil |
| `gym_stats_streak_one` | 1 semana | 1 week | Tarjeta del perfil |
| `gym_stats_streak_many` | %1$d semanas | %1$d weeks | Tarjeta del perfil |
| `gym_stats_empty` | Aún no has apuntado ningún entreno. Abre un rocódromo y pulsa «He entrenado aquí hoy». | You have not logged any session yet. Open a gym and tap "I trained here today". | Tarjeta del perfil vacía |

## Filtros (cápsulas)

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_filter_free` | Gratis | Free | Cápsula |
| `gym_filter_boulder` | Bloque | Bouldering | Cápsula |
| `gym_filter_rope` | Cuerda | Rope | Cápsula |
| `gym_filter_open_now` | Abierto ahora | Open now | Cápsula |
| `gym_filter_favorites` | Favoritos | Favorites | Cápsula (estrella) |
| `gym_filter_chain` | Cadena | Chain | Cápsula con menú; muestra el nombre al elegir |
| `gym_filter_chain_all` | Todas las cadenas | All chains | Primera opción del menú de cadena |
| `gym_filter_distance` | %1$d km | %1$d km | Cápsula de distancia con valor |
| `gym_filter_distance_any` | Distancia | Distance | Cápsula de distancia sin valor |
| `gym_filter_distance_any_option` | Sin límite | No limit | Opción del menú de distancia |

## Tipo, modalidad y estado

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_kind_commercial` | Comercial | Commercial | Etiqueta de tipo |
| `gym_kind_municipal` | Municipal | Municipal | Etiqueta de tipo |
| `gym_kind_free` | Muro de calle | Street wall | Etiqueta de tipo |
| `gym_pay` | De pago | Paid | Etiqueta de precio |
| `gym_free_badge` | GRATIS | FREE | Rótulo bajo el marcador verde |
| `gym_free_tag` | Gratis | Free | Etiqueta de precio |
| `gym_disc_boulder` | Bloque | Bouldering | Modalidad |
| `gym_disc_rope` | Cuerda | Rope | Modalidad |
| `gym_disc_both` | Bloque + cuerda | Bouldering + rope | Modalidad |
| `gym_disc_outdoor` | Muro al aire libre | Outdoor wall | Modalidad |
| `gym_open_until` | Abierto · hasta %1$s | Open · until %1$s | Estado; %1$s = HH:mm |
| `gym_closed` | Cerrado | Closed | Estado |
| `gym_closed_opens` | Cerrado · abre a las %1$s | Closed · opens at %1$s | Estado; %1$s = HH:mm |
| `gym_distance_km` | %1$d km | %1$d km | Distancia (>= 1 km) |
| `gym_distance_m` | %1$d m | %1$d m | Distancia (< 1 km) |

## Ficha

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_directions` | Cómo llegar | Directions | Botón principal |
| `gym_web` | Web | Website | Botón |
| `gym_instagram` | Instagram | Instagram | Botón |
| `gym_view_card` | Ver ficha | View details | Botón de la tarjeta del mapa |
| `gym_section_discipline` | Modalidad | Discipline | Título de sección |
| `gym_section_hours` | Horario | Opening hours | Título de sección |
| `gym_section_price` | Precio orientativo | Estimated price | Título de sección |
| `gym_section_services` | Servicios | Facilities | Título de sección |
| `gym_section_who` | Quién va hoy | Who is going today | Título de sección |
| `gym_hours_unknown` | Horario sin confirmar | Opening hours not confirmed | Sin horario |
| `gym_hours_mon_fri` | L–V | Mon–Fri | Etiqueta de días |
| `gym_hours_sat_sun` | S–D | Sat–Sun | Etiqueta de días |
| `gym_hours_sat` | S | Sat | Etiqueta de días |
| `gym_hours_sun` | D | Sun | Etiqueta de días |
| `gym_hours_daily` | Todos los días | Every day | Etiqueta de días |
| `gym_hours_line` | %1$s %2$s–%3$s | %1$s %2$s–%3$s | Línea de horario: días, apertura, cierre |
| `gym_price_single` | Entrada suelta ~%1$s € | Single entry ~%1$s € | Precio; %1$s = importe |
| `gym_price_pass` | %1$s ~%2$s € | %1$s ~%2$s € | Bono; %1$s = etiqueta del bono, %2$s = importe |
| `gym_price_pass_default` | Bono | Pass | Etiqueta si el bono no trae nombre |
| `gym_price_free` | Entrada gratuita | Free entry | Precio 0 |
| `gym_price_asof` | Revisado el %1$s | Checked on %1$s | Fecha del precio; %1$s = fecha local |
| `gym_price_unverified` | (sin verificar) | (unverified) | Tras el precio si la ficha no está confirmada |
| `gym_price_unknown` | Sin dato de precio todavía | No price data yet | Sin precio o sin fecha |
| `gym_service_autobelay` | Autoseguro | Auto-belay | Servicio |
| `gym_service_gear` | Alquiler de material | Gear rental | Servicio |
| `gym_service_kids` | Niños | Kids | Servicio |
| `gym_service_beginner` | Principiantes | Beginners | Servicio |
| `gym_yes` | Sí | Yes | Valor de servicio |
| `gym_no` | No | No | Valor de servicio |
| `gym_unknown` | Sin confirmar | Unconfirmed | Valor de servicio / verificación |
| `gym_verified` | Confirmado por la comunidad | Confirmed by the community | Estado de la ficha |
| `gym_osm` | © OpenStreetMap | © OpenStreetMap | Atribución (ODbL) si la fuente es OSM |
| `gym_favorite_add` | Añadir a favoritos | Add to favorites | Accesibilidad del botón de favorito |
| `gym_favorite_remove` | Quitar de favoritos | Remove from favorites | Accesibilidad del botón de favorito |

## Quién va hoy y quedadas

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_here_now` | %1$d en «Aquí» ahora | %1$d here now | Presencia |
| `gym_here_none` | Nadie ha marcado «Aquí» todavía | Nobody has marked “Here” yet | Presencia |
| `gym_here_join` | Unirme | Join | Botón para marcarte (usa el aviso de privacidad de Aquí) |
| `gym_here_leave` | Ya no estoy | I left | Botón para quitarte |
| `gym_here_error` | No se pudo actualizar «Aquí» | Could not update “Here” | Error de presencia |
| `gym_create_meetup` | Crear quedada aquí | Create meetup here | Botón de la ficha |
| `gym_meetup_default_name` | Quedada en %1$s | Meetup at %1$s | Nombre propuesto al crear la quedada |
| `gym_meetup_place_label` | Rocódromo | Climbing gym | Etiqueta en el formulario de quedada |

## Corregir y añadir

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_fix_cta` | ¿Algo no es así? Corregir | Something wrong? Fix it | Botón de la ficha |
| `gym_fix_title` | Corregir datos | Fix details | Título de la hoja |
| `gym_fix_what` | ¿Qué no es correcto? | What is not correct? | Etiqueta de los chips |
| `gym_issue_free` | Es gratis | It is free | Chip |
| `gym_issue_paid` | Es de pago | It is paid | Chip |
| `gym_issue_boulder` | Es de bloque | It has bouldering | Chip |
| `gym_issue_rope` | Es de cuerda | It has ropes | Chip |
| `gym_issue_closed` | Está cerrado | It is closed | Chip |
| `gym_issue_location` | Ubicación mal | Wrong location | Chip |
| `gym_issue_other` | Otro | Other | Chip |
| `gym_comment` | Comentario | Comment | Etiqueta |
| `gym_comment_hint` | Ahora hay entrada libre los domingos… | Now there is free entry on Sundays… | Ejemplo del campo |
| `gym_add_photo` | Añadir foto | Add photo | Botón |
| `gym_change_photo` | Cambiar foto | Change photo | Botón con foto elegida |
| `gym_optional` | opcional | optional | Nota junto a la foto |
| `gym_send` | Enviar | Send | Botón |
| `gym_review_note` | La revisará un admin | An admin will review it | Nota bajo Enviar |
| `gym_sent` | Enviado. La revisará un admin. | Sent. An admin will review it. | Confirmación |
| `gym_send_error` | No se pudo enviar. Inténtalo de nuevo. | Could not send. Try again. | Error |
| `gym_report_need_issue` | Elige al menos una opción o escribe un comentario | Pick at least one option or write a comment | Validación |
| `gym_add_missing` | Añadir un muro que falta | Add a missing wall | Cabecera del bloque de alta |
| `gym_new_name` | Nombre | Name | Etiqueta |
| `gym_new_name_hint` | Muro del parque… | Park wall… | Ejemplo del campo |
| `gym_new_place_note` | Se coloca en el centro del mapa · se publica tras revisión | Placed at the map centre · published after review | Nota del alta |
| `gym_new_place_here` | Ubicación: %1$s | Location: %1$s | Coordenadas elegidas |
| `gym_new_kind_free` | Muro de calle | Street wall | Chip del alta |
| `gym_new_kind_municipal` | Municipal | Municipal | Chip del alta |
| `gym_new_kind_commercial` | Comercial | Commercial | Chip del alta |
| `gym_new_title` | Añadir un muro | Add a wall | Título de la hoja de alta |

## Plan B

| Clave | ES | EN | Dónde |
|---|---|---|---|
| `gym_nearby_title` | Rocódromos cerca | Climbing gyms nearby | Plan B en la ficha de escuela con roca mojada |
| `gym_nearby_subtitle` | La roca está mojada. Plan B bajo techo. | The rock is wet. Indoor plan B. | Subtítulo del Plan B |
| `gym_nearby_row` | %1$s · %2$s | %1$s · %2$s | Fila: nombre · distancia |
