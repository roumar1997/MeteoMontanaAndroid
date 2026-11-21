import SwiftUI
import Shared
import PhotosUI

// Pasos 2 y 3 y piezas del asistente «Nueva quedada» (maqueta A). Espejo de MeetupDetailsStep.kt, MeetupFinalStep.kt
// y MeetupWizardParts.kt. Todo redondeado y sin anchos fijos.

/// La mochila del organizador pasa a la quedada al crearla (igual que al unirse).
enum MeetupGearCopy {
    static func copyMyBackpack(meetupId: String) {
        let c = AppDependencies.shared.container
        Task {
            if let myGear = try? await c.getMyProfile.invoke().gearJson, !myGear.isEmpty, !parseGear(myGear).isEmpty {
                _ = try? await c.updateMyGear.execute(meetupId: meetupId, gearJson: myGear)
            }
        }
    }
}

/// Tres segmentos; los [step] primeros en terracota.
struct WizardProgress: View {
    let step: Int
    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...3, id: \.self) { i in
                Capsule().fill(i <= step ? Cumbre.terraFill : Cumbre.rule.opacity(0.5)).frame(height: 5)
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 8)
    }
}

struct WizardQuestion: View {
    let text: String
    var body: some View {
        Text(text).font(Cumbre.serif(27, .bold)).foregroundStyle(Cumbre.ink).fixedSize(horizontal: false, vertical: true)
    }
}

struct WizardEyebrow: View {
    let text: String
    var body: some View { Text(text.uppercased()).eyebrow(Cumbre.ink2) }
}

/// Tarjeta redondeada seleccionable.
struct WizardOptionCard<Content: View>: View {
    let selected: Bool
    let action: () -> Void
    @ViewBuilder let content: () -> Content
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                content().frame(maxWidth: .infinity, alignment: .leading)
                if selected { Image(systemName: "checkmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.terraFill) }
            }
            .padding(selected ? 13 : 14)
            .background(selected ? Cumbre.terraBg : Cumbre.paper, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selected ? Cumbre.terraFill : Cumbre.rule, lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

struct WizardCardTitle: View {
    let title: String
    var subtitle: String?
    var serif = false
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(serif ? Cumbre.serif(20, .bold) : .system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.leading)
            if let subtitle { Text(subtitle).font(.system(size: 12.5)).foregroundStyle(Cumbre.ink2).multilineTextAlignment(.leading) }
        }
    }
}

/// Segmento «A | B» de ancho completo.
struct WizardSegmented: View {
    let first: String
    let second: String
    let firstSelected: Bool
    let onFirst: () -> Void
    let onSecond: () -> Void
    var body: some View {
        HStack(spacing: 0) {
            cell(first, firstSelected, onFirst)
            cell(second, !firstSelected, onSecond)
        }
        .padding(4).background(Cumbre.rule.opacity(0.45), in: Capsule())
    }

