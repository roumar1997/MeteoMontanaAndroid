# Checklist para publicar Cumbre en App Store

> Estado: cuenta Developer aprobada, app creada en App Store Connect
> ("Cumbre Climbing"), pipeline de producción funcionando — el build llega a
> **TestFlight** y arranca en el iPhone con login Google OK. Bundle id real:
> `com.meteomontana.ios.3CP2YRJ579` (con el Team ID pegado; la app en ASC no
> admitía `com.meteomontana.ios` a secas). Ver memoria
> `project_ios_testflight_pipeline` para el detalle técnico del pipeline.

## Hecho

- [x] Cuenta Apple Developer aprobada y pagada.
- [x] App creada en App Store Connect (categoría Deportes + Viajes).
- [x] Firma manual (cert de distribución + perfil generado por API en CI) —
  la firma automática agotaba el límite de certificados de la cuenta.
- [x] Sign in with Apple: proveedor habilitado en Firebase Auth (código y
  entitlement listos). Pendiente probarlo en runtime.
- [x] `.ipa` de producción sube a TestFlight vía `ios-prod-ipa.yml`
  (`gh workflow run ios-prod-ipa.yml --ref main`), altool sin errores.
- [x] Borrado de cuenta (`DELETE /api/me` + UI, exigido por Apple desde 2022).
- [x] Página de soporte y privacidad desplegadas
  (`climbingteams.com/support.html`, `/privacy.html`).

## Pendiente

- [ ] **Capturas de pantalla** (mínimo 3, iPhone 6.5"/6.7") — hacerlas desde
  un build instalado vía TestFlight.
- [ ] **APNs** (push con app cerrada) — capability PUSH no disponible en el
  bundle id largo actual; opcional para v1.
- [ ] Rellenar ficha completa (descripción, keywords — ya redactados en
  sesiones previas, ver conversación) + Privacy Nutrition Labels.
- [ ] **Enviar a revisión** con un build de TestFlight ya probado.
