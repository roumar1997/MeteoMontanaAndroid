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

/// Rocódromo donde se crea la quedada (el formulario enseña su nombre fijo en vez del buscador de escuela).
struct GymPlace: Identifiable, Hashable {
    let id: Int64
    let name: String
}

/// Ficha completa de un rocódromo (maqueta aprobada): etiquetas, nombre, dirección y distancia, acciones
/// (Cómo llegar, Web, Instagram), modalidad, horario, precio con su fecha, servicios, «Quién va hoy», crear quedada
/// y «¿Algo no es así? Corregir». Lo que no se sabe sale como «Sin confirmar», nunca como «No».
struct GymDetailView: View {
    let gymId: Int64
    @StateObject private var vm: GymDetailViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var reportOpen = false
    @State private var meetupPlace: GymPlace?
    @State private var openMeetupId: String?
    @State private var userLat: Double?
    @State private var userLon: Double?

    init(gymId: Int64) {
        self.gymId = gymId
        _vm = StateObject(wrappedValue: GymDetailViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            IconActionRow(count: 2) { w in
                IconAction(symbol: "chevron.left", a11y: L("common_back")) { dismiss() }.frame(width: w)
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
        .sheet(isPresented: $reportOpen) { if let gym = vm.gym { GymReportSheet(gym: gym) } }
        .sheet(item: $meetupPlace) { place in
            CreateMeetupView(onCreated: { id in
                meetupPlace = nil
                openMeetupId = id
            }, gym: place)
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
            actions(gym)
            GymInfoSections(gym: gym)
            GymPresenceSection(vm: vm)
            GymActionPill(text: L(vm.trainedToday ? "gym_train_done" : "gym_train_today"), primary: !vm.trainedToday) {
                vm.toggleTraining()
            }
            .padding(.top, 8)
            if vm.trainedToday {
                Button { vm.toggleTraining() } label: {
                    Text(L("gym_train_undo")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).padding(8)
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            GymActionPill(text: L("gym_create_meetup")) { meetupPlace = GymPlace(id: gym.id, name: gym.name) }
            GymDashedPill(text: L("gym_fix_cta")) { reportOpen = true }.padding(.top, 12)
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

    /// «Cómo llegar» (primaria), «Web» e «Instagram» si la ficha los trae.
    private func actions(_ gym: Gym) -> some View {
        HStack(spacing: 8) {
            GymActionPill(text: L("gym_directions"), icon: "gym_ic_directions", primary: true) {
                openDirections(lat: gym.lat, lon: gym.lon)
            }
            if let site = gym.website, !site.trimmingCharacters(in: .whitespaces).isEmpty, let url = GymPresentation.webUrl(site) {
                GymActionPill(text: L("gym_web")) { UIApplication.shared.open(url) }
            }
            if let handle = gym.instagram, !handle.trimmingCharacters(in: .whitespaces).isEmpty,
               let url = GymPresentation.instagramUrl(handle) {
                GymActionPill(text: L("gym_instagram"), icon: "gym_ic_instagram") { UIApplication.shared.open(url) }
            }
        }
        .padding(.top, 4)
    }
}
