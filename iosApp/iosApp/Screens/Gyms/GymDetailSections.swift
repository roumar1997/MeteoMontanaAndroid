import SwiftUI
import Shared

/// Título de sección en mono mayúsculas, con icono opcional. Espejo de `SectionLabel` de Android.
struct GymSectionLabel: View {
    let text: String
    var icon: String?

    var body: some View {
        HStack(spacing: 6) {
            if let icon {
                Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 14, height: 14)
            }
            Text(text.uppercased()).eyebrow(Cumbre.ink2)
        }
        .foregroundStyle(Cumbre.ink2)
        .padding(.top, 16).padding(.bottom, 2)
    }
}

/// Colores de los iconos de servicios (los mismos de Android).
enum GymServiceTint {
    static let autobelay = Color(hex: 0x7B3FB0)
    static let rain = Cumbre.dyn(0x2563C7, 0x7BA3EE)
}

/// Icono del servicio sobre un cuadradito redondeado tintado.
struct GymTintedIcon: View {
    let icon: String
    let tint: Color
    var body: some View {
        RoundedRectangle(cornerRadius: 12).fill(tint.opacity(0.14)).frame(width: 38, height: 38)
            .overlay(Image(icon).renderingMode(.template).resizable().scaledToFit().frame(width: 20, height: 20).foregroundStyle(tint))
    }
}

