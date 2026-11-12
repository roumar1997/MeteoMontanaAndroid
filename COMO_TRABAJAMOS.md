# Cómo trabajamos

Las normas de Cumbre en un solo sitio. Si algo de aquí choca con otro
documento, manda este (y se corrige el otro). **Cada norma vive en un solo
documento**: los demás enlazan aquí, no la copian. **Cada norma vive en un solo
documento**: los demás enlazan aquí, no la copian.

## 1. Hay gente usando la app

- La app está **publicada**. Cualquier cambio en el backend o en Firebase llega
  a usuarios reales.
- Commitear en la rama y subir a `develop`/staging no necesita permiso.
  **Pasar a `main`, a producción o a las tiendas, siempre con el OK de Álvaro**
  («sí, fusiona»).
- Los cambios del backend son **aditivos** (campos opcionales, endpoints
  nuevos) para que las versiones viejas de la app sigan funcionando.

## 2. Ramas y entornos

| Qué | Dónde se trabaja | A dónde va |
|---|---|---|
| Apps | rama `claude/**` | `main`, con «sí, fusiona» |
| Backend | `develop` → **staging** | `main` → **producción**, con «sí, fusiona» |

- El APK/IPA **debug** usa staging y el **release** usa producción. Lo decide el
  tipo de build, no la rama.
- Tras subir al backend: espera a que Railway despliegue **ese commit** y haz una
  prueba que solo pueda pasar con el código nuevo. Que `/actuator/health` diga
  UP no basta.

## 3. Calidad del código

- **SOLID, código limpio y arquitectura hexagonal siempre** ([ARCHITECTURE.md](ARCHITECTURE.md)).
  Lo que se toca se deja mejor que como estaba. Si hace falta un atajo, se
  avisa; nunca se cuela en silencio.
- **Tamaño máximo por fichero**: 600 líneas en las apps y 400 en el backend.
  Lo comprueban el CI y el pre-commit. Si un fichero se pasa, se parte por
  responsabilidades; no vale subir el límite.
- Cuando algo falla, se arregla **la clase de error y no solo el caso**: se
  busca el mismo patrón en el resto de la app. **Todo error arreglado lleva su test.**
- Todo se adapta a la pantalla: nada de medidas fijas. Hay que probar en la
  pantalla más estrecha.
- Código y nombres en **inglés**; textos, comentarios y documentos en **español**.

## 4. Antes de dar algo por bueno

1. Se lee el código existente antes de tocarlo (y `HISTORIAL.md` si el asunto
   tiene historia).
2. Tests y compilación en verde:
   `./gradlew :app:testDebugUnitTest :app:assembleDebug`, y en el backend
   `./mvnw test`. En este PC `contextLoads` falla porque no hay Postgres, y es
   normal.
3. Si se ve en pantalla, se instala y se mira de verdad.
4. Se apunta una línea en `HISTORIAL.md`, se actualiza «Estado actual» en
   `CLAUDE.md` y se pone al día `estado-cumbre.json`.

## 5. Pruebas con Álvaro

- **Primero pruebo yo**: tests, capturas automáticas y recorridos en el Xiaomi.
  A Álvaro solo le llega lo que no puedo comprobar.
- **Android antes que TestFlight.** Antes de lanzar un build de iOS: todo se
  prueba en Android (emulador: Maestro + a mano). Cada fallo que salga se
  arregla en Android **y se busca y corrige el mismo en iOS**. Solo cuando
  Android está en verde se lanza el TestFlight, y a Álvaro le llega solo lo
  propio del iPhone (avisando de lo corregido en iOS «por espejo»).
- La ronda se carga en el panel **«Pruebas»**, separada en **🍎 iPhone (i1, i2…)** y **🤖 Android (a1, a2…)**; cada punto: «Haz» (paso a paso) y «Debe pasar».
- Álvaro contesta **por audio en el chat** citando números. Claude resume lo que dijo en cada tarjeta (bien / a medias / falla), arregla con ese resumen y, en la ronda siguiente, el punto vuelve con **«Antes falló: …»**.
- Basta con decir lo que falla o va a medias; las capturas, en el chat con su número.
- Android se prueba en el **Redmi Note 12 Pro**.
- Las capturas de referencia se guardan en `Escritorio\paridad absoluta`.

## 6. Paridad entre iOS y Android

- Las dos apps tienen las **mismas pantallas, textos, orden y colores**.
- **Se hace primero en iOS**; cuando Álvaro lo valida, Android lo copia a partir de capturas.
- **Excepción, el panel de admin**: vive solo en iOS. No se porta a Android ni
  se piden capturas de admin. Lo que ve cualquier usuario (verificado, aureola,
  títulos) sí lleva paridad.
- **El diseño visual está terminado** (noviembre de 2026). Solo se retoca si Álvaro lo pide.

## 7. Preguntas y decisiones

- **Decido yo**: arreglos, orden interno del código, tests, documentación, commits en la rama y subidas a `develop`.
- **Pregunto siempre**: lo que cambie lo que ve el usuario, pasar a `main`/producción, publicar en las tiendas, y usar el nivel de razonamiento **máximo**.
- Las preguntas, pocas, numeradas y con mi recomendación delante. Se contesta por número.

## 8. Modelos y agentes

Elijo el modelo de cada tarea:

| Trabajo | Modelo |
|---|---|
| Búsquedas y cambios mecánicos | Haiku |
| Tests, revisiones y documentos | Sonnet 5.5 |
| Arquitectura y errores difíciles | Opus 5.5 en alto |
| Conversación y coordinación | Opus 5.5 en bajo o medio |

**Tripulación Mugiwara** (cómo nos llamamos): Álvaro es **Luffy** (capitán); Claude es **Zoro** (coordina y revisa). Ayudantes, por oficio: **Sanji** (arquitectura y decisiones difíciles, Opus alto), **Nami** (investigar y planear), **Robin** (leer código, inventarios, auditorías, docs), **Chopper** (arreglar fallos con su test), **Franky** (CI, frenos, Gradle, herramientas), **Usopp** (pruebas: Maestro, capturas), **Brook** (funciones nuevas de pantallas), **Jinbe** (despliegues y publicaciones). Cada ayudante se lanza con su nombre delante («Chopper: …») y así sale en el Taller.

El mod «Taller» enseña qué se está usando. El resumen de cada tarea dice qué usé y por qué.

## 9. Capturas y publicaciones

- Las capturas para Instagram son **siempre de iOS**.
- Las de referencia para la paridad van a `Escritorio\paridad absoluta`.
- Cada campaña tiene su carpeta `Escritorio\publicaciones <mes>`, con el orden y los textos. Máximo 5 hashtags. Hay que tapar correos, otras personas y la burbuja de la IA. Solo se publica cuando la versión ya está en las tiendas.

## 10. Publicar

- **Mismo número de versión público** en las dos tiendas (por ejemplo, 7.2).
- **Android**: el AAB se firma **en local** (el workflow de release falla con el
  keystore del secret). Antes se compila `:app:compileReleaseKotlin`. Los AAB
  se dejan en `Escritorio\AABs Play`.
- **iOS**: workflow manual «iOS PROD .ipa». Si App Store Connect contesta
  «train closed», sube el número 2.x de `project.yml`.
- **Forzar actualización**: `MIN_ANDROID_VC` / `MIN_IOS_BUILD` en Railway.
- Publicación **por fases**: 5 % → 20 % → 50 % → 100 %, revisando Crashlytics y las reseñas en cada paso.
- Las funciones nuevas con riesgo llevan **interruptor remoto**.
- Pasos completos en [DEPLOYMENT.md](DEPLOYMENT.md).
