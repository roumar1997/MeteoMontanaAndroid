# Estado anterior (archivado el 2026-11-06)

Copia literal del bloque «Estado actual» de CLAUDE.md antes de resumirlo. El detalle de cada entrada está en HISTORIAL.md.


**📖 GUÍA DE LA ESCUELA + PDF EN ANDROID (2026-11-03) — compila, pasa tests, SIN PROBAR en el móvil y SIN COMMITEAR.** Pestaña BLOQUES = la guía de iOS (build 340) y su PDF sin casillas. Código en `ui/screens/detail/guide/` + `SchoolGuideHost.kt`; enlaces y JPEG del PDF en `data/guide/PdfFinisher.kt` (Android guarda las fotos sin comprimir). Probar con lo demás pendiente de Android, mirando sobre todo el peso del PDF y que los enlaces salten. Detalle en `HISTORIAL.md` 2026-11-03.

**🤖 ANDROID A LA PAR DEL ASISTENTE Y EL MAPA DE iOS (2026-11-01) — compila, pasa tests, SIN PROBAR en el móvil y SIN COMMITEAR.** Chat del asistente rehecho como iOS (bienvenida con Cumbri, burbujas, seguimientos), MI CASA, restricciones, viajes, clásicas, objetivo/logros, grado por altura, varias vías de golpe, aviso de mañana, quedada rellena, hoja de ascenso, filtros A VISTA/AL FLASH, tarjeta de sector/parking con «Seguir camino». Siguiente: probar en el Android de Álvaro con la checklist y, con su OK, commit (solo lo de esta sesión). Detalle en `HISTORIAL.md` 2026-11-01.

**✅ HOJA DE ASCENSO NUEVA (2026-10-31) — iOS build 317 a TestFlight (2.25.8), Android SIN PORTAR.** Marcar una vía abre una hoja corta con CUÁNDO, ESTILO, ¿QUÉ GRADO LE DAS? y un interruptor «Compartir en Comunidad» (apagado por defecto, recuerda lo último); un solo botón GUARDAR EN MI DIARIO / GUARDAR Y PUBLICAR. Se acabó el ajuste Preguntar/Siempre/Nunca. Pendiente: que Álvaro lo valide en el 317, y luego portar a Android pidiéndole capturas concretas. Detalle en `HISTORIAL.md` 2026-10-31.

**✨ HADAS Y DRAGONES (2026-10-20) — backend en PRODUCCIÓN (main f91fbab, V96), iOS 304 en TestFlight y VALIDADO, Android con lo mismo; AAB firmado `Desktop\AABs Play\cumbre-6.7-vc122.aab` (el vc121 de esa carpeta es anterior: NO subirlo).** Efecto animado alrededor de la foto que SOLO pone un admin (también a sí mismo, desde «MI TÍTULO Y EFECTO»); la persona se lo quita eligiendo otro título. Antes del AAB: `:app:compileReleaseKotlin` (el debug usa una Compose más nueva). Detalle en `HISTORIAL.md` 2026-10-19/20 (4ª vuelta).

**🗂️ ESCUELAS EN TARJETAS + DIARIO «⋯»/DESLIZAR + HORAS ARREGLADAS (2026-10-19, 3ª vuelta) — backend en PRODUCCIÓN (main fcd0a69), iOS 302 en TestFlight y VALIDADO, Android con lo mismo y VALIDADO en el Xiaomi (sin AAB).** Lo hecho después del 302 (arreglo de «Activos hoy» del admin en iOS y «FILTROS» que no se parte) va en el próximo build de iOS. **El panel de ADMIN se queda SOLO en iOS** (Álvaro, 2026-10-19): no portarlo ni pedir capturas de admin. Detalle en `HISTORIAL.md` 2026-10-19 (3ª vuelta).

**✔️ VERIFICADO + AUREOLA + DIARIO (2026-10-19, 2ª vuelta) — backend en PRODUCCIÓN (main 0985817, `POST /api/users/badges`), iOS build 299 VALIDADO y 300 (confirmar al borrar del diario) a TestFlight; Android con los mismos cambios, compila y pasa tests, SIN PROBAR en el móvil.** Siguiente: Álvaro prueba el 300, se le piden capturas CONCRETAS de iOS para Android, y checklist de Android (6.6 + esto) con el Xiaomi conectado. Pendiente suyo: quitar `TELEGRAM_BOT_TOKEN` de staging en Railway para que el bot solo avise de producción. Detalle en `HISTORIAL.md` 2026-10-19 (2ª vuelta).

