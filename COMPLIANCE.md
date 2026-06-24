# Cumbre / MeteoMontana — Cumplimiento, privacidad y marca

> Documento interno de trabajo. Resume QUÉ datos trata la app, cómo declararlos en
> las tiendas (Google Play Data Safety + Apple Privacy), el estado de cumplimiento
> RGPD y un informe de marca/propiedad intelectual con los pasos pendientes.
> Basado en auditoría de código (Android + backend `MeteoMontanaAPI` + iOS) del
> 2026-06-24. Documentos públicos: `privacy.html` y `terms.html` (repo PWA,
> servidos en climbingteams.com).

---

## 0. ACCIONES PENDIENTES (lo que falta para estar "perfecto")

- [ ] **Rellenar el responsable**: sustituir `[NOMBRE / RAZÓN SOCIAL DEL RESPONSABLE]`
  en `privacy.html` y `terms.html` por el nombre legal (persona física o, si hay,
  empresa). El RGPD exige identificar al responsable del tratamiento.
- [ ] **Publicar** `privacy.html` y `terms.html` y confirmar las URLs públicas
  (p. ej. `https://climbingteams.com/privacy.html` y `/terms.html`). Meterlas en
  Play Console (ficha + Data Safety) y en App Store Connect.
- [ ] **Borrado de cuenta completo**: hoy `DELETE /api/me` borra Postgres + Firebase
  Auth, pero **NO** borra (a) las conversaciones de chat en Firestore ni (b) las
  fotos en Firebase Storage. Decidir: implementarlo en `DeleteMyAccountUseCase`, o
  mantener el proceso manual ya documentado en la política (sección 9). Las tiendas
  exigen borrado de cuenta y declararlo.
- [ ] **Crashlytics**: la app SÍ recoge informes de fallos (plugin + dep en
  `app/build.gradle.kts`). Ya declarado en la política. Si NO quieres recoger
  diagnósticos, quita el plugin; si lo mantienes, hay que declararlo en Data Safety
  (ya contemplado abajo).
- [ ] **Verificar Firebase Analytics**: confirmar si la dep `firebase-analytics`
  está incluida (el SDK puede activar Analytics automáticamente). Si está, declarar
  "App interactions"/IDs; si no, no declarar. Revisar también en Firebase Console →
  Project Settings → Data collection.
- [ ] **AD_ID**: confirmar que NO se usa identificador publicitario. En el manifest
  no se declara `com.google.android.gms.permission.AD_ID`. En Play Console, en la
  pregunta de "Advertising ID", responder **No** (salvo que Analytics lo active).
- [ ] **Búsqueda de marca autoritativa** (ver sección 4): OEPM + EUIPO + tiendas
  antes de invertir en la marca "Cumbre".

---

## 1. INVENTARIO DE DATOS (resumen)

| Dato | Dónde se trata | Fin | Borrado con la cuenta |
|---|---|---|---|
| UID Firebase, email | Firebase Auth + Postgres `users` | Identidad/login | Sí (Postgres + Auth) |
| @username, displayName, bio, topGrade, isPublic | Postgres `users` | Perfil | Sí |
| Foto de perfil | Firebase Storage `profile-photos/{uid}` + URL en `users` | Avatar | **No automático** (manual) |
| Diario (escuela, sector, vía, grado, notas, fecha, modalidad) | Postgres `journal_sessions` | Registro de actividad | Sí |
| Favoritos | Postgres `favorites` | Tiempo en tus sitios | Sí |
| Follows / solicitudes | Postgres `follows` | Social | Sí |
| Notificaciones | Postgres `notifications` | Bandeja | Sí |
| Preferencias de alerta + **ubicación (lat/lon) si modo NEARBY** | Postgres `weekend_alert_prefs` | Alertas por cercanía | Sí |
| Token FCM | Postgres `users.fcm_token` | Push | Sí |
| Propuestas / submissions | Postgres `pending_contributions`, `school_submissions` | Catálogo colaborativo | Submissions sí; contributions se conservan (comunitario) |
| Notas comunitarias | Postgres `notes` | Comunitario | Sí (las del usuario) |
| Fotos de piedras/vías/notas | Firebase Storage | Contenido comunitario | **No automático** (manual) |
| Mensajes de chat (1‑a‑1 y grupo) | Firestore `conversations/*/messages` | Mensajería | **No automático** (manual) |
| Ubicación puntual (tab Tiempo, orden por cercanía) | Solo en memoria/petición; no se almacena | Tiempo y orden | N/A (no se guarda) |
| Diagnósticos/crashes | Firebase Crashlytics | Estabilidad | Lo gestiona Google (≈90 d) |
| Caché local (SharedPreferences, SQLDelight, Coil) | Dispositivo | Offline/rendimiento | Se borra al desinstalar |