/// Tarjeta de horario o precio: icono de color a la izquierda y el texto a la derecha.
struct GymInfoCard<Content: View>: View {
    let icon: String
    let tint: Color
    @ViewBuilder let content: () -> Content
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            GymTintedIcon(icon: icon, tint: tint)
            VStack(alignment: .leading, spacing: 0) { content() }.frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Modalidad, escala, horario y precio orientativo (con su fecha). Espejo de `GymInfoSections`.
struct GymInfoSections: View {
    let gym: Gym

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GymSectionLabel(text: L("gym_section_discipline"))
            let disciplines = GymPresentation.disciplineChips(gym)
            if disciplines.isEmpty {
                Text(L("gym_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            } else {
                FlowLayoutView(spacing: 6) {
                    ForEach(disciplines, id: \.self) { GymTag(text: $0, color: GymTone.discipline) }
                }
            }
            GymScaleSection(scale: gym.scale)
            GymSectionLabel(text: L("gym_section_hours"), icon: "gym_ic_clock")
            GymInfoCard(icon: "gym_ic_clock", tint: GymServiceTint.rain) {
                let lines = GymPresentation.hoursLines(gym.openingHours)
                if lines.isEmpty {
                    Text(L("gym_hours_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                } else {
                    ForEach(lines, id: \.self) { Text($0).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineSpacing(6) }
                }
            }
            GymSectionLabel(text: L("gym_section_price"), icon: "gym_ic_price")
            GymInfoCard(icon: "gym_ic_price", tint: Cumbre.ok) {
                let info = GymPresentation.priceInfo(gym)
                if info.lines.isEmpty {
                    Text(L("gym_price_unknown")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                } else {
                    ForEach(info.lines, id: \.self) { Text($0).font(.system(size: 14)).foregroundStyle(Cumbre.ink).lineSpacing(6) }
                    if let note = info.note {
                        Text(note).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).padding(.top, 2)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Servicios (autoaseguro, alquiler, niños, principiantes): va arriba en la ficha para verlo de un vistazo.
struct GymServicesSection: View {
    let gym: Gym
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GymSectionLabel(text: L("gym_section_services"))
            VStack(spacing: 8) {
                row("gym_ic_autobelay", "gym_service_autobelay", gym.hasAutobelay, GymServiceTint.autobelay)
                row("gym_ic_gear_rental", "gym_service_gear", gym.hasGearRental, GymServiceTint.rain)
                row("gym_ic_kids", "gym_service_kids", gym.kidsFriendly, Cumbre.warn)
                row("gym_ic_beginner", "gym_service_beginner", gym.beginnerFriendly, Cumbre.ok)
            }
        }
    }

    /// Tarjeta de un servicio: icono en cuadradito de color, nombre y «Sí» (verde), «No» (rojo) o «Sin datos» (gris).
    private func row(_ icon: String, _ label: String, _ value: KotlinBoolean?, _ tint: Color) -> some View {
        let chip: Color = value == nil ? Cumbre.ink2 : (value!.boolValue ? Cumbre.ok : Cumbre.bad)
        return HStack(spacing: 12) {
            GymTintedIcon(icon: icon, tint: tint)
            Text(L(label)).font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(GymPresentation.serviceValue(value)).font(.system(size: 13, weight: .bold)).foregroundStyle(chip).lineLimit(1)
                .padding(.horizontal, 12).padding(.vertical, 5).background(chip.opacity(0.13), in: Capsule())
        }
        .padding(10)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Escala de dificultad en una tarjeta: con colores, cada color con su rango debajo; sin colores, la paleta de grados.
struct GymScaleSection: View {
    let scale: GymScale?
    private static let sampleGrades = ["5", "6a", "6b", "6c", "7a", "7b+"]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            GymSectionLabel(text: L("gym_section_scale"))
            VStack(alignment: .leading, spacing: 8) {
                if let scale, scale.isColors {
                    HStack(alignment: .top, spacing: 6) {
                        ForEach(scale.levels, id: \.name) { lv in
                            VStack(spacing: 3) {
                                RoundedRectangle(cornerRadius: 10).fill(GymPresentation.color(hex: lv.hex)).frame(height: 34)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                                Text(GymPresentation.gradeRange(lv.gradeFrom, lv.gradeTo) ?? holdColorLabel(lv.name))
                                    .font(.system(size: 10)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    Text(L("gym_scale_easy_hard")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                } else {
                    HStack(spacing: 5) {
                        ForEach(Self.sampleGrades, id: \.self) { g in
                            let st = GradeColor.chipStyle(g)
                            RoundedRectangle(cornerRadius: 9).fill(st.stroke).frame(height: 30)
                                .overlay(RoundedRectangle(cornerRadius: 9).stroke(Cumbre.rule, lineWidth: 1))
                                .overlay(Text(g.uppercased()).font(.system(size: 12, weight: .bold)).lineLimit(1)
                                    .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    Text(L("gym_train_by_grade")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
            }
            .padding(12)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Lo último de la ficha, pequeño y discreto: fiabilidad del dato y el crédito de OpenStreetMap (ODbL).
struct GymFooterNotes: View {
    let gym: Gym
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(GymPresentation.verificationText(gym.verification)).font(.system(size: 11)).foregroundStyle(Cumbre.ink2)
            Text(L("gym_osm_credit")).font(.system(size: 10)).foregroundStyle(Cumbre.ink2.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 16)
    }
}

/// Editor de «Escala de colores o grados»: Colores | Grados y, con colores, tocarlos de fácil a difícil.
struct GymScaleEditor: View {
    @Binding var grades: Bool
    @Binding var picked: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L("gym_scale_title").uppercased()).eyebrow(Cumbre.ink2)
            HStack(spacing: 6) {
                Button { grades = false } label: { GymChip(text: L("gym_scale_colors"), selected: !grades) }.buttonStyle(.plain)
                Button { grades = true } label: { GymChip(text: L("gym_scale_grades"), selected: grades) }.buttonStyle(.plain)
            }
            if !grades {
                Text(L("gym_scale_pick")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                FlowLayoutView(spacing: 10) {
                    ForEach(GymScalePresets.shared.colors, id: \.name) { lv in swatch(lv) }
                }
                if picked.count < Int(GymScalePresets.shared.MIN_LEVELS) {
                    Text(L("gym_scale_need")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                }
            }
        }
        .padding(.top, 4)
    }

    private func swatch(_ lv: GymScaleLevel) -> some View {
        let order = (picked.firstIndex(of: lv.name) ?? -1) + 1
        let light = lv.name == "blanco" || lv.name == "amarillo"
        return Button {
            if order > 0 { picked.removeAll { $0 == lv.name } } else { picked.append(lv.name) }
        } label: {
            Circle().fill(GymPresentation.color(hex: lv.hex)).frame(width: 40, height: 40)
                .overlay(Circle().stroke(order > 0 ? Cumbre.terraFill : Cumbre.rule, lineWidth: order > 0 ? 3 : 1))
                .overlay {
                    if order > 0 {
                        Text("\(order)").font(.system(size: 15, weight: .bold)).foregroundStyle(light ? Color.black : .white)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}