**📣 BOT DE TELEGRAM + TÍTULOS ESPECIALES (2026-10-19) — backend en PRODUCCIÓN (main 1b6ca5f, V95), iOS build 297 a TestFlight, Android con los mismos cambios (sin AAB).** @cumbre_salud_bot avisa al arrancar y ante ráfagas de errores, y contesta /salud /hoy /nuevos /pendientes /resumen SOLO al chat de Álvaro (variables TELEGRAM_* en producción; en staging sin `TELEGRAM_WEBHOOK_URL`). Caída total → email de UptimeRobot. Títulos especiales con texto y color (admin, iOS) y «Sin título». Detalle en `HISTORIAL.md` 2026-10-19. Siguiente: que Álvaro valide el 297 y Android 6.6; Android del panel admin solo cuando lo pida.

**🧭 PLAN_MEJORAS_2026-10 — fases B, C, D, E, F y Salud HECHAS (2026-10-18): backend en PRODUCCIÓN (main 000f294, V89–V92), iOS 2.25.6 build 289 en TestFlight (el 290 prepara admin vertical, logros con dificultad y títulos nuevos), Android sin AAB.** Restricciones (con "!" solo dentro de la escuela, buscador y asistente), mejor hora por sector, objetivo del año + logros, historial de cambios con restaurar + confianza a las 20, recompensas (título, insignias, viero/bloquero) y panel de SALUD. Faltan: bot de Telegram (con Álvaro delante), ilustraciones de Cumbri (Álvaro con Gemini), 8a.nu (CSV), analítica (antes privacidad), cargar restricciones verificadas, y portar a Android lo de ADMIN tras validarlo en iOS. Antes de subir: `:shared:compileCommonMainKotlinMetadata` (APIs solo-JVM rompen iOS). Detalle en `HISTORIAL.md` 2026-10-18.

**🧗 VOTO DE GRADO POR ALTURA (2026-10-15) — backend en PRODUCCIÓN (V87), iOS build 283 en TestFlight, Android SIN PORTAR.** Cada persona elige una vez si mide más o menos de 1,70 m (mochilita, privado, solo en grupos) y sus votos de grado se separan por altura; si los dos grupos votan un grado muy distinto (≥3 votos cada uno y ≥2 pasos) el grado se parte en dos colores. La pastilla del grado ahora lleva su color y una flecha clara. Detalle y límites en `HISTORIAL.md` 2026-10-15. Pendiente: validar en iOS, luego portar a Android (junto con la bienvenida de "Continuar" que exigió Apple).

**🔐 ASISTENTE DE IA: SOLO ADMINS Y QUIEN ELLOS ACTIVEN (2026-10-14).** Backend en STAGING (V86, commit 877e561), apps en iOS build 280 y Android 6.5 (vc118). Ya NO es para todos: contesta si eres admin, si el interruptor global está abierto (apagado; sin pantalla aún) o si un admin te lo activó en TU perfil (bloque "SOLO ADMIN" en el perfil de otro usuario). **ANTES de subir las apps hay que llevar el backend a main/prod** (con "sí, fusiona" de Álvaro): sin la V86 en prod el endpoint de acceso da 404 y ni el admin ve la burbuja. Detalle en `HISTORIAL.md` 2026-10-14.

**🤖 ANDROID AL DÍA CON iOS (2026-10-14, compila, pasa tests y VALIDADO por Álvaro en el móvil; falta commit, CI y AAB).** Portado a la rama `claude/chat-notification-stone-drawing-6e1f50` (sin commitear al escribir esto): "? AYUDA" y guías por pantalla, bienvenida de una pantalla, Escuelas rehecha (cabecera, mapa, filtros plegados, chip de días), ficha con pestañas (BLOQUES con el explorador de shared, HOY compacta, notas ordenables, votos de grado) y el ASISTENTE DE IA (burbuja + chat + dictado). Detalle y trampas (permiso RECORD_AUDIO nuevo, mapa de la ficha, targets de guía del MAPA sin conectar) en `HISTORIAL.md` 2026-10-14. Siguiente: commit (con OK), CI en verde, AAB firmado en local y actualizar la declaración de seguridad de datos de Play (RECORD_AUDIO); restringir el asistente a admin SOLO cuando Álvaro lo pida. Para ver datos reales en un APK debug: `gradlew assembleDebug -PdebugApi=prod`.

