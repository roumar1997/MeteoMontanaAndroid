# Backend pendiente para rocódromos

## Quedadas: grado orientativo y cordada (la app ya los envía)

`POST /api/meetups` (y la respuesta de lectura) necesita campos **opcionales** nuevos. La app Android solo los manda cuando tienen valor; un servidor que los ignore sigue funcionando.

| Campo | Tipo | Significado |
|---|---|---|
| `gymId` | long | ya soportado: quedada en rocódromo (entonces `schoolId` va vacío) |
| `discipline` | `BOULDER` \| `ROUTE` \| `BOTH` \| null | ya soportado |
| `gradeMin` | string \| null | grado orientativo mínimo, de la escalera de grados de la app (p. ej. `6a`) |
| `gradeMax` | string \| null | grado orientativo máximo; si solo hay uno, vale como único |
| `partnersWanted` | int \| null | cordada: personas que se buscan (la app envía `1` solo con `discipline = ROUTE`) |

Pendiente: migración (3 columnas nulas en `meetups`), validación (`gradeMin <= gradeMax` en la escalera; `partnersWanted` solo con `ROUTE`), devolverlos en `MeetupDto` y, si se quiere, filtrar por ellos. Después hay que leerlos en la lista y el detalle de quedadas de Android e iOS (hoy no se muestran).

Sigue sin existir un endpoint de quedadas por `gymId` para la ficha del rocódromo.
