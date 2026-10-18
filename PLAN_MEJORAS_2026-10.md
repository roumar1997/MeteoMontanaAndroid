# PLAN_MEJORAS_2026-10.md — Salud, restricciones, mejor hora, estadísticas y logros, historial, recompensas y 8a.nu

Plan acordado con Álvaro el 2026-10-17. **Estado (2026-10-18): B, C, D, E, F y Salud (A3/A4/A6) HECHOS en staging + rama de apps; pendientes A1/A2/A7 (bot de Telegram), A5 (analítica), ilustraciones de Cumbri, carga de restricciones verificadas, Android del panel admin y G (8a.nu).** Ver `HISTORIAL.md` 2026-10-18.
- Maquetas sobre capturas reales: https://claude.ai/artifact/BXvjS5aiUCgsQyXCymdv22
- Cumbri por niveles (boceto): https://claude.ai/artifact/3om4gQDQhfxPKUypH9cHZw

Reglas para todo (CLAUDE.md): backend primero (`develop` → staging → `main`), SOLID + hexagonal, contrato con las
apps ADITIVO, OK de Álvaro antes de cada commit/merge, una línea en HISTORIAL.md. **Lo del panel de ADMIN, primero en
iOS** (TestFlight + capturas) y luego Android. Lo demás, paridad simultánea.

## Decisiones cerradas

| Tema | Decisión |
|---|---|
| Restricciones | Solo admin. **Solo dentro de la escuela** (nada en la lista de escuelas). Pastilla «! 1 cerrado» junto al nombre; exclamación pulsable en el sector (lista de Bloques, etiqueta del mapa y su marca «S») y en sus bloques (también en el buscador y el asistente). Al tocar: hoja con motivo, fechas, a qué afecta y fuente. |
| Mejor hora por sector | Dentro de la tarjeta desplegable de la nota, como fila plegable más (igual que «¿Por qué este índice?»). Plegada: el mejor sector de hoy. Desplegada: cada sector con su franja de 8 a 20 h. |
| Estadísticas | Pantalla nueva desde Perfil con **objetivo del año** (se pregunta en Estadísticas y una vez al empezar cada año; opcional) y **logros**. |
| Confianza | 20 aportaciones aprobadas = se publica sin revisión. El admin ve cuántas lleva cada usuario y puede quitarla. |
| Recompensas | Insignia **Colaborador** con 1. **Título** bajo el nombre. **Cumbri en todos los perfiles** que evoluciona en 0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45 y 50 aportaciones; distinto si eres **viero o bloquero**. Las ilustraciones las hace Claude (boceto publicado, pendiente de visto bueno). |
| ¿Viero o bloquero? | **Opcional.** Nuevos: junto al nombre de usuario, se puede saltar. Ya registrados: una vez al abrir la app, tarjeta que se cierra. Siempre editable en Ajustes. |
| Alertas | Por Telegram. |
| Analítica | Firebase Analytics, actualizando privacidad (web), Play y App Store antes de publicar. |
| 8a.nu | Aplazado hasta tener el CSV. |

## Orden de trabajo

1. **A — Salud y observabilidad.**
2. **B — Restricciones** (con la investigación de abajo como primera carga).
3. **C — Mejor hora por sector.**
4. **D — Estadísticas, objetivo del año y logros.**
5. **E — Historial + aportaciones por usuario + confianza** (admin, iOS primero).
6. **F — Recompensas: insignia, título, Cumbri y «¿viero o bloquero?».**
7. **G — 8a.nu** (cuando llegue el CSV).

---

## A — Salud y observabilidad

**Hoy:** Crashlytics en Android e iOS (+ `HangReporter` en iOS), `/actuator/health`, logs de Railway y `admin_logs`
(solo acciones de admin). **Falta:** analítica, errores del servidor agrupados, aviso de caída, métricas y panel.

