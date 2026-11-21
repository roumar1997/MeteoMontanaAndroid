import SwiftUI
import SafariServices
import PhotosUI
import Shared

/// UNA sola tarjeta con «Estoy aquí» y «Quién va hoy». Espejo de `GymPresenceCard` (GymDetailTopCards.kt).
struct GymPresenceCard: View {
    let people: [SchoolPresence]
    let iAmHere: Bool
    let busy: Bool
    let error: Bool
    let onJoin: () -> Void
    let onLeave: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 10) {
                Circle().fill(iAmHere ? Cumbre.ok : Cumbre.ink2.opacity(0.5)).frame(width: 9, height: 9)
                VStack(alignment: .leading, spacing: 1) {
                    if iAmHere {
                        Text(L("gym_here_you")).font(Cumbre.serif(15, .bold)).foregroundStyle(Cumbre.ink)
                    }
                    Text(people.isEmpty ? L("gym_who_today_none") : L("gym_who_today", people.count))
                        .font(iAmHere ? .system(size: 12) : Cumbre.serif(15, .bold))
                        .foregroundStyle(iAmHere ? Cumbre.ink2 : Cumbre.ink)
                    if !iAmHere {
                        Text(L("gym_who_hint")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !people.isEmpty {
                    HStack(spacing: -10) {
                        ForEach(Array(people.prefix(3)), id: \.uid) { p in
                            AvatarCircle(url: p.photoUrl, size: 26, uid: p.uid).overlay(Circle().stroke(Cumbre.paper, lineWidth: 2))
                        }
                    }
                }
                Button { if iAmHere { onLeave() } else { onJoin() } } label: {
                    Text(L(iAmHere ? "gym_here_leave" : "gym_here_top").uppercased())
                        .font(.system(size: 11, weight: .bold)).tracking(0.6).lineLimit(2).multilineTextAlignment(.center)
                        .foregroundStyle(iAmHere ? Cumbre.ink2 : .white)
                        .padding(.horizontal, 12).padding(.vertical, 9)
                        .background(iAmHere ? Cumbre.bg : Cumbre.terraFill, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Cumbre.rule, lineWidth: 1))
            if error {
                Text(L("gym_here_error")).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
            }
        }
    }
}

/// Fila de 4 acciones secundarias, solo icono con su nombre debajo: Cómo llegar · Llamar · Web · Compartir.
struct GymQuickActions: View {
    let gym: Gym
    let onOpenWeb: (URL) -> Void

    var body: some View {
        let phone = gym.phone.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
        let web = gym.website.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : GymPresentation.webUrl($0) }
        HStack(spacing: 0) {
            action(L("gym_directions"), asset: "gym_ic_directions", enabled: true) { openDirections(lat: gym.lat, lon: gym.lon) }
            action(L("gym_call"), symbol: "phone", enabled: phone != nil) {
                if let phone { GymLinksIOS.dial(phone) }
            }
            action(L("gym_web"), symbol: "globe", enabled: web != nil) { if let web { onOpenWeb(web) } }
            ShareLink(item: GymLinksIOS.shareText(gym)) { face(L("gym_share"), symbol: "square.and.arrow.up", asset: nil) }
                .buttonStyle(.plain).frame(maxWidth: .infinity)
        }
    }

    private func action(_ label: String, symbol: String? = nil, asset: String? = nil, enabled: Bool,
                        _ run: @escaping () -> Void) -> some View {
        Button(action: run) { face(label, symbol: symbol, asset: asset) }
            .buttonStyle(.plain).disabled(!enabled).opacity(enabled ? 1 : 0.4).frame(maxWidth: .infinity)
    }

    private func face(_ label: String, symbol: String?, asset: String?) -> some View {
        VStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 14).fill(Cumbre.paper).frame(width: 46, height: 46)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                .overlay {
                    if let asset {
                        Image(asset).renderingMode(.template).resizable().scaledToFit().frame(width: 21, height: 21)
                            .foregroundStyle(Cumbre.terra)
                    } else if let symbol {
                        Image(systemName: symbol).font(.system(size: 19)).foregroundStyle(Cumbre.terra)
                    }
                }
            Text(label).font(.system(size: 11)).foregroundStyle(Cumbre.ink2).lineLimit(1)
        }
        .padding(.vertical, 2)
    }
}

/// Enlaces del rocódromo en iOS: llamar (abre el marcador), compartir y abrir el punto en Mapas.
enum GymLinksIOS {
    static func dial(_ phone: String) {
        let number = phone.filter { $0.isNumber || $0 == "+" }
        if let url = URL(string: "tel:\(number)") { UIApplication.shared.open(url) }
    }

    static func shareText(_ gym: Gym) -> String {
        let place = [gym.address, gym.city].compactMap { $0 }.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .joined(separator: ", ")
        return [gym.name, place.isEmpty ? nil : place, "https://www.google.com/maps/search/?api=1&query=\(gym.lat),\(gym.lon)"]
            .compactMap { $0 }.joined(separator: "\n")
    }

    static func openOnMap(_ gym: Gym) {
        let q = gym.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let url = URL(string: "maps://?ll=\(gym.lat),\(gym.lon)&q=\(q)") { UIApplication.shared.open(url) }
    }
}

/// Web e Instagram de la ficha dentro de la app (SFSafariViewController), como las Custom Tabs de Android.
struct SafariSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

/// Enlace que se abre en la hoja de Safari.
struct WebLink: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// «Añadir foto» de la ficha: va como reporte con foto para que un admin la apruebe («Solo fotos tuyas»).
struct GymPhotoContribute: View {
    let gym: Gym
    @StateObject private var vm = GymReportViewModel()
    @State private var item: PhotosPickerItem?
    @State private var started = false

    var body: some View {
        VStack(spacing: 4) {
            PhotosPicker(selection: $item, matching: .images) {
                Text(L("gym_photo_add")).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .lineLimit(2).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity).padding(.horizontal, 12).padding(.vertical, 12)
                    .background(Cumbre.paper, in: Capsule()).overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(vm.state == .sending)
            Text(L(noteKey)).font(.system(size: 12)).multilineTextAlignment(.center)
                .foregroundStyle(started && vm.state == .failed ? Cumbre.bad : Cumbre.ink2)
        }
        .onChange(of: item) { _, new in
            guard let new else { return }
            Task {
                if let data = try? await new.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    started = true
                    vm.reset()
                    vm.sendCorrection(gymId: gym.id, issueCodes: ["OTHER"], comment: "Foto de la comunidad", photo: image)
                }
                item = nil
            }
        }
    }

    private var noteKey: String {
        guard started else { return "gym_photo_own_only" }
        switch vm.state {
        case .sent: return "gym_sent"
        case .failed: return "gym_send_error"
        case .sending: return "gym_review_note"
        default: return "gym_photo_own_only"
        }
    }
}
