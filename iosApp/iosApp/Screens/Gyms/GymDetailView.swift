import SwiftUI
import Shared

/// Ficha de un rocódromo: la ficha (de la copia local al instante), el favorito y «Quién va hoy».
/// Espejo de `GymDetailViewModel.kt`.
@MainActor
final class GymDetailViewModel: ObservableObject {
    @Published var gym: Gym?
    @Published var loadFailed = false
    @Published var favorite = false
    @Published var people: [SchoolPresence] = []
    @Published var iAmHere = false
    @Published var presenceBusy = false
    @Published var presenceError = false
    /// Ya hay entreno apuntado hoy en este rocódromo.
    @Published var trainedToday = false

    private let gymId: Int64
    private let c = AppDependencies.shared.container.gyms

    init(gymId: Int64) {
        self.gymId = gymId
        gym = c.getCachedGyms.invoke().first { $0.id == gymId }
        trainedToday = c.recordGymSession.hasSessionToday(gymId: gymId)
    }

    func load() async {
        // La copia local ya pinta la ficha; el servidor la refresca (precio, horario, verificación).
        do {
            gym = try await c.getGym.execute(id: gymId)
            loadFailed = false
        } catch {
            loadFailed = gym == nil
        }
        if let ids = try? await c.getGymFavorites.execute() {
            favorite = ids.contains { $0.int64Value == gymId }
        }
    }

    func loadPresence(myUid: String?) async {
        do {
            let list = try await c.getGymPresence.execute(gymId: gymId)
            people = list
            iAmHere = myUid.map { uid in list.contains { $0.uid == uid } } ?? false
            presenceError = false
        } catch is CancellationError {
            // Cancelada al salir de la pantalla: no es un fallo real.
        } catch {
            presenceError = true
        }
    }

    func togglePresence(myUid: String?) async {
        guard !presenceBusy else { return }
        let here = iAmHere
        presenceBusy = true
        presenceError = false
        do {
            if here {
                try await c.clearGymPresence.execute(gymId: gymId)
            } else {
                _ = try await c.markGymPresence.execute(gymId: gymId)
            }
            iAmHere = !here
        } catch {
            presenceError = true
        }
        presenceBusy = false
        await loadPresence(myUid: myUid)
        // Marcar «Aquí» ya apunta el entreno en el servidor (PRESENCE): se relee para que el botón lo refleje.
        if !presenceError && !here {
            try? await c.recordGymSession.refresh()
            trainedToday = c.recordGymSession.hasSessionToday(gymId: gymId)
        }
    }

    /// «He entrenado aquí hoy»: apunta el día de hoy o, si ya estaba apuntado, lo quita. Funciona sin red.
    func toggleTraining() {
        let done = trainedToday
        trainedToday = !done
        Task {
            do {
                if done {
                    try await c.recordGymSession.undo(gymId: gymId)
                } else {
                    _ = try await c.recordGymSession.execute(gymId: gymId, source: .manual)
                }
            } catch {
                trainedToday = done
            }
        }
    }

    /// Relee si hay entreno de hoy (tras guardar la hoja de entreno).
    func refreshTrained() { trainedToday = c.recordGymSession.hasSessionToday(gymId: gymId) }

    func toggleFavorite() {
        let now = !favorite
        favorite = now
        Task {
            do {
                _ = try await c.setGymFavorite.execute(gymId: gymId, favorite: now)
            } catch {
                favorite = !now
            }
        }
    }
}

/// Rocódromo donde se crea la quedada (el asistente lo enseña fijado y solo pregunta cuándo).
struct GymPlace: Identifiable, Hashable {
    let id: Int64
    let name: String
}

/// Ficha completa de un rocódromo. Orden (Android, ronda 6): cabecera, «Estoy aquí / Quién va hoy», 4 acciones,
/// «He entrenado» + «Crear quedada», servicios, «Añadir foto» + «Corregir», modalidad, escala, horario, precio y notas.
struct GymDetailView: View {
    let gymId: Int64
    @StateObject private var vm: GymDetailViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var reportOpen = false
    @State private var trainOpen = false
    @State private var privacyOpen = false
    @State private var chatOpen = false
    @State private var web: WebLink?
    @State private var meetupPlace: GymPlace?
    @State private var openMeetupId: String?
    @State private var userLat: Double?
    @State private var userLon: Double?
    private let myUid = AppDependencies.shared.authBridge.currentUid()

