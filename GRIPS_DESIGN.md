# GRIPS_DESIGN.md — Pestaña "Agarres" (dinamómetro WH-C06)

> Diseño (sin implementar aún) de una **cuarta pestaña** "Agarres": conectar
> por Bluetooth una báscula-dinamómetro WH-C06 para medir fuerza de dedo,
> guardar tus máximos por tipo de agarre y mano, y crear entrenos
> personalizados (sets/reps/trabajo/descanso) con objetivos en % de tu máximo.
> LEER antes de empezar a implementar.
>
> Decidido con Rodrigo el 2026-07-02. Referencia técnica del protocolo BLE:
> [`TheLastKiwi/Dyna`](https://github.com/TheLastKiwi/Dyna) (app Android
> open-source para la misma báscula). Capturas de referencia (otra app,
> Grippulse/similar) para el layout del editor de sets — NO para copiar su
> taxonomía de agarres 1:1, solo el patrón de columnas/controles.

---

## 0. Resumen en una línea

Una **pestaña nueva** = conectar la báscula por BLE (sin emparejar) → medir tu
fuerza en vivo con gráfica → guardar tus **máximos** por combinación de agarre
y mano → crear **entrenos personalizados** (sets, reps, trabajo, descanso,
agarre, mano) con objetivos en % de tu máximo → guardar **historial** de
progreso.

---

## 1. El hardware — protocolo BLE (YA INVESTIGADO)

La WH-C06 es una báscula-gancho de cocina/industrial reutilizada por
escaladores como dinamómetro. **No usa GATT (no hay que emparejar/conectar)**:
simplemente **emite paquetes de publicidad BLE** (advertising) todo el rato,
~8 Hz, con nombre de dispositivo `"IF_B7"`.

**Cómo leer el peso** (verificado leyendo `DataCollector.java` /
`BTManager.java` del repo de referencia):

1. Escanear BLE filtrando por nombre de dispositivo `"IF_B7"` (o, más robusto,
   por el **manufacturer ID `256`** presente en el payload).
2. De los datos de fabricante (17 bytes), el peso está en los bytes 10-11:
   ```
   peso_kg = (unsigned(byte[10]) * 256 + unsigned(byte[11])) / 100.0
   ```
3. Bytes 9/14 indican la unidad que muestra la báscula físicamente (kg vs lb) —
   nosotros **siempre pedimos/mostramos kg**, ignoramos ese flag salvo aviso si
   detectamos que el usuario tiene la báscula en modo libras (posible mismatch
   de una cifra ×2.2, conviene validar con un aviso "¿tu báscula está en kg?").

**Consecuencias de diseño:**
- **No hay "emparejar" en Ajustes del móvil.** Es solo escanear + escuchar.
  Más simple de implementar y de usar (el usuario solo enciende la báscula).
