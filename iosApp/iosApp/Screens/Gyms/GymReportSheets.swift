import SwiftUI
import PhotosUI
import Shared

private let gymIssueLabels: [(code: String, key: String)] = [
    ("IS_FREE", "gym_issue_free"), ("IS_PAID", "gym_issue_paid"), ("CLOSED", "gym_issue_closed"),
    ("IS_BOULDER", "gym_issue_boulder"), ("IS_ROPE", "gym_issue_rope"), ("WRONG_LOCATION", "gym_issue_location"),
    ("OTHER", "gym_issue_other")
]

/// «Corregir datos» (maqueta): chips de lo que falla, comentario, foto opcional y «Enviar». Lo recibe un admin.
/// Abajo, el acceso a «Añadir un muro que falta». Espejo de `GymReportSheet` de Android.
struct GymReportSheet: View {
    let gym: Gym
    @StateObject private var vm = GymReportViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var issues = Set<String>()
    @State private var comment = ""
    @State private var photo: UIImage?
    @State private var addWallOpen = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("gym_fix_title")).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                Text(gym.name).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                GymSheetEyebrow(text: L("gym_fix_what"))
                FlowLayoutView(spacing: 7) {
                    ForEach(gymIssueLabels, id: \.code) { issue in
                        Button { toggle(issue.code) } label: { GymChip(text: L(issue.key), selected: issues.contains(issue.code)) }
                            .buttonStyle(.plain)
                    }
                }
                GymSheetEyebrow(text: L("gym_comment"))
                GymCommentField(text: $comment, hint: L("gym_comment_hint"), lines: 3)
                GymPhotoRow(photo: $photo)
                GymSendBlock(state: vm.state, enabled: !issues.isEmpty || !comment.trimmingCharacters(in: .whitespaces).isEmpty) {
                    vm.sendCorrection(gymId: gym.id, issueCodes: issues, comment: comment, photo: photo)
                }
                addWallCard
            }
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .gymCloseWhenSent(vm.state) { dismiss() }
        .sheet(isPresented: $addWallOpen) { GymNewSheet(lat: gym.lat, lon: gym.lon) }
    }

    private func toggle(_ code: String) {
        if issues.contains(code) { issues.remove(code) } else { issues.insert(code) }
    }

    /// Bloque suave terracota de la maqueta que abre el alta de un muro.
    private var addWallCard: some View {
        Button { addWallOpen = true } label: {
            Text("＋  " + L("gym_add_missing")).font(Cumbre.serif(16, .bold)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                .background(Cumbre.terraBg, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Cumbre.terra.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.top, 12)
    }
}

/// «Añadir un muro que falta»: nombre, tipo, modalidad y el punto del mapa. Se publica tras revisión.
struct GymNewSheet: View {
    let lat: Double
    let lon: Double
    @StateObject private var vm = GymReportViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var kind: GymKind = .free
    @State private var boulder = false
    @State private var rope = false
    @State private var photo: UIImage?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("gym_new_title")).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                FlowLayoutView(spacing: 6) {
                    kindChip("gym_new_kind_free", .free)
                    kindChip("gym_new_kind_municipal", .municipal)
                    kindChip("gym_new_kind_commercial", .commercial)
                }
                FlowLayoutView(spacing: 6) {
                    Button { boulder.toggle() } label: { GymChip(text: L("gym_disc_boulder"), selected: boulder) }.buttonStyle(.plain)
                    Button { rope.toggle() } label: { GymChip(text: L("gym_disc_rope"), selected: rope) }.buttonStyle(.plain)
                }
                GymSheetEyebrow(text: L("gym_new_name"))
                GymCommentField(text: $name, hint: L("gym_new_name_hint"), lines: 1)
                Text(L("gym_new_place_here", String(format: "%.5f, %.5f", lat, lon)))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                Text(L("gym_new_place_note")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                GymPhotoRow(photo: $photo)
                GymSendBlock(state: vm.state, enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                    vm.sendNewGym(name: name, lat: lat, lon: lon, kind: kind, boulder: boulder, rope: rope, photo: photo)
                }
            }
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .onAppear { vm.reset() }
        .gymCloseWhenSent(vm.state) { dismiss() }
    }

    private func kindChip(_ key: String, _ value: GymKind) -> some View {
        Button { kind = value } label: { GymChip(text: L(key), selected: kind == value) }.buttonStyle(.plain)
    }
}

// MARK: - Piezas comunes de las hojas

private struct GymSheetEyebrow: View {
    let text: String
    var body: some View { Text(text.uppercased()).eyebrow(Cumbre.ink2).padding(.top, 8) }
}

/// Campo de texto con radio 14: una línea (nombre) o varias (comentario).
private struct GymCommentField: View {
    @Binding var text: String
    let hint: String
    let lines: Int

    var body: some View {
        TextField(hint, text: $text, axis: .vertical)
            .lineLimit(lines...max(lines, 6))
            .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
            .padding(12)
            .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// «Añadir foto» (cápsula; con foto elegida pasa a «Cambiar foto» y se marca) + «opcional».
private struct GymPhotoRow: View {
    @Binding var photo: UIImage?
    @State private var item: PhotosPickerItem?

    var body: some View {
        HStack(spacing: 10) {
            PhotosPicker(selection: $item, matching: .images) {
                GymChip(text: L(photo == nil ? "gym_add_photo" : "gym_change_photo"), selected: photo != nil)
            }
            Text(L("gym_optional")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
        }
        .padding(.top, 4)
        .onChange(of: item) { _, new in
            guard let new else { return }
            Task {
                if let data = try? await new.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    photo = image
                }
            }
        }
    }
}

/// Botón «Enviar», su nota («La revisará un admin») y el resultado (enviado / error).
private struct GymSendBlock: View {
    let state: GymSendState
    let enabled: Bool
    let onSend: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            GymActionPill(text: L("gym_send"), primary: true, enabled: enabled && state != .sending && state != .sent, action: onSend)
            if state == .sending { ProgressView() }
            Text(note).font(.system(size: 12)).multilineTextAlignment(.center)
                .foregroundStyle(state == .failed ? Cumbre.bad : Cumbre.ink2)
        }
        .frame(maxWidth: .infinity).padding(.top, 8)
    }

    private var note: String {
        switch state {
        case .sent: return L("gym_sent")
        case .failed: return L("gym_send_error")
        default: return L("gym_review_note")
        }
    }
}

private extension View {
    /// Enviado: se enseña la confirmación un momento y la hoja se cierra sola (1,5 s).
    func gymCloseWhenSent(_ state: GymSendState, close: @escaping () -> Void) -> some View {
        onChange(of: state) { _, new in
            guard new == .sent else { return }
            Task {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                close()
            }
        }
    }
}