    private func cell(_ t: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t.uppercased()).eyebrow(on ? .white : Cumbre.ink2).lineLimit(1)
                .frame(maxWidth: .infinity).padding(.vertical, 12)
                .background(on ? Cumbre.terraFill : Color.clear, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Botón redondo − / + del asistente.
struct StepperButton: View {
    let symbol: String
    let action: (() -> Void)?
    let primary: Bool
    var size: CGFloat = 42
    var body: some View {
        let enabled = action != nil
        Button { action?() } label: {
            Text(symbol).font(.system(size: size * 0.52, weight: .bold))
                .foregroundStyle(primary ? Color.white : Cumbre.ink.opacity(enabled ? 1 : 0.4))
                .frame(width: size, height: size)
                .background((primary ? Cumbre.terraFill : Cumbre.bg).opacity(enabled ? 1 : 0.4), in: Circle())
                .overlay(Circle().stroke(primary ? Color.clear : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// Paso 2 de 3: ¿Qué vais a hacer? Bloque y Vía (casillas), rango de grado con colores, cuántos buscáis y cordada.
struct MeetupDetailsStep: View {
    @Binding var draft: MeetupDraft

    var body: some View {
        WizardQuestion(text: L("mw_step2_q"))
        WizardEyebrow(text: L("mw_modality_both"))
        HStack(spacing: 10) {
            WizardOptionCard(selected: draft.hasBoulder, action: { draft = draft.toggleBoulder() }, content: {
                WizardCardTitle(title: L("mw_boulder_title"), subtitle: L("mw_boulder_sub"), serif: true)
            })
            WizardOptionCard(selected: draft.hasRoute, action: { draft = draft.toggleRoute() }, content: {
                WizardCardTitle(title: L("mw_route_title"), subtitle: L("mw_route_sub"), serif: true)
            })
        }
        WizardEyebrow(text: L("mw_grade_group") + (rangeText.map { " · \($0)" } ?? ""))
        FlowLayoutView(spacing: 6) {
            ForEach(MeetupDraft.Companion.shared.GRADE_LADDER, id: \.self) { g in
                let st = GradeColor.chipStyle(g)
                let sel = draft.isGradeSelected(grade: g)
                Button { draft = draft.toggleGrade(tapped: g) } label: {
                    Text(g.uppercased()).font(.system(size: 13, weight: .bold)).lineLimit(1)
                        .foregroundStyle(st.dark ? Color(hex: 0x1B1B1B) : .white)
                        .padding(.horizontal, 13).padding(.vertical, 9)
                        .background(st.stroke, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(sel ? Cumbre.terraFill : Color.clear, lineWidth: 3))
                }
                .buttonStyle(.plain)
            }
        }
        WizardEyebrow(text: L("mw_partners_q"))
        let p = Int(draft.partners)
        HStack(spacing: 10) {
            HStack(spacing: 0) {
                StepperButton(symbol: "−", action: p > Int(MeetupDraft.Companion.shared.MIN_PARTNERS) ? { draft = draft.withPartners(n: Int32(p - 1)) } : nil,
                              primary: false)
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    Text("\(p)").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                    Text(" " + LP("mw_people", p)).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                }
                .frame(maxWidth: .infinity)
                StepperButton(symbol: "+", action: p < Int(MeetupDraft.Companion.shared.MAX_PARTNERS) ? { draft = draft.withPartners(n: Int32(p + 1)) } : nil,
                              primary: true)
            }
            .padding(6).background(Cumbre.paper, in: Capsule()).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
            Button { draft = draft.toggleRopeTeam() } label: {
                GymChip(text: L("create_meetup_rope_team"), selected: draft.isRopeTeam)
            }
            .buttonStyle(.plain)
        }
        if draft.isRopeTeam {
            Text(L("mw_rope_team_hint")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
        }
    }

    private var rangeText: String? {
        guard let min = draft.gradeMin else { return nil }
        let max = draft.gradeMax ?? min
        return min == max ? min.uppercased() : L("mw_grade_range", min.uppercased(), max.uppercased())
    }
}

func gearItemLabel(_ item: String) -> String {
    switch item {
    case "ROPE": return L("gear_rope")
    case "GRIGRI": return L("gear_grigri")
    case "QUICKDRAWS": return L("gear_quickdraws")
    default: return L("gear_crashpad")
    }
}

/// Paso 3 de 3: material (baldosas de dos en dos), mensaje, quién puede unirse, nombre con foto y resumen.
struct MeetupFinalStep: View {
    @Binding var draft: MeetupDraft
    @Binding var uploadingPhoto: Bool
    let defaultName: String
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        WizardQuestion(text: L("mw_step3_q"))
        let items = MeetupGearItem.shared.forPlace(discipline: draft.discipline, gym: draft.placeIsGym)
        if !items.isEmpty {
            WizardEyebrow(text: L("mw_gear_tap"))
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(items, id: \.self) { gearTile($0) }
            }
        }
        WizardEyebrow(text: L("mw_message"))
        TextField(L("mw_message_hint"), text: Binding(
            get: { draft.description_ ?? "" },
            set: { v in draft = draft.with(description: .some(v.trimmingCharacters(in: .whitespaces).isEmpty ? nil : String(v.prefix(300)))) }
        ), axis: .vertical)
        .lineLimit(2...3).font(.system(size: 15)).padding(12)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
        WizardEyebrow(text: L("mw_who_joins"))
        HStack(spacing: 8) {
            privacyChip("OPEN", "create_meetup_privacy_open")
            privacyChip("FOLLOWERS", "create_meetup_privacy_followers")
            privacyChip("WOMEN", "create_meetup_privacy_women")
        }
        WizardEyebrow(text: L("mw_name_label"))
        HStack(spacing: 8) {
            TextField(defaultName, text: Binding(get: { draft.name }, set: { draft = draft.with(name: $0) }))
                .font(.system(size: 15)).padding(14)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.rule, lineWidth: 1))
            PhotosPicker(selection: $photoItem, matching: .images) {
                RoundedRectangle(cornerRadius: 16).fill(Cumbre.paper).frame(width: 56, height: 56)
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(draft.photoUrl != nil ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                    .overlay {
                        if uploadingPhoto { ProgressView() } else {
                            Image(systemName: draft.photoUrl != nil ? "checkmark.circle" : "camera")
                                .font(.system(size: 20)).foregroundStyle(draft.photoUrl != nil ? Cumbre.terraFill : Cumbre.ink2)
                        }
                    }
            }
            .accessibilityLabel(L("mw_photo_add"))
        }
        .onChange(of: photoItem) { _, item in upload(item) }
        MeetupSummaryCard(draft: draft)
    }

    private func upload(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task { @MainActor in
            guard let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) else { return }
            uploadingPhoto = true
            if let url = try? await StorageUploader.uploadMeetupPhoto(img, tempId: "new_\(Int(Date().timeIntervalSince1970))") {
                draft = draft.with(photoUrl: .some(url))
            }
            uploadingPhoto = false
            photoItem = nil
        }
    }

    private func privacyChip(_ key: String, _ title: String) -> some View {
        Button { draft = draft.with(privacy: key) } label: { GymChip(text: L(title), selected: draft.privacy == key) }
            .buttonStyle(.plain)
    }

    private func gearTile(_ item: String) -> some View {
        let qty = draft.gear[item].map { Int(truncating: $0) }
        return VStack(alignment: .leading, spacing: 4) {
            Text(gearItemLabel(item)).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
            if let qty, MeetupGearItem.shared.hasQuantity(item: item) {
                HStack(spacing: 10) {
                    StepperButton(symbol: "−", action: qty > 1 ? { draft = draft.withGearQty(item: item, qty: Int32(qty - 1)) } : nil,
                                  primary: false, size: 30)
                    Text("\(qty)").font(.system(size: 15, weight: .bold)).foregroundStyle(Cumbre.ink)
                    StepperButton(symbol: "+", action: qty < Int(MeetupGearItem.shared.MAX_QTY) ? { draft = draft.withGearQty(item: item, qty: Int32(qty + 1)) } : nil,
                                  primary: true, size: 30)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(qty != nil ? Cumbre.terraBg : Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(qty != nil ? Cumbre.terraFill : Cumbre.rule, lineWidth: qty != nil ? 2 : 1))
        .contentShape(RoundedRectangle(cornerRadius: 14))
        .onTapGesture { draft = draft.toggleGear(item: item) }
    }
}

/// Resumen oscuro: lugar · días y, debajo, modalidad · personas · grado.
struct MeetupSummaryCard: View {
    let draft: MeetupDraft
    var body: some View {
        let discipline: String? = {
            switch draft.discipline {
            case "BOULDER": return L("mw_boulder_title")
            case "ROUTE": return L("mw_route_title")
            case "BOTH": return L("mw_both_title")
            default: return nil
            }
        }()
        let grade = draft.gradeMin.map { min -> String in
            let max = draft.gradeMax ?? min
            return min == max ? min.uppercased() : "\(min.uppercased())–\(max.uppercased())"
        }
        let days = draft.days.sorted().map { d in d.suffix(5).split(separator: "-").reversed().joined(separator: "/") }.joined(separator: ", ")
        let line1 = [draft.placeName, days].filter { !$0.isEmpty }.joined(separator: " · ")
        let line2 = [discipline, "\(draft.partners) " + LP("mw_people", Int(draft.partners)), grade].compactMap { $0 }.joined(separator: " · ")
        VStack(alignment: .leading, spacing: 4) {
            Text(L("mw_summary").uppercased()).eyebrow(Color(hex: 0xBDB7A6))
            Text(line1).font(Cumbre.serif(17, .bold)).foregroundStyle(Color(hex: 0xF5F3EE))
            Text(line2).font(.system(size: 13)).foregroundStyle(Color(hex: 0xDDD7C6))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color(hex: 0x1B1B1B), in: RoundedRectangle(cornerRadius: 20))
    }
}
