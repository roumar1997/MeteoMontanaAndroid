import SwiftUI
import Shared

/// Tarjeta «🎯 ¿En cuántos intentos? · opcional» del registro de boards: «⚡ 1 · FLASH» (dorado al elegirlo), 2, 3, 4 y «5+»
/// (lista de 5 a 30). Nada elegido de inicio; tocar lo elegido lo quita. Espejo de `AttemptsCard.kt`.
struct AttemptsCard: View {
    @Binding var selected: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            AttemptsCardTitle(title: L("attempts_title"))
            WeightedRow(spacing: 8) {
                AttemptsFlashChip(on: selected == AttemptsBridge.flash, label: L("attempts_flash_chip")) {
                    selected = selected == AttemptsBridge.flash ? nil : AttemptsBridge.flash
                }
                .rowWeight(2.4)
                ForEach(AttemptsBridge.quick.dropFirst(), id: \.self) { n in
                    AttemptsNumberChip(label: "\(n)", on: selected == n) { selected = selected == n ? nil : n }
                }
                AttemptsMoreChip(selected: selected) { selected = $0 }.rowWeight(1.4)
            }
            Text(L("attempts_hint")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Tarjeta única «🎯 ¿Cómo te salió? · opcional» de la hoja «Hecho»: «👁 A VISTA» (azul) y «⚡ FLASH» (dorada), debajo 2, 3, 4 y
/// «5+» con «intentos». Elección única; la lógica es `AttemptsStyleLink` (shared). Espejo de `AscentStyleCard.kt`.
struct AscentStyleCard: View {
    @Binding var value: StyleAttempts

    var body: some View {
        let n: Int? = (value.flash || value.onsight) ? nil : value.attempts?.intValue
        VStack(alignment: .leading, spacing: 10) {
            AttemptsCardTitle(title: L("ascent_how_title"))
            WeightedRow(spacing: 8) {
                AttemptsOnsightChip(on: value.onsight) { value = AttemptsStyleLink.shared.setOnsight(current: value, on: !value.onsight) }
                AttemptsFlashChip(on: value.flash, label: L("ascent_flash_pill")) {
                    value = AttemptsStyleLink.shared.setFlash(current: value, on: !value.flash)
                }
            }
            WeightedRow(spacing: 8) {
                ForEach(AttemptsBridge.quick.dropFirst(), id: \.self) { k in
                    AttemptsNumberChip(label: "\(k)", on: n == k) {
                        value = AttemptsStyleLink.shared.pick(current: value, picked: AttemptsBridge.kotlin(n == k ? nil : k))
                    }
                }
                AttemptsMoreChip(selected: n) { value = AttemptsStyleLink.shared.pick(current: value, picked: AttemptsBridge.kotlin($0)) }
                    .rowWeight(1.4)
                Text(L("ascent_how_attempts")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                    .minimumScaleFactor(0.7).frame(maxWidth: .infinity, alignment: .leading).rowWeight(1.3)
            }
            Text(L("ascent_how_hint")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
    }
}

private struct AttemptsCardTitle: View {
    let title: String
    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text("🎯 " + title).font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink)
            Text(L("attempts_optional")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
        }
    }
}

/// Chip «⚡ FLASH»: dorado con texto marrón y contorno blanco al elegirlo; apagado, como los demás.
struct AttemptsFlashChip: View {
    let on: Bool
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 3) {
                Image(systemName: "bolt.fill").font(.system(size: 13)).foregroundStyle(on ? StyleBadgeColors.flashBrown : Cumbre.terraFill)
                Text(label).font(.system(size: 13, weight: .heavy)).tracking(0.5).lineLimit(1).minimumScaleFactor(0.8)
                    .foregroundStyle(on ? StyleBadgeColors.flashBrown : Cumbre.ink)
            }
            .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 40)
            .background(on ? AnyShapeStyle(StyleBadgeColors.flash) : AnyShapeStyle(Cumbre.bg), in: Capsule())
            .overlay(Capsule().stroke(on ? Color.white : Cumbre.rule, lineWidth: on ? 1.5 : 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Chip «👁 A VISTA»: azul con texto marino, contorno blanco y brillo suave al elegirlo.
private struct AttemptsOnsightChip: View {
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "eye").font(.system(size: 14)).foregroundStyle(on ? StyleBadgeColors.onsightNavy : StyleBadgeColors.onsightEnd)
                Text(L("onsight_badge")).font(.system(size: 13, weight: .heavy)).tracking(0.5).lineLimit(1).minimumScaleFactor(0.8)
                    .foregroundStyle(on ? StyleBadgeColors.onsightNavy : Cumbre.ink)
            }
            .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 40)
            .background(on ? AnyShapeStyle(StyleBadgeColors.onsight) : AnyShapeStyle(Cumbre.bg), in: Capsule())
            .overlay(Capsule().stroke(on ? Color.white : Cumbre.rule, lineWidth: on ? 1.5 : 1))
            .shadow(color: StyleBadgeColors.onsightEnd.opacity(on ? 0.4 : 0), radius: 4)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct AttemptsNumberChip: View {
    let label: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 15, weight: .bold)).lineLimit(1).foregroundStyle(on ? .white : Cumbre.ink)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(on ? Cumbre.terraFill : Cumbre.bg, in: Capsule())
                .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// «5+ ▾»: abre la lista de 5 a 30; elegido muestra el número. Volver a elegir el mismo lo quita.
struct AttemptsMoreChip: View {
    let selected: Int?
    let onChange: (Int?) -> Void
    var body: some View {
        let on = AttemptsBridge.isFivePlus(selected)
        Menu {
            ForEach(AttemptsBridge.more, id: \.self) { n in
                Button { onChange(selected == n ? nil : n) } label: {
                    if n == selected { Label("\(n)", systemImage: "checkmark") } else { Text("\(n)") }
                }
            }
        } label: {
            HStack(spacing: 2) {
                Text(on ? "\(selected ?? 0)" : L("attempts_more")).font(.system(size: 15, weight: .bold)).lineLimit(1)
                Image(systemName: "chevron.down").font(.system(size: 10, weight: .bold))
            }
            .foregroundStyle(on ? .white : Cumbre.ink)
            .frame(maxWidth: .infinity, minHeight: 40)
            .background(on ? Cumbre.terraFill : Cumbre.bg, in: Capsule())
            .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
            .contentShape(Capsule())
        }
    }
}
