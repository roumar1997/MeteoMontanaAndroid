# Plan de pendientes — lista de Rodrigo del 2026-08-15

> **Estado: NADA IMPLEMENTADO.** Cada punto está *investigado en el código* (no
> supuesto) y trae síntoma, causa real y solución propuesta. Se ejecutan cuando
> Rodrigo lo pida, uno a uno como siempre.
>
> Los pendientes de la sesión de fotos viven en `MejorasFuturas.md`
> ("PENDIENTE SUELTO — salidos de la sesión de fotos"), no se repiten aquí.

## Resumen: qué necesita publicar versión nueva

| Se arregla sin tocar las tiendas | Exige release |
|---|---|
| Nada de esta lista (todo es app) | **Los 11 puntos** |

De la lista de Rodrigo **todo es de app**. Lo único que se arregla en caliente
(backend/configuración) es lo de fotos, ya cerrado, y los cabos de
`MejorasFuturas.md`. **Conviene agrupar varios puntos en una sola release**: cada
una cuesta revisión de Play + Apple y pruebas en dos móviles.

**Tanda sugerida para la primera release**: puntos 5/10 (bug que sufren todos),
3 (función bloqueada), 8 (feo y trivial), 4 (muy pedido). Son los de mejor
relación impacto/esfuerzo y no se pisan entre ellos.

---

## 1. Grabar recorrido con la pantalla apagada

**Síntoma (Rodrigo)**: grabar un recorrido consume mucha batería; se quiere poder
bloquear la pantalla.

**Causa real**: en `ApproachRecordScreen.kt:105` la grabación es un
`LaunchedEffect` dentro de un `Dialog` de Compose, y muestrea la ubicación cada
segundo desde `rememberUserLocation`. **No hay servicio en primer plano ni
wake lock** (verificado: ni en la pantalla ni en el `AndroidManifest`). O sea que
el problema es peor que la batería: **al bloquear la pantalla la grabación se
detiene y se pierde el track**, porque el composable deja de estar activo.

**Solución**: mover la grabación a un **servicio en primer plano** con
notificación permanente ("Grabando aproximación · 12 min · 340 m"), que es el
mecanismo que Android exige para seguir recibiendo ubicación con la pantalla
apagada. La pantalla pasa a ser solo un visor del estado del servicio.
- Permiso `FOREGROUND_SERVICE_LOCATION` (obligatorio desde Android 14).
- El muestreo por tiempo (1/s) se sustituye por **distancia** (cada 5-10 m), que
  gasta mucha menos batería y da un track igual de bueno.
- Recuperar la grabación si la app muere a mitad (guardar los puntos según
  llegan, no solo al final).

**Esfuerzo**: alto (es la tarea más grande de la lista). **Ojo**: Play pide
justificar el permiso de ubicación en segundo plano en la ficha.

---

## 2. Recorrido en los dos sentidos + marcar inicio y final

**Síntoma**: que un recorrido grabado valga para ir *y* para volver
(parking→sector y sector→parking), y poder marcar en el mapa dónde empieza y
dónde acaba.

**Causa/estado**: **no es un fallo, es una función que falta.** Y la buena
noticia es que **la base de datos ya está preparada**: `V64__approaches.sql`
tiene `from_block_id` y `to_block_id`, y `Approach.kt` los expone. **No hace
falta migración.**

**Solución**:
- **Sentido inverso gratis**: un camino A→B es el mismo B→A con los puntos al
  revés. Basta un botón "Volver al parking" que pinte la polilínea invertida y
  renumere las chinchetas. Cero backend.
- **Inicio y final en el mapa**: pintar dos marcadores con los extremos del
  `pathJson` (o de `fromBlockId`/`toBlockId` si están). Al grabar, rellenar esos
  dos campos con el parking/sector más cercano al primer y último punto.

**Esfuerzo**: medio-bajo. Es de las que más se nota por lo poco que cuesta.

---

## 3. Android: el ✓ de aceptar la foto de perfil no se puede pulsar

**Síntoma**: al ponerse la foto de perfil, el tic de aceptar está tan arriba a la
derecha que no se llega a pulsar.

**Causa real (encontrada)**: la pantalla de recorte es **uCrop**, una actividad
de terceros, y en `AndroidManifest.xml:52-55` se declara con
`@style/Theme.AppCompat.Light.NoActionBar` — un tema antiguo que **no descuenta
los márgenes del sistema**. La app tiene `targetSdk = 36` y llama a
`enableEdgeToEdge()` (`MainActivity.kt:53`): desde Android 15 el sistema
**obliga** a dibujar de borde a borde, así que la barra superior de uCrop (donde
vive el ✓) queda **por debajo del reloj y del notch**, fuera de alcance.
Encaja exactamente con "está muy arriba a la derecha".

