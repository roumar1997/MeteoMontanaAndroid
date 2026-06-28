# Quedadas — pruebas pendientes (sesión 2026-06-28)

> Estado: backend en **staging** (`develop` de MeteoMontanaAPI, V36 + endpoints
> nuevos) y app (Android+iOS) en **`main`** de este repo. Probar con el **APK
> debug de GitHub Actions** (apunta a staging) y el **`.ipa`** de iOS (AltStore).
>
> El APK/`.ipa` instalados ANTES de esta sesión NO llevan estos cambios.

## Qué se implementó en esta tanda
- Backend (staging): columna `description` (V36); `PATCH /api/meetups/{id}`
  (editar descripción, solo organizador); `GET /api/meetups/by-conversation/{convId}`
  (abrir detalle desde el chat); `MeetupDto` con `description` + `schoolLat/Lon`.
- App (Android+iOS): pulsar quedada ya unida → chat directo; detalle con
  descripción editable + "Cómo llegar"; chat de grupo con título → detalle,
  menú Silenciar (local) + Cómo llegar; deep-link de push de grupo abre el chat;
  push silenciado no se muestra (Android).

## Checklist de pruebas (⚠️ = punto frágil)

### 1. Crear quedada
- [ ] Crear con escuela + días + nombre → sin error 500
- [ ] Al elegir escuela y días → aparece score por día con color
- [ ] ⚠️ Subir foto al crear (en staging puede fallar; probar con y sin foto)
- [ ] "Solo mujeres" siendo hombre → rechaza
- [ ] Privacidad seguidores / abierta → ok

### 2. Lista
- [ ] Filtros TODAS / SIGUIENDO / SOLO MUJERES / SEGUIDORES
- [ ] Buscador por escuela + ✕ limpia
- [ ] ⚠️ Quedada NO unida → abre detalle
- [ ] ⚠️ Quedada SÍ unida (o ser organizador) → entra DIRECTO al chat

### 3. Detalle
- [ ] ⚠️ Botón "CÓMO LLEGAR" abre Google Maps (requiere staging desplegado + recargar lista)
- [ ] Organizador: lápiz → editar descripción → se guarda y se ve
- [ ] No organizador: NO sale el lápiz
- [ ] UNIRSE / SALIR DE LA QUEDADA
- [ ] Aforo completo bloquea
- [ ] Expulsar (organizador) / denunciar (no organizador)

### 4. Chat de grupo
- [ ] ⚠️ Título "Ver detalles de la quedada ›" → abre detalle (depende de by-conversation)
- [ ] Menú ⋮ → Silenciar / Activar notificaciones (cambia texto)
- [ ] Menú ⋮ → Cómo llegar (si hay coords)
- [ ] Enviar/recibir + responder deslizando

### 5. Notificaciones (push)
- [ ] ⚠️ App cerrada, te escriben en grupo → llega notificación
- [ ] Tocar notificación → abre el chat del grupo
- [ ] ⚠️ Silenciar → no llega (solo con app en segundo plano; mute solo de este móvil)
- [ ] Quitar silencio → vuelven a llegar

## Riesgos conocidos
1. Staging debe estar verde con V36 antes de probar.
2. Offline: descripción y "cómo llegar" NO se cachean (no se ven sin conexión).
3. Silenciar es solo local (este dispositivo).
4. Foto al crear en staging (a confirmar si es de Storage).
5. iOS solo verificado por CI (compila); probar en iPhone vía AltStore.

## Si algo falla
- "Cómo llegar" no sale / título del chat no abre detalle → staging no
  desplegó V36/endpoints, o no recargaste la lista. Verificar
  `GET meteomontanaapi-staging.up.railway.app/actuator/health` y que el deploy
  de Railway (`develop`) está verde.
