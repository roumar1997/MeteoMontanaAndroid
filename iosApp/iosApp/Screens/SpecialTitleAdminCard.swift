import SwiftUI
import Shared

/// Solo admin, en la hoja ADMIN del perfil de otra persona: ponerle un título especial con texto libre y el color que
/// quieras (brilla como «Señor de la Cumbre»). Título vacío = quitarlo.
struct SpecialTitleAdminCard: View {
    let uid: String
    @State private var text = ""
    @State private var color = Color(red: 0.72, green: 0.53, blue: 0.04)
    @State private var saved: String?
    @State private var hasSpecial = false
    @State private var working = false

    /// Colores rápidos; el selector de abajo deja elegir cualquier otro.
    private let presets: [String] = ["#B8860B", "#FF4FA3", "#9B5DE5", "#2E86DE", "#00A5A5", "#2E9E5B", "#E67E22", "#D63031", "#6C7A89"]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("TÍTULO ESPECIAL").font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.terra)
            Text("Un título solo para esta persona, con el texto y el color que quieras. Brilla como «Señor de la Cumbre» y sustituye al de su nivel.")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            AdminField(placeholder: "Por ejemplo: Reina Hada", text: $text)
            HStack(spacing: 8) {
                ForEach(presets, id: \.self) { hex in
                    let c = colorFromHex(hex) ?? .gray
                    Button { color = c } label: {
                        Circle().fill(c).frame(width: 26, height: 26)
                            .overlay(Circle().stroke(hexFromColor(color) == hex ? Cumbre.ink : Color.clear, lineWidth: 2.5))
                    }.buttonStyle(.plain)
                }
                ColorPicker("", selection: $color, supportsOpacity: false).labelsHidden()
            }
            if !text.trimmingCharacters(in: .whitespaces).isEmpty {
                HStack { Spacer(); SpecialTitleView(text: text.trimmingCharacters(in: .whitespaces), colorHex: hexFromColor(color)); Spacer() }
                    .padding(.vertical, 4)
            }
            HStack(spacing: 8) {
                AdminButton(title: "GUARDAR", symbol: "sparkles", busy: working) { Task { await save(text) } }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                if hasSpecial {
                    AdminButton(title: "QUITAR", symbol: "xmark", kind: .danger) { Task { await save("") } }
                }
            }
            if let saved { Text(saved).font(.system(size: 12)).foregroundStyle(Cumbre.ok) }
            effectPicker
        }
        .task {
            if let r = try? await AppDependencies.shared.container.getUserRewards.invoke(uid: uid) {
                text = r.specialTitle ?? ""
                hasSpecial = !(r.specialTitle ?? "").isEmpty
                if let c = colorFromHex(r.specialColor) { color = c }
                effect = r.titleEffect ?? ""
            }
        }
    }

    // MARK: - Efecto animado (hadas / dragones)

    /// "" = ninguno, "FAIRIES" o "DRAGONS".
    @State private var effect = ""
    @State private var effectSaved: String?

    private var effectPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider().overlay(Cumbre.rule).padding(.vertical, 4)
            Text("EFECTO ALREDEDOR DE LA FOTO").font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.terra)
            Text("Hadas o dragones dando vueltas en su perfil (quietos en el feed y los chats). Sustituye a la aureola normal.")
                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            HStack(spacing: 8) {
                effectChip("Ninguno", value: "")
                effectChip("Hadas", value: "FAIRIES")
                effectChip("Dragones", value: "DRAGONS")
            }
            if let effectSaved { Text(effectSaved).font(.system(size: 12)).foregroundStyle(Cumbre.ok) }
        }
    }

    private func effectChip(_ title: String, value: String) -> some View {
        let on = effect == value
        return Button { Task { await saveEffect(value) } } label: {
            Text(title).font(Cumbre.mono(11, .bold))
                .foregroundStyle(on ? Color.white : Cumbre.ink2)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(on ? Cumbre.terraFill : Cumbre.paper, in: Capsule())
                .overlay(Capsule().stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private func saveEffect(_ value: String) async {
        if let r = try? await AppDependencies.shared.container.setTitleEffect.invoke(uid: uid, effect: value) {
            effect = r.titleEffect ?? ""
            UserBadgeStore.shared.refresh(uid)
            effectSaved = effect.isEmpty ? "Efecto quitado." : "Guardado. Ya se ve en su foto."
        } else {
            effectSaved = "No se pudo guardar (revisa la conexión)."
        }
    }

    private func save(_ value: String) async {
        working = true
        defer { working = false }
        let clean = value.trimmingCharacters(in: .whitespaces)
        if let r = try? await AppDependencies.shared.container.setSpecialTitle.invoke(uid: uid, title: clean, color: hexFromColor(color)) {
            hasSpecial = !(r.specialTitle ?? "").isEmpty
            if !hasSpecial { text = "" }
            UserBadgeStore.shared.refresh(uid)
            saved = hasSpecial ? "Guardado. Ya lo ve en su perfil." : "Título especial quitado."
        } else {
            saved = "No se pudo guardar (revisa la conexión)."
        }
    }
}