| Paso | Qué |
|---|---|
| A1 | **Bot de Telegram** (lo crea Álvaro, pasos al final). `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID` en Railway (prod y staging). Puerto `AlertNotifier` + adaptador `TelegramAlertNotifier`; la misma alerta como mucho una vez cada 30 min. |
| A2 | **Monitor externo** (UptimeRobot / Better Stack, gratis) contra `/actuator/health` de prod cada minuto, avisando por Telegram. Único aviso posible si Railway entero cae. |
| A3 | **Errores del servidor**: el `GlobalExceptionHandler` cuenta cada 500 por endpoint en `server_errors` (agregado por hora). Alerta si se pasa de N en 10 min. |
| A4 | **Métricas** con Micrometer: latencia y errores por endpoint, llamadas a Gemini y Open-Meteo, push enviadas/fallidas. Endpoint admin con el resumen de 24 h. |
| A5 | **Firebase Analytics** en las dos apps: pantallas y eventos clave (abrir escuela, buscar, apuntar, aportar, asistente). Antes: política de privacidad, seguridad de datos de Play, etiquetas de privacidad de App Store. |
| A6 | **Panel Salud** (admin, iOS primero): API, errores 24 h, cierres (enlace a Crashlytics), cupo de IA, Open-Meteo, push, usuarios activos del día. |
| A7 | **Alertas**: API caída, ráfaga de 500, Open-Meteo fallando, cupo de IA al 80 %, migración fallida al arrancar, cola de propuestas > 20. |

Verificación: apagar staging → llega Telegram; forzar un 500 en staging → aparece en panel y en Telegram.

---

## B — Restricciones

**Backend**
- Migración `restrictions`: `id, school_id, sector_block_id (null = toda la escuela), block_ids (json), kind (CLOSED |
  REGULATED | NOTICE), reason, starts_on, ends_on, yearly, source_url, source_name, verified_on, created_by, updated_at`.
- Dominio `Restriction.isActiveOn(date)` (con `yearly` compara mes/día; tests con periodos que cruzan año).
- `GET /api/schools/{id}/restrictions` y CRUD admin `/api/admin/restrictions`.
- `restrictionLevel` (null | REGULATED | CLOSED) en sectores, bloques y resultados de búsqueda (aditivo). **No** en la lista de escuelas.

**Apps**: pastilla junto al nombre, exclamación en sector/etiqueta/marca del mapa/bloques/buscador/asistente, hoja de
detalle. Pantalla de admin (iOS primero) con selector escuela → sector → bloques y fechas.

**Regla de carga:** solo se mete una restricción si se conocen **las fechas concretas** (o si es un cierre
permanente confirmado por la fuente oficial). Sin fechas → no se pone.

**Cómo funcionan las categorías de La Pedriza** (Resolución anual del BOCM; leída la de 2025, la de 2026 sigue el
mismo esquema): van **por sectores con nombre y por vías concretas**, no por zonas del mapa, así que encajan con los
sectores y bloques de Cumbre.
- **A** — sector donde no se permite escalar **nunca** (zonificación del Plan Rector del parque). En Cumbre: cerrado permanente.
- **B1** — solo **unas vías concretas** de ese sector, del 1 ene al 31 jul. En Cumbre: exclamación en esos bloques/vías, no en todo el sector.
- **B2** — **todo el sector**, del 1 ene al 31 jul. En Cumbre: sector cerrado con fechas.
- **B3** — no se puede **pasar por un camino** (viales y accesos) del 1 ene al 31 jul. En Cumbre: aviso en el sector al que da acceso.

**Solo se marcan sectores que existen en Cumbre.** Comprobado el 2026-10-18 en producción: los sectores de La Pedriza
en Cumbre (Dehesa inferior, La ermita, Quebrantaherraduras, Mancevilla inferior, El callejón, La Raja) **no aparecen**
en la resolución, que regula sectores de vías (El Indio, El Pájaro, Cueva de la Mora…). Hoy La Pedriza no mostraría
ninguna exclamación. En El Vellón sí hay coincidencia: el sector «Alcores» de Cumbre (normativa: Alcores 1 y 2). En
Pontón de la Oliva (Cumbre: «Stradivarius») tampoco coincide. Cuando alguien añada un sector regulado, el admin le
asigna la restricción.

**Investigación hecha (2026-10-17) — primera carga, a verificar contra la fuente oficial antes de publicar cada una:**