- **Puede haber varias básculas cerca** (gimnasio de escalada con varias
  unidades) → si el escaneo encuentra más de un `IF_B7`, mostrar una lista
  (con intensidad de señal RSSI) para elegir cuál "bloquear" para la sesión
  (por MAC/identificador del anuncio). **Alias editable**: el usuario puede
  ponerle un nombre propio a cada báscula que haya usado (p.ej. "Mi báscula
  azul") para reconocerla rápido la próxima vez sin fijarse en RSSI/MAC —
  guardado **local** en el dispositivo (SharedPreferences/UserDefaults,
  clave = identificador del anuncio BLE), no hace falta backend para esto.
- **Sin conexión persistente**: si la báscula se apaga o se aleja, simplemente
  dejamos de recibir paquetes → mostrar "SIN SEÑAL" tras ~2s sin datos nuevos.
- **Pantalla siempre encendida** mientras se está en las pantallas de
  Conectar/Medir/Entrenar (`FLAG_KEEP_SCREEN_ON` en Android,
  `UIApplication.shared.isIdleTimerDisabled = true` en iOS, revertido al
  salir de esas pantallas) — imprescindible: el usuario tiene las manos
  ocupadas tirando de la báscula, no puede estar tocando la pantalla para
  evitar que se bloquee.
- ⚠️ **No verificado contra hardware real** (no tengo báscula física ni Mac).
  La primera prueba con la báscula de verdad la hace Rodrigo; puede haber que
  ajustar el offset de bytes o el manufacturer ID exacto tras probar.

**Permisos necesarios:**
- Android: `BLUETOOTH_SCAN` (Android 12+) o `BLUETOOTH`+`ACCESS_FINE_LOCATION`
  (versiones previas). Ya tenemos el patrón de pedir permisos en runtime
  (ubicación, notificaciones).
- iOS: `NSBluetoothAlwaysUsageDescription` en `Info.plist` (via `project.yml`,
  como las demás usage strings) + `CBCentralManager` en foreground (no
  necesitamos background modes: solo mientras la pantalla de Agarres está
  abierta).

---

## 2. Modelo de agarres — matriz combinable (decidido)

En vez de 5 agarres fijos, un **editor combinable** de dos ejes (como las
capturas que mandaste):

**Eje 1 — dedos/posición** (5 opciones):
- `FIVE` — 5 dedos (todos)
- `FOUR` — 4 dedos
- `THREE` — 3 dedos
- `FRONT_TWO` — 2 dedos frontales (índice + corazón)
- `MID_TWO` — 2 dedos centrales (corazón + anular)

**Eje 2 — estilo** (3 opciones):
- `CRIMP` — arqueo (full crimp)
- `HALF_CRIMP` — semi-arqueo
- `DRAG` — extensión / drag (open hand)

Un **"agarre"** = combinación de ambos ejes (ej. "4 dedos · semi-arqueo",
"2 dedos frontales · drag") → hasta 15 combinaciones. No todas tienen sentido
físico obligatoriamente (p.ej. "5 dedos · drag" es raro pero no lo bloqueamos,
que el usuario decida). Campo opcional **"borde (mm)"** (texto/número libre,
metadato informativo tipo "20mm" de las capturas — no afecta a la lógica, solo
contexto de qué canto usó).

Cada agarre se guarda **por mano** (izquierda/derecha) de forma independiente
— los máximos, targets y mediciones son por `(agarre, mano)`.

---

## 3. Modos de la pestaña

### 3.1 Medir ("Test de máximos")
- Conectar báscula → elegir agarre (eje1+eje2+borde opcional) → elegir mano →
  cuenta atrás → **gráfica en vivo** (fuerza vs tiempo) mientras el usuario
  tira → parar → muestra **pico** (máximo) y **media** de la serie → guardar
  como tu récord para ese `(agarre, mano)` (si supera al anterior, o siempre,
  a decidir en detalle de implementación — probablemente "guarda si es mayor
  que el guardado, o pregunta si quieres sobrescribir uno menor").
- Este es el modo que **alimenta los % de los entrenos personalizados**.

### 3.2 Entrenar ("Sesión personalizada")
- Editor de sets, mirroring el patrón de las capturas que mandaste:
  - **Cambio de mano**: `UNA` (toda la sesión con una mano fija) / `POR_SERIE`
    (alterna cada set) / `POR_REP` (alterna cada repetición).
  - **Contar por**: `TIEMPO` (aguantar X segundos) / `PESO` (tirar hasta
    alcanzar el peso objetivo, sin límite de tiempo — o con uno de seguridad).
  - Por cada **set**: nº de reps, tiempo de trabajo, tiempo de descanso,
    **agarre** (eje1+eje2), y un **rango objetivo mín%–máx%** de tu máximo
    (no un % único con tolerancia — un rango explícito, p.ej. "entre 10% y
    30%") → la app calcula el rango de kg objetivo (target range) a partir de
    tu máximo guardado de ese `(agarre, mano)` — igual que el "Target Range
    (kg)" L/R de las capturas.
  - **Feedback sonoro en vivo durante el trabajo**: mientras tiras, si la
    fuerza que marca la báscula se sale del rango objetivo (por debajo O por
    encima), la app **pita en bucle** hasta que vuelvas a estar dentro del
    rango — feedback sin mirar la pantalla. Usa el canal de audio normal del
    sistema (respeta silencio/volumen del móvil, no lo salta como una
    alarma). También se marca visualmente en la gráfica (verde=dentro,
    rojo=fuera del rango).
  - **Descanso global** entre sets (además del descanso por set).
  - **Edición masiva** (aplicar un cambio a varios sets a la vez) y
    **Reordenar** sets — igual que las capturas.
  - **Duración estimada** calculada en vivo según sets/reps/tiempos.
- Durante la ejecución: gráfica en vivo con la línea de objetivo + banda de
  umbral, cuenta atrás de trabajo/descanso, aviso de cambio de mano (con el
  **orden fijo izquierda → derecha** decidido).
- Los entrenos se pueden **guardar como plantilla** (nombre) para reutilizar.

---

## 4. Alternancia de manos — máquina de estados (CERRADO, confirmado con Rodrigo)

Regla dura: **nunca hay dos manos TIRANDO a la vez** (es físicamente
imposible con una sola báscula). El **descanso** de una mano SÍ puede correr
en paralelo mientras la otra tira — eso es lo que permite alternar sin
esperas tontas. Orden fijo siempre **izquierda → derecha**.

### 4.1 `UNA`
Toda la sesión con una sola mano (elegida al principio). Sin alternancia,
sin máquina de estados — ciclo simple `WORK → REST → WORK → REST...` de esa
mano.

### 4.2 `POR_SERIE`
**Dentro de cada set**: se hacen TODAS las repeticiones de ese set con la
mano **izquierda** (su propio ciclo work/rest, la derecha totalmente
inactiva/sin empezar), y **al terminar esa ronda completa**, se hacen TODAS
las repeticiones del mismo set con la mano **derecha**. Este patrón
(ronda izq completa → ronda der completa) se repite en **cada set** — no es
"el set 1 es izquierda y el set 2 es derecha", sino que **cada set** contiene
las dos rondas, izquierda primero siempre. Al ser secuencial (una ronda
entera acaba antes de que la otra empiece), **no hace falta la máquina de
estados de exclusión mutua** de la sección 4.3 — es simplemente dos
mini-series consecutivas.

### 4.3 `POR_REP` — la parte delicada
Cada repetición individual alterna mano: rep 1 izquierda, rep 2 derecha,
rep 3 izquierda... **Motor con dos cronómetros de mano independientes +
exclusión mutua sobre quién tira:**

- Cada mano tiene su propio contador de **descanso**, que arranca en el
  instante exacto en que ESA mano termina de tirar (no antes, no al empezar
  la sesión).
- Solo puede haber **una mano en estado TIRANDO** a la vez (variable global
  "mano activa"). Cuando una mano termina de tirar: (a) esa mano arranca
  su propio descanso configurado, y (b) el motor mira de inmediato si le
  toca a la OTRA mano (orden fijo alternante) y si su descanso propio **ya
  llegó a 0** → si sí, esa mano empieza a tirar YA (esto es lo normal: la
  izquierda empieza a descansar justo cuando la derecha empieza a tirar, en
  paralelo).
- Si a una mano le toca el turno pero su propio descanso **todavía no ha
  llegado a 0** (pasa si el descanso configurado es más largo que lo que ha
  tardado la otra mano en currar su turno), esa mano entra en un estado
  **"esperando turno"** — sigue contando su propio descanso hasta 0 y AHÍ
  empieza a tirar (nunca antes, aunque la otra mano ya esté libre).
- Caso límite explícito (el de "me distraje y los descansos están a 0"): con
  descanso=0, el "propio descanso ya a 0" se cumple al instante para ambas
  manos, así que lo único que decide el orden es la exclusión mutua + el
  turno fijo — la mano a la que le toca tira en cuanto la otra termina,
  nunca las dos a la vez, nunca se salta el orden.
- Indicador visual grande de qué mano toca AHORA (no basta un número).

### 4.4 Gráfica en vivo (aplica sobre todo a `POR_REP`)
**Un único eje de tiempo compartido**, con los tramos de la mano izquierda
en un color y los de la derecha en otro (p.ej. terra=izquierda,
azul/verde=derecha) — así se ve de un vistazo el patrón de alternancia
completo en una sola gráfica (huecos = descanso, color según qué mano tiró
en cada tramo). Aplica igual en `POR_SERIE` (aunque ahí el patrón es más
simple: un bloque entero de un color seguido de un bloque entero del otro).

---

## 5. Arquitectura — dónde vive cada cosa

| Pieza | Dónde | Por qué |
|---|---|---|
| **Lectura BLE de la báscula** | Nativo por plataforma (Android Kotlin BLE / iOS CoreBluetooth), con el mismo **patrón bridge** que `LocationProvider`/`AuthService`: interfaz `GripScaleBridge` en `iosMain` + implementación Swift; en Android, implementación directa en `app/` | Bluetooth no es multiplataforma en Kotlin puro; ya tenemos el patrón validado |
| **Máximos guardados + entrenos personalizados + historial de sesiones** | **Postgres** (backend) | Decidido: sync entre Android/iOS, sobrevive a reinstalar, coherente con el resto de la app (diario, favoritas...) |
| **Medición en vivo (stream de la sesión mientras mides/entrenas)** | Solo en memoria del dispositivo (no se manda al backend en tiempo real) | No hace falta tiempo real entre dispositivos; solo se sube el RESULTADO (pico/medias/serie resumida) al terminar |
| **Gráfica en vivo** | Librería de gráficas nativa por plataforma (Android: Vico o similar Compose-native; iOS: Swift Charts, ya en iOS 16+, encaja con deploymentTarget 17.0 actual) | Cada plataforma ya tiene buenas opciones nativas en Compose/SwiftUI, evita depender de una lib KMP inmadura para algo tan interactivo (60fps táctil) |

**Historial**: si queremos ver evolución en el tiempo, guardamos cada sesión
de MEDIR (no cada sesión de entrenar, salvo que se pida luego) con su pico y
fecha → permite graficar progresión de tu máximo por `(agarre, mano)` a lo
largo de las semanas/meses.

---

## 6. Modelo de datos (Postgres — propuesta)

```
grip_types                     -- catálogo fijo (no editable por usuario), sembrado por migración
  id            SERIAL PK
  finger_group  VARCHAR(16)    -- FIVE | FOUR | THREE | FRONT_TWO | MID_TWO
  style         VARCHAR(16)    -- CRIMP | HALF_CRIMP | DRAG
  UNIQUE(finger_group, style)

grip_max_records                -- tu máximo actual por agarre+mano (1 fila = el récord vigente)
  id            UUID PK
  uid           VARCHAR
  grip_type_id  FK -> grip_types(id)
  hand          VARCHAR(8)     -- LEFT | RIGHT
  max_kg        NUMERIC(6,2)
  edge_mm       VARCHAR(16) NULL   -- metadato opcional ("20mm")
  measured_at   TIMESTAMP
  UNIQUE(uid, grip_type_id, hand)

grip_measure_sessions           -- historial de cada test de MEDIR (para gráficas de progreso)
  id            UUID PK
  uid           VARCHAR
  grip_type_id  FK -> grip_types(id)
  hand          VARCHAR(8)
  peak_kg       NUMERIC(6,2)
  avg_kg        NUMERIC(6,2)
  duration_s    INT
  edge_mm       VARCHAR(16) NULL
  created_at    TIMESTAMP

grip_workouts                   -- plantillas de entreno guardadas
  id            UUID PK
  uid           VARCHAR
  name          VARCHAR(80)
  hand_mode     VARCHAR(16)    -- UNA | POR_SERIE | POR_REP
  count_mode    VARCHAR(16)    -- TIEMPO | PESO
  rest_between_sets_s  INT
  created_at    TIMESTAMP
  updated_at    TIMESTAMP

grip_workout_sets               -- sets de una plantilla, en orden
  id            UUID PK
  workout_id    FK -> grip_workouts(id) ON DELETE CASCADE
  sort_order    INT
  reps          INT
  work_s        INT
  rest_s        INT
  grip_type_id  FK -> grip_types(id)
  target_min_pct  NUMERIC(5,1) -- límite inferior del rango objetivo (% del máximo)
  target_max_pct  NUMERIC(5,1) -- límite superior del rango objetivo (% del máximo)

grip_workout_sessions           -- historial de ENTRENOS completados (opcional, fase posterior)
  id            UUID PK
  uid           VARCHAR
  workout_id    FK -> grip_workouts(id) NULL   -- null si fue ad-hoc sin plantilla
  completed_at  TIMESTAMP
  -- detalle por rep queda para una fase posterior si hace falta analítica fina
```

---

## 7. Endpoints backend (propuesta)

| Endpoint | Qué hace |
|---|---|
| `GET /api/grips/types` | Catálogo de combinaciones agarre (público/estático) |
| `GET /api/me/grip-maxes` | Tus máximos actuales por agarre+mano |
| `POST /api/me/grip-maxes` `{gripTypeId, hand, kg, edgeMm}` | Guarda/actualiza tu máximo (si `kg` > el guardado, o forzado) |
| `GET /api/me/grip-measure-sessions?gripTypeId=&hand=` | Historial de tests (para la gráfica de progreso) |
| `POST /api/me/grip-measure-sessions` | Sube el resultado de un test de MEDIR (peak/avg/duración) |
| `GET /api/me/grip-workouts` | Tus plantillas de entreno guardadas |
| `POST /api/me/grip-workouts` | Crear plantilla (con sus sets) |
| `PUT /api/me/grip-workouts/{id}` | Editar plantilla |
| `DELETE /api/me/grip-workouts/{id}` | Borrar plantilla |
| `POST /api/me/grip-workout-sessions` | (fase posterior) marcar un entreno como completado |

Todo bajo auth (Bearer Firebase), privado por uid — no hay nada público aquí
(a diferencia de escuelas/quedadas, esto es 100% personal).

---

## 8. Pantallas (propuesta, a bocetar/ajustar al implementar)

1. **Agarres (home de la pestaña)**: estado de conexión BLE ("Conecta tu
   báscula" / "Conectado a IF_B7 · -x dBm"), accesos a **MEDIR** y
   **ENTRENAR**, resumen de tus máximos recientes (grid pequeño tipo
   "4 dedos·semi IZQ 18.2kg / DER 17.9kg"), lista de plantillas de entreno
   guardadas, acceso a **progreso** (gráficas históricas).
2. **Conectar báscula**: escaneo BLE, lista de `IF_B7` encontrados con RSSI,
   tocar para "usar esta báscula" durante la sesión.
3. **Medir**: selector agarre (2 ejes + borde opcional) + mano → cuenta atrás
   → gráfica en vivo + pico/media → guardar.
4. **Editor de entreno** (espejo del patrón de las capturas): cabecera
   Cambio de mano / Contar por, tabla de sets con Reps/Work/Rest/Agarre/
   Rango objetivo (mín%–máx%), Edición masiva, Reordenar, Descanso global,
   Duración estimada, Guardar.
5. **Ejecutar entreno**: gráfica en vivo (verde=dentro del rango objetivo,
   rojo=fuera) + pitido en bucle mientras estés fuera de rango + indicador de
   mano activa muy visible + cuenta atrás de trabajo/descanso + progreso de
   sets/reps.
6. **Progreso**: gráfica de evolución de tu máximo por agarre+mano a lo largo
   del tiempo (elegir combinación → línea temporal).

---

## 9. Fases de implementación (propuesta)

> Backend primero, luego shared/modelos, luego el bridge BLE (arriesgado, sin
> hardware para probar hasta que Rodrigo lo valide), luego UI Android, luego
> paridad iOS. Pedir OK antes de cada commit/merge (testers en vivo).

- **Fase 0 — Investigación protocolo BLE**: ✅ hecha (este documento).
- **Fase 1 — Backend**: migración Postgres (tablas de arriba, catálogo
  `grip_types` sembrado), endpoints de máximos + historial + plantillas.
  Tests.
- **Fase 2 — Shared (KMP)**: modelos (GripType, GripMaxRecord,
  GripWorkout...), use cases, cliente Ktor, puerto `GripScaleBridge` +
  `IosDependencyContainer`.
- **Fase 3 — Bridge BLE Android**: escaneo + parseo del protocolo,
  probado por Rodrigo con la báscula real cuanto antes (para pillar
  pronto si el offset de bytes necesita ajuste).
- **Fase 4 — Android UI**: 4ª pestaña, conectar báscula, Medir, Editor de
  entreno, Ejecutar entreno (máquina de estados de manos), Progreso.
- **Fase 5 — Bridge BLE iOS** (CoreBluetooth) + **Fase 6 — iOS UI**: paridad
  exacta, verificado por CI (sin Mac) + prueba real de Rodrigo con AltStore/
  TestFlight (el Bluetooth SÍ se puede probar sin Mac, ya que corre en el
  iPhone, no en el simulador de CI).

---

## 10. Decisiones abiertas (resolver al empezar / durante la Fase 3-4)

- ~~**`POR_SERIE`**~~ — **CERRADO**: dentro de cada set, ronda izquierda
  completa → ronda derecha completa (ver sección 4.2).
- ~~**Alternancia `POR_REP`**~~ — **CERRADO**: máquina de estados de la
  sección 4.3, confirmada con el ejemplo de Rodrigo.
- ~~**Gráfica con dos manos**~~ — **CERRADO**: un eje de tiempo, dos colores
  (sección 4.4).
- ~~**Nombre de báscula / pantalla bloqueada**~~ — **CERRADO**: alias local
  editable + wake-lock (sección 1).
- ~~**Guardar máximo**~~ — **CERRADO**: si mides menos que tu récord actual,
  no se sobrescribe ni interrumpe con ningún aviso — se guarda igual la
  sesión en el historial (para la gráfica de progreso), pero el "récord" solo
  se actualiza cuando superas el máximo guardado.
- ~~**Feedback de rango objetivo**~~ — **CERRADO**: rango explícito
  mín%–máx% (no un % único con tolerancia) + pitido en bucle mientras estés
  fuera del rango, por el canal de audio normal (respeta silencio del
  sistema).
- ~~**Modo "Peso" en Contar por**~~ — **CERRADO**: sin límite de tiempo
  automático. El set sigue hasta que el usuario lo para manualmente (botón
  "Listo"/"Parar") — no se corta por timeout, porque el objetivo es una guía,
  no una obligación (algún día puedes estar más flojo y querer ir más suave).

## 11. Detalles de implementación a decidir sobre la marcha (no bloquean empezar)

- **Librería de gráficas**: a elegir en la Fase 4 (Android) — revisar Vico
  (Compose-nativo, activo) vs MPAndroidChart (la que usa el repo de
  referencia, Java/View-based, funciona pero no es Compose-idiomático).
- **Un usuario que no tenga máximo guardado para un agarre**: al crear un
  entreno con "% del máximo" para ese agarre, ¿bloqueamos con aviso "mide
  antes este agarre", o dejamos poner un kg absoluto en vez de %? (Por
  defecto: bloquear con aviso — es más simple y evita entrenos con targets
  sin sentido; se puede relajar más adelante si hace falta.)

---

## 12. Cómo empezar en una sesión nueva (PROMPT)

> Crea la sesión **con los DOS repos** (`MeteoMontanaAndroid` + `MeteoMontanaAPI`)
> y pega esto:

```
Vamos a implementar la 4ª pestaña "Agarres" (dinamómetro BLE WH-C06 para medir
fuerza de dedo + entrenos personalizados). Lee primero GRIPS_DESIGN.md en la
raíz del repo Android — diseño completo, incluye el protocolo BLE ya
investigado (sección 1) y el modelo de datos (sección 6).

Contexto importante:
- Backend en vivo con testers (Railway prod = main, staging = develop). PIDE OK
  antes de cualquier commit/merge, sobre todo en el backend. Trabaja primero
  contra staging.
- El protocolo BLE de la báscula NO está probado contra hardware real — en
  cuanto haya un bridge Android mínimo, avisa para que Rodrigo lo pruebe con
  su báscula cuanto antes (puede necesitar ajustar el offset de bytes).
- Sigue el orden de fases del documento (sección 9). Resuelve las decisiones
  abiertas de la sección 10 conmigo ANTES de programar la máquina de estados
  de alternancia de manos (sección 4) — es la parte más delicada.
- Reutiliza el patrón "bridge" ya usado para LocationProvider/AuthService
  (interfaz en iosMain + implementación Swift) para el Bluetooth de iOS.

Empieza por la Fase 1 (backend: migración + endpoints). NO empieces a programar
hasta confirmar conmigo el plan de la Fase 1.

Modelo: Sonnet para el grueso (CRUD, migraciones, UI mecánica); Opus si hay
una decisión de arquitectura ambigua o la máquina de estados de manos da
problemas.
```