**Solución** (en orden de preferencia):
1. Definir un **tema propio para `UCropActivity`** que aplique los márgenes del
   sistema (`fitsSystemWindows`) y darle color de barra coherente con Cumbre.
   Es el cambio mínimo.
2. Si con el tema no basta (Android 16 ya no admite desactivar el borde a
   borde), **envolver uCrop** en una actividad propia que aplique los insets, o
   sustituirlo por un recortador propio en Compose (encaja mejor con el diseño,
   pero es bastante más trabajo).

**Verificación obligatoria**: probar en un móvil con notch **y** en uno sin él;
es un fallo que solo se ve en dispositivo real.

**Esfuerzo**: bajo (opción 1). **Impacto**: alto, ahora mismo bloquea del todo
poner foto de perfil.

---

## 4. Piedra con varias caras: saltar entre ellas sin scroll

**Síntoma**: en una piedra con varias caras, cada una con su foto y sus líneas,
hay que bajar scrolleando; se quiere un selector arriba para saltar.

**Causa/estado**: no es un fallo. Hoy `BlockDetailDialog.kt:299` pinta las caras
**una detrás de otra** con su cabecera "FOTO 1", "FOTO 2"… en una lista vertical.
Con dos caras es llevadero; con cuatro es incómodo.

**Solución**: una fila de pestañas **fija arriba** ("FOTO 1 · FOTO 2 · FOTO 3")
que se quede pegada al hacer scroll y lleve a esa cara. Dos variantes:
- **Barata**: la fila hace scroll hasta esa cara (se conserva la lista actual).
- **Mejor**: enseñar **solo la cara elegida** y cambiar con las pestañas o
  deslizando el dedo. Más limpio y carga solo una foto — encaja con lo de
  aligerar datos.

Recomendada la segunda, y aprovechar para **no descargar las fotos de las caras
no visibles** (hoy se bajan todas de golpe).

**Esfuerzo**: medio. Hay que hacerlo también en iOS por la regla de paridad.

---

## 5 y 10. Entras en una escuela/sector guardado y no carga nada

**Síntoma**: a veces entras a un sector guardado y no carga; una escuela guardada
apareció de repente sin ningún sector ni bloque. Sale bien al salir y volver a
entrar.

**Causa real (investigada a fondo)**:
- **El backend está sano**: `/api/schools/{id}/blocks` responde en **~0,5 s**
  siempre, y La Pedriza entera (125 KB) en 0,6 s. Medido cinco veces seguidas
  contra producción. **No es el servidor.**
- **`/api/schools/**` es PÚBLICO** (comprobado en `SecurityConfig.java:70`): para
  leer los bloques **no hace falta token**.
- **Y aquí está el fallo**: en `ApiHttpClient.kt:22` hay un plugin que en **cada**
  petición se para a pedirle el token a Firebase antes de enviarla. En frío
  (recién abierta la app) Firebase todavía está arrancando, así que la petición
  **se queda esperando un permiso que ni siquiera hace falta**. Además el cliente
  **no tiene ningún timeout configurado**.
- Lo del 2026-08-13/14 (`00990c50`, `de310c0e`) fueron **reintentos**: una tirita
  que tapa el síntoma. Ya está publicada, así que hoy falla menos, pero la causa
  sigue.

**Solución**:
1. **No bloquear las llamadas públicas esperando el token**: que el plugin pida
   el token solo cuando hace falta, o que no espere si Firebase aún no está
   listo (la petición sale sin token y el backend la sirve igual).
2. **Poner timeouts explícitos** en el cliente (conexión y lectura), para que un
   fallo sea un fallo rápido y no un cuelgue.
3. Con 1 y 2 dentro, **quitar los reintentos** o dejar solo uno: ya no harían
   falta y hoy disimulan errores de verdad.

**Esfuerzo**: bajo. **Impacto**: alto — lo sufren todos los usuarios en cada
arranque en frío. **Es el primero que yo haría.**

---

## 6. Los iconos se ven distintos en dos iPhones

**Síntoma**: dos iPhones muestran los iconos de forma diferente.

**Estado**: **sin investigar — faltan las capturas.** Rodrigo dijo que las
mandaría y no llegaron.

**Hipótesis a comprobar cuando lleguen** (por orden de probabilidad):
1. **Versión de iOS distinta**: los emojis y los símbolos del sistema (SF
   Symbols) cambian de dibujo entre versiones. Si en algún sitio se usan emojis
   como icono, se ven distintos sí o sí.
2. **Tamaño de letra / accesibilidad**: un iPhone con texto grande escala los
   iconos que dependan del tamaño de fuente.
3. **Builds distintos**: que uno tenga el 138 y el otro uno anterior.