Fuente principal encontrada: **[Montaña Regulada](https://montanaregulada.org)**, proyecto sin ánimo de lucro de
montañeros que recopila la normativa por zona con periodos y fuentes oficiales (no es oficial; avisa de verificar).
No tiene API. **Antes de usar sus datos, escribirles** (contacto en su web) para pedir permiso o colaborar y citarles.

| Escuela del catálogo | Qué hay | Fuente |
|---|---|---|
| La Pedriza | Zona A: escalada prohibida todo el año en varios sectores y vías. Zona B: prohibida del 1 ene al 31 jul. Viales cerrados del 1 ene al 31 jul. Zona segoviana: solo sectores del Anexo III, sin magnesio. | Resolución de 10 mar 2026, BOCM nº 70 (24 mar 2026); Decreto 16/2019 CyL |
| Albarracín | Normativa del Gobierno de Aragón; sectores cerrados de forma permanente y zonas restringidas (un sector cerrado desde 2015); prohibido acampar y hacer fuego. Lista exacta en Montaña Regulada. | Montaña Regulada / theCrag |
| El Vellón | Sectores Alcores 1 y 2: suele prohibirse del 1 ene al 31 jul por nidificación. La Buhardilla: propiedad privada, vallado. | theCrag (remite a Montaña Regulada) |
| Pontón de la Oliva / Patones | Cañón de Uceda: apertura retrasada por pollos de buitre. 2026: prohibición ampliada hasta el 15 jul en 18 vías de los sectores Quebrantahuesos y Sangre. Prohibido el paso de coches por la vía de servicio del Canal. | theCrag |
| La Cabrera | Tiene regulación; **fechas por confirmar en Montaña Regulada antes de cargar.** | theCrag |
| Hoces del Duratón | Escalada no permitida dentro del Parque Natural (folleto del parque 2019). | Junta de CyL / patrimonionatural.org |
| Calpe (Peñón de Ifach) | No se escala del 1 abr al 30 jun por nidificación. Registro para el sendero en verano. | UKHillwalking (verificar con el parque) |
| Sant Llorenç del Munt | Zonas sin escalada todo el año y zonas cerradas del 1 ene al 31 jul. | NacióDigital 2022 (verificar vigente) |
| El Chorro / El Torcal | Zonificación de la escalada desde 2018 en los Parajes Naturales; tramos vallados por flora. | BOJA 2018 / Rockfax |
| Margalef | Desde oct 2025, barrera y reserva online de aparcamiento (5 €/coche). Aviso de acceso, no cierre. | Diari de Tarragona |
| Montsant / Siurana | Acuerdo de 2006 con regulación en época de cría. **Sin fechas: no se carga.** | Diari de Tarragona |
| Riglos, Rodellar, Alquézar, Vadiello | Espacios protegidos (ZEPA, quebrantahuesos). **Sin fechas: no se carga.** | Gobierno de Aragón |
| Resto (Montserrat, Chulilla, Montanejos, Sella, Cazorla, Monfragüe, Etxauri, Lumbier…) | Sin información encontrada aún. Revisar una a una en Montaña Regulada. | — |

---

## C — Mejor hora por sector

- Backend: por sector con orientación, de 8 a 20 h, sol/sombra de la pared (`SunPositionCalculator`) + temperatura,
  viento y humedad de la hora → puntuación con la fórmula del índice (`ClimbScoreCalculator`) ajustada. Mejor tramo =
  horas seguidas con mejor puntuación. `GET /api/schools/{id}/sector-hours?date=`.
- Tests: pared N mejor a mediodía en verano que pared S; pared SO mejor por la tarde.
- Apps: fila plegable dentro de la tarjeta de la nota; se pide al desplegar. Sin conexión: no se muestra.

---

## D — Estadísticas, objetivo del año y logros

- Cálculo en **shared** (`JournalStatsCalculator`, se amplía): funciona sin conexión.
- **Objetivo del año** (opcional): grado (bloque o vía), ascensiones y días de roca. `user_goals` en el servidor.
  Se pregunta desde Estadísticas («Poner objetivo») y una vez al empezar cada año.
- Pantalla: objetivo con progreso, máximos, % a vista/flash, comparación con el año anterior, mejor mes, pirámide de
  grados con colores y consejo, grado máximo por mes, cómo las haces, dónde escalas, logros. Filtros año/todo y bloque/vía.

**Catálogo de logros (primera versión, ~30, todos calculables con lo que ya se guarda):**

| Familia | Logros |
|---|---|
| Grado | Primer 6a, 6c, 7a, 7b, 7c, 8a (por separado en bloque y en vía) |
| Volumen | 10, 50, 100, 250, 500 ascensiones |
| Estilo | 10 flashes, 10 a vista, flash de tu grado máximo, a vista de tu grado máximo |
| Explorar | 5, 10, 25 escuelas distintas; 3 comunidades autónomas; 1 escuela fuera de España |
| Constancia | 4 fines de semana seguidos; 20 días de roca en un año; un año entero con al menos una salida al mes |
| Proyectos | Cerrar un proyecto; cerrar 5 proyectos |
| Comunidad | Primera aportación aprobada; primera beta subida; primera quedada; 10 votos de grado |

Se ven en Estadísticas, en tu perfil y en el de otros (los desbloqueados).

---

## E — Historial, aportaciones por usuario y confianza (admin → iOS primero)

- `content_history`: `id, entity_type (BLOCK | LINE | PHOTO), entity_id, school_id, actor_uid, source (ADMIN |
  CONTRIBUTION | TRUSTED | RESTORE), contribution_id, before_json, after_json, created_at`. Escrito en un único sitio
  por tipo, con test que falla si un guardado no deja historial.
- Admin: lista filtrable, diferencia antes/después y «Restaurar esta versión».
- `GET /api/admin/contributors`: aprobadas, rechazadas, pendientes, última, confianza. Bloque «SOLO ADMIN» en el perfil.
- `user_trust`: al aprobar la nº 20 → confianza automática + push. Con confianza se aplica directo (historial
  `TRUSTED`) y sale en «publicado sin revisión». El admin puede quitarla.

---

## F — Recompensas: insignia, título, Cumbri y «¿viero o bloquero?»

- `users.climber_type (ROUTES | BOULDER | BOTH | null)`. Pregunta opcional (nuevos: junto al nombre de usuario;
  existentes: tarjeta una vez; Ajustes siempre).
- Nivel calculado del nº de aportaciones aprobadas: 0, 1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50.
- Perfil público devuelve `climberType`, `level`, `title`, `badges` (aditivo). `title` elegible entre los desbloqueados.
- **Cumbri:** 12 niveles × 2 (vías / bloque), más «las dos». Las ilustraciones finales las genera Álvaro con Gemini con
  `Desktop\cumbre\cumbre-mascota\prompts-cumbri-niveles.md` (el boceto vectorial no llega a su nivel). Se exportan a PNG
  sin fondo y se suben al CDN de fotos (no van dentro de la app para no engordarla).
  Sin barba (neutro: ni chico ni chica). Dedos que engordan en cada nivel y pies de gato desde la 1.ª aportación.
  Viero: casco, arnés, cintas exprés, magnesera de cintura, cuerda, frontal y bandera. Bloquero: crashpad grande a la
  espalda desde el nivel 1, magnesera de bloque en el suelo, cepillo que crece, gorro, esparadrapo, rodillera y un
  segundo crashpad. Capa con 45 y **alas doradas con 50**.

---

## G — 8a.nu (aplazado)

Subir el CSV → buscar cada vía en el catálogo (alias de escuelas, sector, nombre por similitud, grado) → seguras /
probables / dudosas / no encontradas → el usuario confirma → solo se importan las que existen en Cumbre.

---

## Pasos del bot de Telegram (A1, los hace Álvaro)

1. En Telegram, abrir **@BotFather** → `/newbot` → nombre «Cumbre Salud» → usuario terminado en `bot`.
   Guarda el **token** que te da (es secreto: no lo pegues en chats ni en el código).
2. Abrir el bot nuevo y mandarle cualquier mensaje («hola»).
3. En el navegador, abrir `https://api.telegram.org/bot<TOKEN>/getUpdates` (con tu token) y apuntar el número que
   sale en `"chat":{"id": …}`: es tu **chat id**.
4. En Railway, servicio `MeteoMontanaAPI`, en staging y en production: variables `TELEGRAM_BOT_TOKEN` (el token) y
   `TELEGRAM_CHAT_ID` (el número). Así el token nunca pasa por el chat.
5. Prueba: cuando esté el código, se manda un mensaje de prueba desde staging y te tiene que llegar.
