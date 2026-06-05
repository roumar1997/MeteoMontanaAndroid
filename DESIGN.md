# MeteoMontana — Sistema de diseño "Cumbre"

Fuente de verdad para que **PWA, Android y iOS** se vean idénticos.
Cuando una pantalla cambia visualmente, primero se actualiza este doc,
luego se aplica en cada plataforma.

> Origen de los valores: `css/style.css` (`:root`) y `css/tokens.css` de
> la PWA. Si hay duda entre lo escrito aquí y la PWA, **manda la PWA**.

---

## 1 · Tokens

### 1.1 Colores — light

| Nombre  | Hex      | Uso                                              |
|---------|----------|--------------------------------------------------|
| bg      | #F5F3EE  | fondo de pantalla                                |
| paper   | #EBE7DD  | cards / surfaces                                 |
| paper2  | #F0EAD8  | surface variant                                  |
| ink     | #1C1C1A  | texto principal                                  |
| ink2    | #5A574F  | texto secundario                                 |
| ink3    | #8A8478  | texto terciario / eyebrow                        |
| rule    | #D6D2C4  | hairlines y bordes                               |
| terra   | #C2410C  | acento principal                                 |
| terraBg | #FDE4D3  | acento sobre fondo                               |
| moss    | #5E6B4F  | acento secundario                                |
| ok      | #3F6B4A  | éxito                                            |
| warn    | #B45309  | aviso                                            |
| bad     | #9A3412  | error                                            |
| rain    | #2563C7  | datos de lluvia                                  |
| wind    | #4A7C3F  | datos de viento                                  |

### 1.2 Colores — dark

| Nombre   | Hex      |
|----------|----------|
| bg       | #15140F  |
| paper    | #1D1C17  |
| paper2   | #211F19  |
| ink      | #ECE7D8  |
| ink2     | #A8A397  |
| ink3     | #6E6A5F  |
| rule     | #2A281F  |
| terra    | #E0612B  |
| moss     | #7D8A6A  |
| ok       | #7DA068  |
| warn     | #D6904A  |
| bad      | #C9543B  |

### 1.3 Heatmap de score

| Rango  | Light    | Dark     | Texto      |
|--------|----------|----------|------------|
| ≥90    | #5E8B50  | #6F9C5F  | blanco     |
| 80–89  | #82A76E  | #5E8B50  | blanco     |
| 70–79  | #B7C089  | #4A6B40  | ink        |
| 60–69  | #E3D599  | #5E5A35  | ink/blanco |
| 50–59  | #E8B878  | #7A5D2C  | ink/blanco |
| 40–49  | #D99A5A  | #8C5022  | ink/blanco |
| 30–39  | #C2410C  | #A8420E  | blanco     |
| 20–29  | #9A3412  | #7A2A0E  | blanco     |
| <20    | #5A1E08  | #4A1808  | blanco     |

### 1.4 Tipografías

| Familia        | Origen                              | Uso                         |
|----------------|-------------------------------------|-----------------------------|
| Inter          | Google Fonts                        | sans — body, títulos, chips |
| Source Serif 4 | Google Fonts                        | serif — hero, score grande  |
| JetBrains Mono | Google Fonts                        | mono — eyebrow, datos       |

Pesos disponibles: Inter 300/400/500/600/700/800, Source Serif 4
300/400/600/700 + italic 400, JetBrains Mono 400/500/600/700.

| Rol Material3   | Familia | Peso     | Tamaño | Letterspacing |
|-----------------|---------|----------|--------|---------------|
| displayLarge    | serif   | 700      | 32     | -0.5          |
| displayMedium   | serif   | 600      | 28     | -0.3          |
| headlineLarge   | sans    | 700      | 24     | 0             |
| headlineMedium  | sans    | 600      | 20     | 0             |
| titleLarge      | sans    | 600      | 18     | 0             |
| titleMedium     | sans    | 500      | 16     | 0             |
| bodyLarge       | sans    | 400      | 16     | 0 (lh 24)     |
| bodyMedium      | sans    | 400      | 14     | 0 (lh 20)     |
| labelLarge      | sans    | 500      | 14     | +0.5          |
| labelMedium     | mono    | 700      | 10     | +1.8 (eyebrow)|