    init(gymId: Int64) {
        self.gymId = gymId
        _vm = StateObject(wrappedValue: GymDetailViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            IconActionRow(count: 4) { w in
                IconAction(symbol: "chevron.left", a11y: L("common_close")) { dismiss() }.frame(width: w)
                IconAction(symbol: "bubble.left.and.bubble.right", a11y: L("gym_chat_open")) { chatOpen = true }.frame(width: w)
                IconAction(symbol: "mappin.and.ellipse", a11y: L("gym_map_open")) {
                    if let gym = vm.gym { GymLinksIOS.openOnMap(gym) }
                }.frame(width: w)
                IconAction(symbol: vm.favorite ? "star.fill" : "star",
                           a11y: L(vm.favorite ? "gym_favorite_remove" : "gym_favorite_add"),
                           active: vm.favorite) { vm.toggleFavorite() }.frame(width: w)
            }
            .padding(.horizontal, 12).padding(.vertical, 4)
            Divider().overlay(Cumbre.rule)
            if let gym = vm.gym {
                ScrollView { detail(gym) }
            } else if vm.loadFailed {
                Text(L("gym_load_error")).foregroundStyle(Cumbre.ink2).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity).padding(24)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task { await vm.load() }
        .task { await loadLocation() }
        .task { await vm.loadPresence(myUid: myUid) }
        .sheet(isPresented: $reportOpen) { if let gym = vm.gym { GymReportSheet(gym: gym) } }
        .sheet(isPresented: $privacyOpen) {
            PresencePrivacySheet { privacyOpen = false; Task { await vm.togglePresence(myUid: myUid) } }
        }
        .sheet(isPresented: $trainOpen, onDismiss: { vm.refreshTrained() }, content: {
            if let gym = vm.gym { GymTrainSheet(gym: gym, dateLabel: L("gym_train_today_label", GymDates.todayLong())) }
        })
        .sheet(item: $web) { link in SafariSheet(url: link.url).ignoresSafeArea() }
        .sheet(item: $meetupPlace) { place in
            CreateMeetupView(onCreated: { id in
                meetupPlace = nil
                openMeetupId = id
            }, gym: place)
        }
        .navigationDestination(isPresented: $chatOpen) {
            if let gym = vm.gym { GymChatView(gymId: gym.id, gymName: gym.name) }
        }
        .navigationDestination(isPresented: Binding(get: { openMeetupId != nil }, set: { if !$0 { openMeetupId = nil } })) {
            if let id = openMeetupId { MeetupDetailView(meetupId: id) }
        }
    }

    private func loadLocation() async {
        guard AppDependencies.shared.locationBridge.hasPermission() else { return }
        if let loc = try? await AppDependencies.shared.container.locationProvider?.current() {
            userLat = loc.lat
            userLon = loc.lon
        }
    }

    private func detail(_ gym: Gym) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            header(gym)
            GymPresenceCard(people: vm.people, iAmHere: vm.iAmHere, busy: vm.presenceBusy, error: vm.presenceError,
                            onJoin: { privacyOpen = true },
                            onLeave: { Task { await vm.togglePresence(myUid: myUid) } })
            GymQuickActions(gym: gym) { web = WebLink(url: $0) }.padding(.top, 4)
            if let handle = gym.instagram, !handle.trimmingCharacters(in: .whitespaces).isEmpty,
               let url = GymPresentation.instagramUrl(handle) {
                Button { web = WebLink(url: url) } label: {
                    Text(L("gym_instagram")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(4)
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            HStack(spacing: 8) {
                GymActionPill(text: L(vm.trainedToday ? "gym_train_short_done" : "gym_train_short"), primary: !vm.trainedToday) {
                    trainOpen = true
                }
                GymActionPill(text: L("gym_meetup_short")) { meetupPlace = GymPlace(id: gym.id, name: gym.name) }
            }
            .padding(.top, 4)
            if vm.trainedToday {
                Button { vm.toggleTraining() } label: {
                    Text(L("gym_train_undo")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(8)
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            GymServicesSection(gym: gym)
            HStack(alignment: .top, spacing: 8) {
                GymPhotoContribute(gym: gym).frame(maxWidth: .infinity)
                Button { reportOpen = true } label: {
                    Text(L("gym_fix_cta")).font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                        .lineLimit(2).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 8)
                        .overlay(Capsule().stroke(Cumbre.ink2, lineWidth: 1))
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            .padding(.top, 4)
            GymInfoSections(gym: gym)
            GymFooterNotes(gym: gym)
        }
        .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 32)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func header(_ gym: Gym) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            GymTags(gym: gym, status: gym.openStatusNow())
            Text(gym.name).font(Cumbre.serif(27, .bold)).foregroundStyle(Cumbre.ink).padding(.top, 4)
            let place = [gym.city, gym.province].filter { !$0.isEmpty }.joined(separator: ", ")
            let address = (gym.address?.isEmpty == false ? gym.address : nil) ?? (place.isEmpty ? nil : place)
            let line = [address, GymPresentation.distanceText(distanceKm(gym))].compactMap { $0 }.joined(separator: " · ")
            if !line.isEmpty { Text(line).font(.system(size: 14)).foregroundStyle(Cumbre.ink2) }
        }
    }

    private func distanceKm(_ gym: Gym) -> Double? {
        guard let lat = userLat, let lon = userLon else { return nil }
        return Geo.shared.haversineKm(lat1: lat, lon1: lon, lat2: gym.lat, lon2: gym.lon)
    }
}
