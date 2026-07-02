# Publicación y deploy de Cumbre (MeteoMontana)

> Backend, apps y tiendas ya están operativos. Este documento es ahora
> **referencia operativa** (cómo hostear/liberar una versión), no un roadmap.

## Arquitectura de producción

```
[Móviles Android / iOS]
   │
   ├── HTTPS ──→ [Backend Spring Boot @ api.climbingteams.com] ──→ [Postgres gestionada (Railway)]
   │
   └── ────────→ [Firebase: Auth / Storage / Firestore / FCM] (proyecto climbingteams)
```

- Backend + Postgres: Railway, proyecto `zoological-wisdom`, dos entornos
  (producción/`main` y staging/`develop` — ver `CLAUDE.md` sección Entornos).
- Firebase compartido entre staging y prod (solo backend+BD están aislados).
- Dominios: `climbingteams.com` (PWA + páginas legales/soporte),
  `api.climbingteams.com` (backend).

## Release Android

1. Subir `versionCode` en `app/build.gradle.kts` (nunca reutilizar uno ya
   subido a Play) y `versionName` si procede.
2. Generar el AAB firmado (contraseña del keystore en
   `Desktop\CUMBRE-CLAVE-FIRMA-GUARDAR.txt`, fuera de git):
   ```powershell
   $env:KEYSTORE_PASSWORD='...'; $env:KEY_PASSWORD='...'
   ./gradlew :app:bundleRelease
   ```
   → `app/build/outputs/bundle/release/app-release.aab`.
3. Play Console → tu canal de prueba → **Crear nueva versión** → subir el
   `.aab` → notas → Guardar → Revisar → Publicar. Google revisa políticas
   (minutos-horas); los testers reciben la actualización automática.
4. **Regla de Google para pasar a producción**: mínimo 12 testers opted-in
   durante 14 días seguidos en prueba cerrada.

Keystore: si se pierde, la app NO se puede volver a actualizar nunca en Play
— está respaldado en `Desktop\CUMBRE-FIRMA-BACKUP\` además del repo.

## Release iOS

Automatizado por CI, sin necesidad de Mac:
```
gh workflow run ios-prod-ipa.yml --ref main
```
Compila, firma (cert + perfil generados por API, no firma automática — evita
agotar el límite de certificados) y sube a **TestFlight** vía altool. Subir
`CFBundleShortVersionString`/`CFBundleVersion` en `iosApp/project.yml` antes
de lanzarlo (cada subida a App Store Connect exige un build number único y
mayor que el anterior).

## Legal / cuentas

- Política de privacidad y soporte: `climbingteams.com/privacy.html` y
  `/support.html` (repo `MeteoMontana`, la PWA).
- Play Console y Apple Developer: cuentas personales, ya aprobadas y activas.
- Borrado de cuenta (`DELETE /api/me` + UI): implementado, exigido por ambas
  tiendas.