**🧗 FICHA DE ESCUELA CON PESTAÑAS + "MÁS REPETIDOS" — iOS en TestFlight (2.25.4 build 270), backend en PRODUCCIÓN, Android SIN PORTAR (2026-10-13).** Ficha: puntuación compacta → 16 h → pestañas MAPA (por defecto) / BLOQUES(-VÍAS) / TIEMPO / NOTAS. BLOQUES: podio de los 3 más repetidos, chips de grado (varios) con su color, beta +1,70/−1,70 combinables, buscador por nombre, resto agrupado por SECTOR (plegable). Repeticiones = personas distintas con la vía HECHA en su diario (solo el total). Pendiente: portar TODO esto (y el asistente) a Android pidiendo a Álvaro capturas concretas de iOS; luego restringir el asistente a admin SOLO cuando él lo pida. Detalle y trampas en `HISTORIAL.md` 2026-10-13.

**🤖 ASISTENTE DE IA (burbuja + chat) — iOS en TestFlight (2.25.4 build 263), backend en
PRODUCCIÓN, Android SIN PORTAR (2026-10-08).** Burbuja flotante → chat (escribir o dictar) que
entiende frases naturales y contesta con DATOS REALES (tarjetas tocables, nunca texto inventado).
Hace: buscar vías/bloques (grado, escuela/s, sector, "cerca de mí", estrellas, beta +1,70/-1,70,
salida, con topo, "que no haya hecho"), comparar escuelas, recomendar dónde ir (sin lluvia, por
cantidad de vías), tiempo (lluvia/viento/humedad/temperatura/¿roca seca?), mejor día, ficha de
escuela, SU diario/favoritas/quedadas (se calcula EN EL MÓVIL: `MineAnswerer`, el diario no sale
del teléfono) y ACCIONES con CONFIRMAR (favoritas, abrir escuela, aviso de fin de semana, apuntar
en el diario hecha/proyecto). Cola de preguntas (máx. 5), REINTENTAR, listado completo y plegable.
**Arquitectura**: la IA (Gemini, detrás del puerto `AssistantQueryInterpreter`) SOLO entiende la
frase → `AssistantQuery`; todo lo demás es código con datos reales; el servidor devuelve estado +
datos y las apps escriben el texto ES/EN. Cupo: 15 preguntas/día/usuario (admin sin tope), 800/día
global, 10/min. Env en Railway: `GEMINI_API_KEY`, `GEMINI_MODEL` (opcional, admite varios por coma).
**TRAMPAS APRENDIDAS (leer antes de tocar la IA):**
- **Google va A RACHAS**: el mismo modelo tardó 1,3 s y a la hora 23 s; otro pasó de 503 a 1,6 s.
  NO elegir "el modelo bueno": `HedgedModelRace` lanza el siguiente a la vez si el primero tarda
  (2 s, 1 s en frío) y `ModelHealth` empieza por el más rápido ahora. Caché de 10 min de frases.