**Terceros / encargados:** Google Firebase (Auth, Firestore, Storage, Cloud
Messaging, Crashlytics), Google Sign‑In, Apple (Sign in with Apple, pendiente de
activar en iOS), Railway (backend + Postgres), Open‑Meteo (solo coords de escuelas),
proveedores de mapas (OpenTopoMap/OSM/CartoDB/Esri), Resend (email), Cloudflare
Pages (web). **Sin publicidad ni tracking de marketing.**

**Diferencias iOS:** push (APNs/FCM) está **desactivado** (`PushManager.enabled =
false`) → en iOS no se recoge token push por ahora. "Sign in with Apple" está
implementado pero desactivado (requiere cuenta de pago); Apple lo **exige** si se
ofrece login con Google → activar antes de publicar en App Store.

---

## 2. GOOGLE PLAY — DATA SAFETY (respuestas exactas)

Play Console → Política → Seguridad de los datos. Responder así:

**Generales**
- ¿Recopila o comparte datos de usuario? **Sí, recopila.**
- ¿Se cifran en tránsito? **Sí** (HTTPS/TLS).
- ¿Pueden los usuarios solicitar la eliminación de datos? **Sí** (en la app: Perfil
  → Eliminar cuenta; y por email soporte@climbingteams.com).
- ¿Recopila datos de un identificador publicitario (AD_ID)? **No** (verificar
  Analytics; ver acción pendiente).

**Para cada tipo: "Recopilado" = Sí; "Compartido con terceros" = No** (Firebase y
Railway actúan como encargados/proveedores, no como terceros independientes). Marcar
"Recopilado" y, salvo indicación, no "Compartido".

| Categoría Play | Tipo de dato | Recopilado | Opcional/Obligatorio | Fines |
|---|---|---|---|---|
| Ubicación | Ubicación aproximada | Sí | Opcional | Funcionalidad de la app |
| Ubicación | Ubicación precisa | Sí | Opcional | Funcionalidad de la app |
| Información personal | Nombre | Sí | Opcional | Funcionalidad; función social |
| Información personal | Dirección de correo | Sí | Obligatorio | Funcionalidad de la app (cuenta) |
| Información personal | ID de usuario | Sí | Obligatorio | Funcionalidad de la app |
| Información personal | Otra info (bio, grado) | Sí | Opcional | Funcionalidad; función social |
| Fotos y vídeos | Fotos | Sí | Opcional | Funcionalidad; función social |
| Mensajes | Otros mensajes (in‑app) | Sí | Opcional | Funcionalidad de la app |
| Actividad en la app | Otro contenido generado por el usuario (diario, notas, propuestas) | Sí | Opcional | Funcionalidad de la app |
| Actividad en la app | Otras acciones (follows, favoritos) | Sí | Opcional | Funcionalidad; función social |
| ID de dispositivo o de otro tipo | ID de dispositivo (token FCM) | Sí | Opcional | Funcionalidad de la app (push) |
| Aplicaciones: rendimiento | Registros de fallos | Sí | — | Análisis / estabilidad |
| Aplicaciones: rendimiento | Diagnósticos | Sí | — | Análisis / estabilidad |

> Nota: "función social" = la opción de Play "Para mostrar a otros usuarios"
> aplica a nombre, foto, bio, grado, contenido público (solo si el perfil es
> público). El email NO se muestra a otros usuarios.

---

## 3. APPLE — PRIVACY NUTRITION LABELS (App Store Connect)

Coherente con `PrivacyInfo.xcprivacy` ya presente, pero **ampliar**: el manifiesto
actual solo declara Coarse Location, Email, Name y Photos. Faltan, para la realidad
de la app: **mensajes**, **contenido del usuario** (diario/notas), **identificadores
de usuario** y **diagnósticos**. Para App Store Connect declarar:

