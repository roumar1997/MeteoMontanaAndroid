# Checklist para publicar Cumbre en App Store

> Estado: preparado al 95%. Solo falta la cuenta Developer ($99) y 3 pasos
> manuales que no se pueden hacer sin ella.

## Paso 0: Cuenta Apple Developer
- [ ] Ir a **https://developer.apple.com/programs/enroll/**
- [ ] Pagar $99/año con tu Apple ID
- [ ] Esperar aprobación (24-48h, a veces horas)
- [ ] Una vez aprobado, ir a **https://developer.apple.com/account**

## Paso 1: Sign in with Apple (obligatorio — Apple lo exige por ofrecer Google)
- [ ] developer.apple.com → Certificates → **Identifiers** → tu App ID →
      activar capability **"Sign in with Apple"**
- [ ] Firebase Console → Authentication → Sign-in method → habilitar **Apple**
      (necesita el Service ID de Apple)
- [ ] En `project.yml`: descomentar la línea `com.apple.developer.applesignin`:
      ```yaml
      com.apple.developer.applesignin:
        - Default
      ```
      (Ya está comentada en el fichero, solo quitar los `#`)
- [ ] El CÓDIGO ya está implementado (`AuthBridge.signInWithApple` + botón en
      `LoginView`). No hay que tocar código.

## Paso 2: Push Notifications (APNs)
- [ ] developer.apple.com → Keys → **Create a Key** → activar "Apple Push
      Notifications service (APNs)" → descargar el `.p8`
- [ ] Firebase Console → climbingteams → Project Settings → Cloud Messaging →
      **APNs Authentication Key** → subir el `.p8` + Team ID + Key ID
- [ ] En `project.yml`: descomentar:
      ```yaml
      aps-environment: production
      ```
      Y añadir bajo `info.properties`:
      ```yaml
      UIBackgroundModes:
        - remote-notification
      ```
- [ ] En `PushManager.swift`: cambiar `static let enabled = false` → `true`

## Paso 3: Crear la app en App Store Connect
- [ ] Ir a **https://appstoreconnect.apple.com**
- [ ] "+ New App" → iOS → nombre "Cumbre" → Bundle ID `com.meteomontana.ios`
      → SKU "cumbre-ios" → idioma principal: Español
- [ ] **Categoría**: Deportes (principal) + Tiempo (secundaria)

## Paso 4: Ficha de la app
- [ ] **Nombre**: Cumbre
- [ ] **Subtítulo**: Tiempo para escalar (max 30 chars)
- [ ] **Descripción**: (preparar ~170 palabras sobre qué hace la app)
- [ ] **Keywords**: escalada, boulder, tiempo, meteo, escalar, roca, montaña,
      quedadas, climbing, weather
- [ ] **Capturas de pantalla**:
      - iPhone 6.7" (iPhone 15 Pro Max): 3-6 capturas
      - iPhone 5.5" (iPhone 8 Plus): 3-6 capturas (pueden ser las mismas
        redimensionadas si no tienes el dispositivo)
- [ ] **Icono de la app**: 1024x1024 PNG (ya tienes logo_cumbre)
- [ ] **URL de privacidad**: https://climbingteams.com/privacy.html
- [ ] **URL de soporte**: https://climbingteams.com (o email soporte@climbingteams.com)

## Paso 5: Privacy Nutrition Labels (en App Store Connect)
Declarar lo que ya está en COMPLIANCE.md:
- [ ] Contact Info → Email Address: vinculado, funcionalidad
- [ ] User Content → Photos or Videos: vinculado, funcionalidad
- [ ] User Content → Other User Content (diario, notas, mensajes): vinculado
- [ ] Identifiers → User ID: vinculado
- [ ] Location → Coarse Location: vinculado, funcionalidad
- [ ] Diagnostics → Crash Data (si Crashlytics activo)
- [ ] **Tracking: NO**

## Paso 6: Compilar y subir el .ipa firmado
- [ ] En `project.yml`: poner tu DEVELOPMENT_TEAM:
      ```yaml
      DEVELOPMENT_TEAM: XXXXXXXXXX  # Tu Team ID de developer.apple.com
      CODE_SIGN_IDENTITY: "Apple Distribution"
      CODE_SIGN_STYLE: Automatic
      CODE_SIGNING_REQUIRED: "YES"
      ```
- [ ] Regenerar el proyecto: `xcodegen generate`
- [ ] Compilar: `xcodebuild -scheme MeteoMontana -configuration Release
      -archivePath build/Cumbre.xcarchive archive`
- [ ] Exportar: `xcodebuild -exportArchive ...` o usar Xcode Organizer
- [ ] Subir con **Transporter** (app gratuita de Apple) o `xcrun altool`

## Paso 7: Revisión y publicación
- [ ] En App Store Connect: seleccionar el build subido → "Submit for Review"
- [ ] Apple revisa en 24-48h (a veces más)
- [ ] Si rechazan: te dicen por qué, corriges y reenvías

## Notas
- **Borrado de cuenta**: ya implementado (`DELETE /api/me` + UI en ambas apps).
  Apple lo exige desde 2022.
- **Clasificación de edad**: sin contenido para mayores, sin compras in-app
  por ahora → probablemente 4+ o 12+ (por el chat).
- **Precio**: Gratis (la suscripción se añadiría más adelante).