- Los modelos se equivocan con el CALENDARIO ("semana pasada" = "hace dos semanas"): las fechas las
  da el servidor (`calendarHints`, pasado para su diario y futuro para el tiempo). Y con el DICTADO:
  "7a" llega como "siete a" → `SpokenGrade` lee los grados DEL TEXTO (un grado con letra = exacto, "más/
  plus/+" pegado a la letra es el signo +, rangos, bandas "los sietes") y corrige a la IA; `SpokenDates`
  hace lo mismo con "hoy, mañana y pasado". Un seguimiento NO hereda escuela + "cerca de mí" a la vez.
- `AssistantQuery` crece con cada función: constructores secundarios mantienen los tests; el
  contrato con las apps es ADITIVO (apps viejas ignoran campos nuevos).
- Staging: filtrar por GRADO devuelve [] (sin `grade_score`); usar discipline/startType para probar.
**Banco de frases**: `GeminiPhraseSuite` (123 casos, `GEMINI_LIVE=1 ./mvnw test -Dtest=GeminiPhraseSuite`,
gasta cupo): 123/123. Cuando Álvaro diga una frase que falla, AÑADIRLA ahí antes de arreglar.
**NO adjuntar el build a ninguna versión de la tienda hasta que Álvaro valide el asistente.**
**PLAN (2026-10-13)**: (1) portar el asistente a ANDROID (la lógica de shared ya existe; falta la UI Compose); (2) cuando Álvaro diga
que Android queda igual que iOS, RESTRINGIRLO A ADMIN (teme costes; la clave de Gemini es de un proyecto sin facturación → no se
puede cobrar, solo limita). NO restringirlo antes de que lo pida.
**Pendiente (a su orden)**: aviso DIARIO de tiempo por la mañana (antes, comprobar que los push de iOS
llegan con la app cerrada — YA verificado en el build 34, 2026-07-03; no se ha repetido desde); guardar "mi casa" para "donde vivo"; respuestas por
voz; portar a Android tras validar iOS. Detalle y causas en `HISTORIAL.md` 2026-10-08.

**🧗 ICONOS VÍA/BLOQUE — HECHO y VALIDADO en iOS y Android (2026-10-06).**
Mosquetón = vías, crashpad = bloques, los dos si es mixta: lista de Escuelas,
pestaña Vías/Bloques y piedras del mapa (icono + chapa con el nombre corto).
Backend: `/api/search/lines` devuelve `discipline` por resultado (en PRODUCCIÓN).
Mismo día, también en PRODUCCIÓN (solo backend): roca seca/mojada ahora cuenta la
lluvia que YA cayó (`past_days=3` + `RockWetness`), no la prevista.
**Subida**: iOS **2.25.3 build 256** en TestFlight (probado); Android **6.4 vc117**,
AAB firmado en `Desktop\AABs Play\cumbre-6.4-vc117.aab` (instalado y probado en el
móvil; **pendiente que Álvaro lo suba a Play** y crear la versión en App Store
Connect con el build 256). Pendiente menor: vectorizar los dos iconos (hoy son PNG)
y mover `SchoolKind` de Swift a shared si se quiere un solo origen. Detalle en
`HISTORIAL.md` 2026-10-06.

**🔎 BUSCADOR POR GRADO — COMPLETO y VALIDADO en Android e iOS (2026-10-02).**
**Último estado de subida (2026-10-02):** Android **6.3.2 (vc116)**, AAB firmado
en `Desktop\AABs Play\cumbre-6.3.2-vc116.aab` (los vc114/vc115 de esa carpeta
son anteriores; Álvaro subía uno a Play ese día — confirmar cuál quedó
publicado); iOS **2.25.2 build 254** en TestFlight, y en App Store Connect
la versión **6.7** debe llevar la compilación **254** (no la 253). Capturas
de la ficha (iPhone 6,5") en `Desktop\capturas-appstore\`. **Trampa de
versiones iOS**: en App Store Connect las versiones públicas son 6.x pero el
build lleva `CFBundleShortVersionString` 2.x en `project.yml`; cuando una 2.x
se PUBLICA su tren se cierra y hay que subir a la siguiente (2.25.1 → 2.25.2).
`docs/BLOCK_SEARCH_DESIGN.md` completo:
Fase 1 (backend, en producción), Fase 3 (iOS) y Fase 2 (Android, en `main`,
**probada en el móvil con el build release firmado: "todo funciona
perfecto"**). Pestaña "Escuelas"/
"Vías/Bloques" en terracota — **el modo implícito sin pestaña del §4.1 se
probó primero en iOS y se descartó** tras feedback real de Álvaro ("no
funciona bien"); si se retoma esta función, usar SIEMPRE el patrón de
pestaña, no el implícito. Decisiones cerradas tras iterar varios builds de
iOS y replicadas 1:1 en Android: grado por DESPLEGABLE (Menu/DropdownMenu),
no slider ni chips, con el último min/max recordado (`UserDefaults` en iOS,
`SharedPreferences` en Android); SIN selector manual de escuelas en el radio
(se probó y se quitó, lista ingobernable — DISTANCIA ya filtra sola); grupos
de resultados por escuela empiezan PLEGADOS; "ORDENAR POR" es Cercanía/
**Mejores condiciones** (nunca "Grado" — el score es del cliente, se
reordena localmente, el backend siempre recibe `sort=DISTANCE`); miniatura
de vía con trazo fino sin badge (`MiniTopoThumbnail` en ambas plataformas,
nunca `TopoPhotoView`/`TopoPhotoCanvas` — ese badge es para el visor
grande). Bug de backend cazado: `PAGE_SIZE` de 30→300 en
`SearchLinesService.java` (una escuela cercana con muchas vías llenaba toda
la página), ya en producción. Android lleva también el botón de ubicación en
el mapa de Escuelas y el aviso "Sin ubicación — toca para activarla" bajo
DISTANCIA (paridad con iOS; si el permiso está denegado del todo abre
Ajustes). Detalle completo en `HISTORIAL.md` 2026-10-01/02.

**⏳ PENDIENTE DE SUBIR (2026-09-28) — Android vc112 (6.2) / iOS build 233.**
Repasada exhaustivamente la i18n ES/EN en las tres capas: Android, iOS y
backend. AAB firmado en `Desktop\AABs Play\cumbre-6.2-vc112.aab` (convención
nueva de carpeta, ver `LEEME.md` dentro); build 233 de iOS ya compilado por CI,
pendiente de subir a TestFlight desde el `.ipa` de esa run. Contenido: fix del
idioma que no se releía tras cambiarlo en marcha (`AppText`/`AppLanguage`),
`scoreLabel` del servidor sin traducir, mensaje de secado de la roca ahora
traducido (backend, YA en producción). Detalle en `HISTORIAL.md` 2026-09-28.

**🚀 APP LANZADA AL PÚBLICO (2026-07-10)**: Cumbre **2.12 pública en Google
Play (producción 100%) y en App Store (España/UE incluidas)**. Android + iOS
a paridad completa. Backend Spring Boot en Railway
(producción + staging separados, ver sección Entornos arriba). Features
completas: catálogo de escuelas + forecast + mapa + score, piedras/muros con
vías y modalidad Bloque/Vía, contribuciones con revisión admin, diario
personal, perfiles + follows + chat (1-a-1 y grupos), notificaciones push,
favoritas + widget, modo offline (SQLDelight + outbox de sincronización),
Quedadas (3ª pestaña: chat de grupo + privacidad + caducidad + material),
i18n ES/EN completo, alerta de tiempo/quedadas configurable, ayuda/onboarding
contextual, comparador de escuelas, admin completo (propuestas, gestión de
bloques, stats, logs, push manual).

**🎨 EN CURSO EN `develop` — "que Android no se vea antigua" (2026-08-10, SIN
VALIDAR EN DISPOSITIVO).** Solo Android y solo presentación: **cero ficheros de
`iosApp/` y de `shared/`** (verificado) → el build 119 de iOS no se toca.
1) **Fuentes DENTRO del APK** — causa raíz del problema que arrastraba desde
julio: el provider de Google Fonts va por Play Services y en MIUI cae a la
tipografía del sistema. La serif y la mono son los MISMOS `.ttf` que iOS;
Inter en variable. Fuera la dependencia `ui-text-google-fonts`.
2) **Armazón con material** (`CumbreChrome.kt`, regla nueva en `DESIGN.md` §1.9):
contenido plano, armazón con material. Barra + las **11 hojas**. Tres
tratamientos conmutables desde Perfil→Ajustes→"Aspecto de la barra"
(**TEMPORAL**: cuando Rodrigo elija, se fija y se borra el selector).
3) **Movimiento con muelle** (`CumbreMotion`) en vez de `tween(280)`.
4) **Pantalla de licencias** (obligación de OFL/Apache 2.0).
Haze clavada a **1.5.4** (la última con Compose 1.7; de la 1.6 en adelante pide
1.8+). BOM 2024.09→2024.12.
**PENDIENTE**: instalar en el Android moderno de Rodrigo (el Redmi es Android
11 → solo verá SÓLIDO) y **verificar el desenfoque sobre el mapa del radar**,
que es el riesgo abierto. Nada de esto llega a `main` hasta que lo valide.