- **Contact Info → Email Address** — vinculado a identidad; función de la app.
- **User Content → Photos or Videos** — vinculado; función de la app.
- **User Content → Other User Content** (diario, notas, mensajes) — vinculado.
- **Identifiers → User ID** — vinculado.
- **Location → Coarse Location** — vinculado (precisa solo si activas cercanía).
- **Diagnostics → Crash Data / Performance Data** (si Crashlytics activo en iOS).
- **Tracking: NO.** No se usa para seguimiento entre apps.

> Recordatorio iOS: activar **Sign in with Apple** (obligatorio por ofrecer Google),
> el push (APNs) si se quiere, y firmar con cuenta Apple Developer real antes de
> subir. Ampliar `PrivacyInfo.xcprivacy` con los tipos que faltan.

---

## 4. MARCA Y PROPIEDAD INTELECTUAL — INFORME

### 4.1 Hallazgos preliminares
- **"Cumbre" es una palabra común del español** ("la cima de una montaña"). Por sí
  sola es **poco distintiva** y muy probablemente ya esté registrada por terceros
  en varias clases de Niza (es habitual en marcas de turismo, eventos, medios,
  software, etc.). Registrar el término "Cumbre" a secas para una app puede ser
  difícil y chocar con marcas previas. Las marcas fuertes son las distintivas.
- **Búsqueda en tiendas (no autoritativa):** no se encontró ninguna app de escalada
  llamada exactamente "Cumbre" en Google Play / App Store (sí apps de escalada con
  otros nombres: *Summit Escalada*, *Climb Around*, *Climbr*, etc.). Esto reduce el
  riesgo de confusión directa en la tienda, pero **no sustituye** la búsqueda oficial.
- **Doble nombre**: la app se muestra como "Cumbre" pero el paquete/proyecto es
  `com.meteomontana.android` / "MeteoMontana", y el dominio es `climbingteams.com`.
  Esa inconsistencia de marca conviene resolverla (elegir UNA marca principal).
  "MeteoMontana" es más distintiva que "Cumbre" y probablemente más registrable.

### 4.2 Cómo hacer la búsqueda oficial (gratis, ~30 min)
Hazla para "Cumbre", "MeteoMontana" y "Cumbre Escalada":
1. **OEPM (España)** — Localizador de marcas:
   `https://consultas2.oepm.es/LocalizadorWeb/` → busca por denominación. Mira las
   clases **9** (software/apps), **42** (SaaS, software como servicio) y **41**
   (formación/ocio deportivo), que son las relevantes.
2. **EUIPO (Unión Europea)** — eSearch plus:
   `https://euipo.europa.eu/eSearch/` → marca de la UE (cubre España + UE).
3. **TMview** (global, agrega muchas oficinas): `https://www.tmdn.org/tmview/`.
4. **Dominios y redes**: comprobar dominios `.com/.es/.app` y handles disponibles.
5. **Tiendas**: buscar "Cumbre" en Google Play y App Store por si aparece algo nuevo.

### 4.3 Recomendación
- Si "Cumbre" sale muy ocupado en clases 9/42: usar una marca **compuesta y
  distintiva** (p. ej. *"Cumbre — meteo para escalar"*, o consolidar en
  *"MeteoMontana"*), y registrar la combinación **palabra + logotipo** (marca mixta),
  que es más fácil de conceder.
- Clases a registrar si decides hacerlo: **9** (aplicación móvil descargable) y
  **42** (software como servicio / SaaS). Opcional **41** si hay componente de
  comunidad/deporte. Coste orientativo OEPM: ~125 € la primera clase + ~75 € por
  clase adicional (tasas, una sola vez; confirmar en la OEPM).
- Mientras tanto ya tienes **derechos de autor** automáticos sobre el código, los
  textos y el logotipo (no requieren registro), y la sección 6 de los Términos los
  reserva. La marca registrada es un plus para proteger el NOMBRE.

> Esto es orientación práctica, no asesoramiento jurídico. Para el registro formal o
> dudas de infracción, conviene un agente de la propiedad industrial.

---

## 5. RESUMEN DE DECISIONES PARA RODRIGO
1. ¿Nombre legal del responsable para meter en los documentos?
2. ¿Implementamos el borrado de chat+fotos en `DELETE /api/me`, o dejamos el paso
   manual documentado?
3. ¿Mantener Crashlytics (recoge diagnósticos) o quitarlo?
4. ¿Hacemos la búsqueda OEPM/EUIPO y decidimos marca única (Cumbre vs MeteoMontana)?