**Qué hace falta**: las dos capturas y, de cada iPhone, **modelo, versión de iOS
y número de build de la app** (Perfil → abajo del todo).

---

## 7. Editar vías o fotos sin conexión: ¿se guarda y se reenvía?

**Síntoma/duda de Rodrigo**: al añadir vías o fotos y darle a "enviar cambios"
sin internet, ¿avisa de que se guardará y se enviará al recuperar cobertura,
como en otros sitios de la app?

**Estado**: **hay que comprobarlo pantalla por pantalla.** Lo verificado:
- La cola existe y funciona (`Outbox` en `Schema.sq:167`, `OutboxFlusher.kt`).
- **Proponer piedra nueva** sí tiene camino offline (`onSaveOffline` en
  `ProposeContributionFlow.kt:474`, tipo `CONTRIBUTION_BOULDER`).
- **Notas**: offline se encola **solo el texto y se pierde la foto** —
  `SchoolDetailViewModel.kt:277` lo dice literalmente ("subir la foto a Storage
  requiere conexión").
- **Editar una piedra existente** (añadir vías/fotos a una que ya está): no se
  ha localizado un camino offline equivalente.

**Solución**: auditar los flujos de escritura uno a uno y dejarlos **coherentes**:
o todos ofrecen "guardar y enviar luego", o los que no puedan lo dicen claro
*antes* de que el usuario rellene el formulario. Lo peor es lo de ahora, que
depende de por dónde entres. Se junta con el punto 11.

**Esfuerzo**: medio (la auditoría es lo que lleva tiempo).

---

## 8. Al hacer mucho zoom sale "map data not yet available"

**Síntoma**: haciendo mucho zoom en el mapa aparece ese mensaje. Rodrigo propone
limitar el zoom para que no llegue a salir — y es exactamente lo correcto.

**Causa real**: no hay **ningún límite de zoom máximo** configurado en la app
(comprobado: no aparece `setMaxZoomPreference` ni equivalente en ningún mapa;
solo se fija el zoom *inicial*, `zoom(15.0)`). Al pasar del nivel para el que el
servidor tiene teselas, MapLibre no tiene nada que pintar y muestra ese aviso.

**Solución**: fijar el **zoom máximo** al último nivel con teselas reales del
estilo que se use (satélite y mapa pueden diferir; típicamente 18-19, hay que
mirar el `maxzoom` del estilo). Ponerlo en el sitio común donde se crean los
mapas, no en cada pantalla, para que valga en todas.

**Esfuerzo**: muy bajo. **Aviso**: hay que comprobar el límite real del estilo,
no inventarse el número.

---

## 9. Al pulsar un parking, ver sus sectores y poder saltar a ellos

**Síntoma/petición**: igual que al pulsar un parking te acerca, que salgan debajo
los sectores, y al pulsar uno haga zoom a ese sector enseñando todas sus piedras.

**Estado**: **función nueva, y el dato ya lo tenemos.** No hace falta backend:
cada piedra ya trae `sectorBlockId` (`school_blocks.sector_block_id`) y los
sectores son bloques de tipo `ZONE`, así que agrupar piedras por sector es
inmediato en la app.

**Solución**:
- Al tocar un parking, en su ficha listar los sectores de la escuela con
  **distancia desde ese parking** y cuántas piedras tiene cada uno.
- Al tocar un sector, **encuadrar el mapa** en el conjunto de sus piedras
  (ajustar a los límites de esos puntos, no un zoom fijo).
- Encaja de forma natural con las **aproximaciones** (puntos 1 y 2): si hay
  camino grabado parking→sector, ofrecerlo ahí mismo.

**Esfuerzo**: medio. Es de las que más "producto" aportan.

---

## 11. Con 2 o más fotos sin conexión no se suben todas

**Síntoma**: al meter más de dos imágenes con líneas en cada una y subirlo sin
conexión, no se suben las dos imágenes.

**Causa probable (localizada, pendiente de reproducir)**: el diseño **sí**
contempla varias caras con foto (`QueuedFace.localPhotoPath`, y
`OutboxFlusher.kt:75` recorre todas las caras subiendo cada foto). Pero el punto
débil está en `QueuedBoulder.kt:50`, `copyPhotoToOutbox`: copia la foto elegida
al almacenamiento de la app y, **si falla, devuelve `null` sin decir nada**
(`runCatching { … }.getOrNull()`). Esa cara se encola **sin foto** y el usuario
no se entera hasta que ve el resultado. Es el único sitio del camino donde se
pueden perder fotos en silencio, y explica que "la primera sí y las demás no"
(los permisos de lectura de las URIs del selector pueden caducar para las
siguientes).

**Solución**:
1. **Que deje de fallar en silencio**: si una foto no se puede copiar, avisar
   ahí mismo y no dejar guardar a medias.
2. **Copiar la foto en cuanto se elige**, no al pulsar guardar — así el permiso
   de lectura sigue vivo y además el borrador sobrevive a que se cierre la app.
3. **Verificar antes de dar por buena la subida**: comprobar que todas las caras
   con foto acabaron con URL, y si no, dejar la fila en la cola en vez de
   borrarla.
4. Un test de la cola con 3 caras y 3 fotos (hoy no hay ninguno).

**Esfuerzo**: medio. **Impacto**: alto, se pierde trabajo del usuario, que es lo
más grave de toda la lista.

---

## 12. Guardar una escuela DEBE incluir sus fotos (prioridad de producto)

**Rodrigo, 2026-08-16**: *"es importante que el usuario pueda guardarse escuelas
con fotos, si no, nada nos diferencia de otras apps"*. Es la razón de ser del
modo offline: en la roca no hay cobertura y el topo **es** la foto.

**Causa**: `SavedSchoolRepository.saveOffline` guarda escuela, bloques, vías,
trazados y forecast, pero de la foto **solo el `photoPath`** (la URL en texto,
no los bytes). Las imágenes solo están si Coil las cacheó al mostrarlas antes —
y la caché de Coil se limpia sola cuando el móvil necesita espacio.

**El tamaño NO es problema** (medido en producción el 2026-08-16, ya con las
fotos reducidas a ~280 KB de media):

| Escuela | Piedras | Fotos | Peso |
|---|---|---|---|
| zarzalejo (la mayor) | 20 | 25 | **~7 MB** |
| santa-gadea | 11 | 15 | ~4 MB |
| la-pedriza | 11 | 11 | ~3 MB |
| el resto | 1-5 | 0-7 | < 2 MB |

**Todas las fotos de toda la plataforma juntas son 46 MB.** O sea que ni hace
falta pedirle al usuario que vaya alternando escuelas: puede guardarlas todas y
seguiría ocupando menos que un par de canciones. Aun así conviene enseñar el
peso y dejar borrar, por respeto al usuario, no por necesidad técnica.

**Solución**:
1. Al guardar una escuela, **descargar también las fotos** (piedras y caras) a
   almacenamiento propio de la app, con barra de progreso y posibilidad de
   cancelar. Guardar la ruta local junto al `photoPath` remoto.
2. Al pintar, **usar el fichero local si existe**; si no, la URL. Así funciona
   igual con y sin cobertura.
3. **Contenido nuevo en una escuela ya guardada**: `syncAllSaved`
   (`SavedSchoolRepository.kt:81`) ya re-descarga bloques y forecast y reemplaza
   el snapshot. Hay que **extenderlo a las fotos**: bajar solo las que falten
   (comparando `photoPath`) y **borrar las que ya no estén** para que no se
   acumule basura. Responde a la pregunta de Rodrigo: hoy los *datos* sí se
   actualizan, las *fotos* no existen.
4. Que se vea en la ficha **cuánto ocupa** y un botón de borrar.
5. Descargar solo con **wifi** por defecto, con opción de forzar con datos.

**Esfuerzo**: medio. **Ahora es viable**: antes de reducir las fotos, Zarzalejo
habrían sido ~30 MB; ahora son 7.

**OJO — no confundir con los puntos 5 y 10.** Que una escuela guardada "ni te
deje entrar y se quede cargando" **no tiene nada que ver con las fotos**: es el
bug del token (punto 5/10), y pasa igual con escuelas sin una sola foto. Son dos
arreglos independientes y el del token va antes.

---

## Orden que propongo

| # | Qué | Por qué ahí | Esfuerzo |
|---|---|---|---|
| 1 | **5 y 10** (token en frío) | lo sufren todos, causa localizada, arreglo pequeño | bajo |
| 2 | **3** (✓ de la foto) | bloquea una función entera | bajo |
| 3 | **8** (zoom del mapa) | trivial y se ve feo | muy bajo |
| 4 | **11** (fotos offline al subir) | se pierde trabajo del usuario | medio |
| 5 | **12** (guardar escuela CON fotos) | es lo que diferencia la app; ya cabe en 7 MB | medio |
| 6 | **4** (caras de la piedra) | muy usado, y de paso aligera datos | medio |
| 6 | **2** (recorrido ida/vuelta) | la BD ya está lista, sale barato | medio-bajo |
| 7 | **9** (parking → sectores) | function nueva golosa | medio |
| 8 | **7** (auditoría offline) | ordenar antes de crecer | medio |
| 9 | **1** (grabar con pantalla apagada) | la más grande; mejor sola | alto |
| — | **6** (iconos iPhone) | bloqueado: faltan capturas | ? |

Los cuatro primeros caben en **una sola release**.