**📦 EN LAS TIENDAS AHORA — Android vc96 (2.24.0) / iOS build 138**, confirmado
por Rodrigo el 2026-08-15: ambos subidos y aceptados. La actualización
obligatoria se fuerza desde Railway subiendo `MIN_ANDROID_VC` / `MIN_IOS_BUILD`
en el servicio `MeteoMontanaAPI` (el gate lo sirve `AppVersionController`).
El 2026-08-15 `MIN_IOS_BUILD` seguía en **117** → mientras no se suban esos
números, los usuarios se quedan tranquilamente en la versión vieja.

**🖼️ FOTOS — cómo se sirven (2026-08-15/16, EN PRODUCCIÓN).** Van por el dominio
propio **`photos.climbingteams.com`** (Custom Domain del bucket R2), **NO** por
el endpoint S3 (`r2.cloudflarestorage.com`) ni por `r2.dev`: ninguno de los dos
es alcanzable desde móviles reales, y eso tuvo las fotos rotas sin que se notara.
Se reducen a 1600px/q75 al subir (~350 KB en vez de 1,2 MB). **Si tocas una foto
ya subida, purga la caché de Cloudflare** (Caching → Purge Everything) o se
seguirá sirviendo la vieja hasta 4 h. Causa raíz, medidas y trampas:
`HISTORIAL.md` del repo API, sesión 2026-08-15/16.