### 1.5 Spacing

Escala única. Nunca usar valores fuera de escala sin discutirlo aquí.

| Token | dp / pt | Uso típico                                  |
|-------|---------|---------------------------------------------|
| xs    | 4       | dentro de chips, separación mínima           |
| sm    | 8       | gap entre items densos                       |
| md    | 12      | padding interno de cards                     |
| lg    | 16      | padding lateral de pantalla                  |
| xl    | 24      | separación entre secciones                   |
| xxl   | 32      | separación entre bloques grandes             |
| xxxl  | 48      | hero, espacios "respira"                     |

### 1.6 Radius

| Token   | dp / pt | Uso                              |
|---------|---------|----------------------------------|
| sm      | 0       | inputs, hairlines, eyebrows      |
| md      | 2       | chips, cards estándar            |
| lg      | 4       | cards grandes, sheets            |

Cumbre es deliberadamente "casi sin esquinas". Nunca subir de 4dp salvo
componentes circulares (avatares, FAB).

### 1.7 Sombras

Cumbre usa **hairlines**, no sombras blob. Equivalente Material:

- `shadow-sm` PWA → border de 1dp `rule` sin elevación.
- `shadow` PWA    → border de 1dp `rule` sin elevación + tono `paper2`.

Si Material3 mete elevación, ponerla a `0.dp` y compensar con borde.

---

## 2 · Componentes (anatomía espejo)

Cada componente debe existir con **el mismo nombre y la misma anatomía**
en Compose y en SwiftUI. Cuando aparezca uno nuevo en una captura, se
añade a esta lista antes de codearlo.

### 2.1 `Eyebrow`
Pequeña etiqueta tipográfica encima de un bloque. Mono 10sp, peso 700,
letterspacing 1.8sp, color `ink2` o `ink3`, uppercase.

### 2.2 `RuleLine`
Hairline horizontal de 1dp color `rule`. Variante `ink` con 2dp color `ink`.

### 2.3 `ScoreCell`
Celda cuadrada del heatmap. Color de fondo según rango (sección 1.3),
texto mono 10sp peso 700. Sin border, sin radius.

### 2.4 `Chip` *(filtros, tags)*
Padding `xs` vertical / `md` horizontal. Border 1dp `rule`. Radius `md`.
Tipo `labelLarge`. Estado seleccionado: fondo `ink`, texto blanco, border
`ink`.

### 2.5 `Card`
Surface `paper`. Padding interior `md`. Border 1dp `rule`. Radius `md`.
Sin elevación.

*(Esta lista crecerá según vayamos viendo capturas.)*

---

## 3 · Mapeo por plataforma

| Token       | Compose (`theme/*.kt`)           | SwiftUI (planificado)         |
|-------------|----------------------------------|-------------------------------|
| Colores     | `Color.kt`                       | `Color.swift`                 |
| Tipografía  | `Type.kt` (Google Fonts provider)| `Typography.swift` (.ttf bundled) |
| Spacing     | `Spacing.kt`                     | `Spacing.swift`               |
| Shapes      | `Shape.kt`                       | `Shape.swift`                 |
| Theme       | `Theme.kt` (`MeteoMontanaTheme`) | `CumbreTheme` view modifier   |

---

## 4 · Reglas para que no se desincronice

1. **Nunca** hardcodear hex, dp, sp ni nombre de fuente dentro de
   pantallas. Siempre via tokens.
2. Si una captura requiere un valor que no existe, primero se añade a
   este doc y a `Spacing.kt` / `Color.kt`, luego se usa.
3. Cuando se cree un componente nuevo, se añade su sección a §2 antes
   de portarlo a la otra plataforma.
4. Modo oscuro: si una vista no se ve igual de bien en dark, es bug,
   no "ya lo arreglo luego".
