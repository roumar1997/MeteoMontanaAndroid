# Efectos de título (aureola animada) — cómo añadir uno sin sacar versión

El servidor guarda el catálogo (`title_effects`, V99) y lo sirve en `GET /api/title-effects` (público, cacheable).
Las apps lo guardan en disco y pintan cualquier efecto con esos datos (`TitleEffectHalo` en iOS y Android).

## Pasos

1. **Sprites**: PNG con fondo transparente, unos 480 px de ancho, menos de 1 MB. Indica si la figura mira a la derecha.
2. **Subirlos al CDN** (`photos.climbingteams.com`, bucket R2) con un token de admin de Firebase:
   ```bash
   curl -X POST "https://<api>/api/admin/title-effects/<efecto>/sprites" \
        -H "Authorization: Bearer <ID_TOKEN_ADMIN>" -F name=<nombre> -F file=@<nombre>.png
   ```
   Devuelve `{"path":"effects/<efecto>/<nombre>.png","url":"https://photos.climbingteams.com/effects/..."}`.
   (Alternativa: subirlos a mano al bucket R2 en esa misma ruta desde el panel de Cloudflare.)
   Si sustituyes un PNG ya subido, purga la caché de Cloudflare.
3. **Crear el efecto** con `PUT /api/admin/title-effects/<CLAVE>` (solo admin; clave A-Z, 0-9, _):
   ```json
   {"name":"Furgo Lola","orientation":"ORBIT","figureCount":2,
    "sprites":[{"url":"https://photos.climbingteams.com/effects/lola/effect_van1.png","asset":"effect_van1","facesRight":true}],
    "ringColors":["#7CCBF5","#1E9BE0","#0E6FB0","#1E9BE0"],"glowColor":"#1E9BE0","highlightColor":"#7CCBF5",
    "particleColor":"#CFEFFF","particleStyle":"SPARKLE",
    "motion":{"orbit":1.5,"size":0.62,"speed":0.45,"staticAngles":[-2.2],"staticSize":0.45},
    "sortOrder":40,"active":true}
   ```
   - `orientation`: `FACE_PHOTO` (miran a la foto, como hadas y dragones) u `ORBIT` (tangentes, ruedas hacia la foto).
   - `particleStyle`: `SPARKLE` (estrellitas) o `EMBER` (brasas).
   - `motion`: órbita y tamaño en radios de la foto; `speed` en rad/s; `staticAngles`/`staticSize` = foto pequeña quieta.
   - `asset` solo si el PNG va empaquetado en las apps (respaldo sin red). Para efectos nuevos basta `url`.
   - `active:false` lo esconde del selector y de las fotos sin borrarlo.
4. En iOS (admin) aparece solo en «EFECTO ALREDEDOR DE LA FOTO». Las apps refrescan el catálogo como mucho cada hora
   (`refreshAfterSeconds`), al abrir o volver a primer plano.

## Pendiente de Furgo Lola

Los PNG de Lola van empaquetados en las apps (`effect_van1/2`) y la fila LOLA apunta además a
`https://photos.climbingteams.com/effects/lola/effect_van{1,2}.png`, que **hay que subir** (paso 2, con los PNG de
esta carpeta). Mientras no estén, las apps usan los empaquetados y no pasa nada.