**📦 RELEASE ANTERIOR — 2.22.0 (Android vc92 / iOS build 119), 2026-08-09/10.**
Contenido: aportar piedra desde una foto (EXIF → escuela), brújula en el mapa y
al votar orientación, chips del editor con el nombre de la vía, imán apagable,
apertura a Francia y Portugal (backend `V63__schools_country` + `/api/geo/countries`,
ya en PRODUCCIÓN) y arreglos de diario/mapas.
**Play tumbó la vc91** por su política de permisos de fotos y vídeos; la vc92 la
resuelve sin perder la feature — ver memoria `project_play_photo_picker_policy.md`
(resumen: el selector de FOTOS borra el EXIF, el de DOCUMENTOS no). AAB firmado
en `Desktop\cumbre-2.22.0-vc92.aab`. **iOS build 119 ya en TestFlight**, sin
tocar: la política es solo de Google. Queda: Rodrigo sube el AAB a Producción y
crea la versión 2.22.0 en App Store Connect con el build 119.

**📅 PLAN PARA LA SESIÓN DEL 2026-07-17 (acordado con Rodrigo — ir paso a paso, él pide cada uno):**
A. Cerrar release 2.18.5: (1) checks finales Xiaomi (compartir 2 posts distintos, piedra nueva con líneas, trazar muro), (2) checks iPhone build 88 (compartir piedra nueva, chat en foreground), (3) subir `Desktop\cumbre-2.18.5-vc64.aab` a Play, (4) ASC versión 2.18.5 con build 88.
B. Preparar Instagram automático: (5) IG de Cumbre → cuenta profesional, (6) página de Facebook vinculada, (7) Rodrigo pasa plantillas/ejemplos de historias.
C. Montar automatización (ver memoria `project_n8n_instagram.md`): (8) diseñar plantillas Cumbre, (9) endpoint datos + generador de imágenes en el backend, (10) app de Meta con instagram_content_publish, (11) n8n en Railway + workflow historia diaria condiciones Madrid, (12) workflow posts de piedras nuevas.

**🔜 Pendiente / acciones de Rodrigo (no bloqueante):**
- App Store: responder el **cuestionario de redes sociales** de la
  clasificación por edades (banner azul en la ficha) — límite 2026-09-07.
- Play Console: **descartar** el cambio residual "Prueba abierta / dejar de
  sincronizar países" en Resumen de publicación (menú ⋮ → descartar).
- Seguridad (menor): rotar contraseñas BD Railway + `DB_POOL_SIZE=25` en
  prod + `INVITE_SECRET` en Railway. Límites de `gearJson` → ver
  `docs/MejorasFuturas.md` Bloque 1 (magic bytes ya hechos 2026-07-07).
- Mejora continua post-lanzamiento: **`docs/MejorasFuturas.md`** (plan completo
  priorizado tras la revisión de arquitectura del 2026-07-09).
- **Monetización — ANTES de poner la app de pago/suscripción** (checklist
  completo en `docs/MejorasFuturas.md` §6.4): (1) contratar la API COMERCIAL de
  Open-Meteo (la gratis es CC-BY-NC, solo válida mientras la app no genere
  ingresos; el cambio es SOLO backend — las apps no llaman a Open-Meteo,
  no requiere release en tiendas); (2) DSA a "comerciante" en App Store
  Connect (verificación ~días, datos públicos en la ficha UE); (3) firmar
  el Acuerdo para apps de pago (Apple) / cuenta de comercio (Play).
- APNs (push iOS con app cerrada): **ACTIVADO y verificado** el 2026-07-03 (build 34; ver HISTORIAL.md 2026-07-03).
  Esta línea decía "falta activarla" y estaba desfasada.
- **Próxima release de iOS**: `PrivacyInfo.xcprivacy` declara ubicación
  APROXIMADA y la app envía coordenadas PRECISAS. Aplazado a conciencia el
  2026-08-09 (el build 119 ya estaba subido). Detalle en `docs/MejorasFuturas.md`
  → "PENDIENTE SUELTO — privacidad de iOS".

