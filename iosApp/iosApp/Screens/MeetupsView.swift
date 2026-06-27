import SwiftUI
import Shared

// ── ViewModel ──────────────────────────────────────────────────────────────

@MainActor
final class MeetupsViewModel: ObservableObject {
    @Published var meetups: [Meetup] = []
    @Published var loading = false
    @Published var error: String?
    @Published var filterRelation: String? = nil   // nil = todas, "following"

    private let getMeetups = AppDependencies.shared.container.getMeetups

    init() { Task { await load() } }

    func load(relation: String? = nil) async {
        loading = true
        error = nil
        let rel = relation ?? filterRelation
        do {
            if let uc = getMeetups {
                let result = try await uc.execute(schoolId: nil, date: nil, relation: rel)
                meetups = result
            }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    func setFilter(_ relation: String?) {
        filterRelation = relation
        Task { await load(relation: relation) }
    }
}

// ── List view ──────────────────────────────────────────────────────────────

struct MeetupsView: View {
    @StateObject private var vm = MeetupsViewModel()
    @State private var showCreate = false
    @State private var selectedMeetupId: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("QUEDADAS")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .tracking(1.8)
                            .foregroundColor(Cumbre.ink.opacity(0.6))
                        Text(vm.meetups.isEmpty ? "Quedar a escalar" : "\(vm.meetups.count) activas")
                            .font(.headline)
                    }
                    Spacer()
                    Button { Task { await vm.load() } } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .foregroundColor(Cumbre.ink)
                    Button { showCreate = true } label: {
                        Image(systemName: "plus")
                            .foregroundColor(Cumbre.terra)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Cumbre.bg)
                .overlay(Divider(), alignment: .bottom)

                // Filter chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterPill(label: "TODAS",     selected: vm.filterRelation == nil)       { vm.setFilter(nil) }
                        FilterPill(label: "SIGUIENDO", selected: vm.filterRelation == "following") { vm.setFilter("following") }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
                Divider()

                // Content
                Group {
                    if vm.loading && vm.meetups.isEmpty {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let err = vm.error, vm.meetups.isEmpty {
                        VStack(spacing: 12) {
                            Text("No se pudo cargar").foregroundColor(Cumbre.ink.opacity(0.6))
                            Button("REINTENTAR") { Task { await vm.load() } }
                                .foregroundColor(Cumbre.terra)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if vm.meetups.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "person.3").font(.system(size: 40))
                                .foregroundColor(Cumbre.ink.opacity(0.4))
                            Text("Sin quedadas activas").foregroundColor(Cumbre.ink.opacity(0.6))
                            Text("Crea una para quedar a escalar")
                                .font(.footnote).foregroundColor(Cumbre.ink.opacity(0.5))
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List {
                            ForEach(vm.meetups, id: \.id) { meetup in
                                NavigationLink(destination: MeetupDetailView(meetupId: meetup.id)) {
                                    MeetupRowView(meetup: meetup)
                                }
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                            }
                        }
                        .listStyle(.plain)
                    }
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showCreate) {
                CreateMeetupView { _ in
                    showCreate = false
                    Task { await vm.load() }
                }
            }
        }
    }
}

// ── Row ─────────────────────────────────────────────────────────────────────

struct MeetupRowView: View {
    let meetup: Meetup

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Photo/avatar
            let photo = meetup.photoUrl ?? meetup.creatorPhotoUrl
            ZStack {
                RoundedRectangle(cornerRadius: 4).fill(Cumbre.ink.opacity(0.06))
                if let url = photo.flatMap({ URL(string: $0) }) {
                    AsyncImage(url: url) { img in img.resizable().scaledToFill() }
                        placeholder: { Color.clear }
                        .clipped()
                } else {
                    Image(systemName: "person.3").foregroundColor(Cumbre.ink.opacity(0.4))
                }
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 4))

            VStack(alignment: .leading, spacing: 3) {
                if let school = meetup.schoolName {
                    Text(school.uppercased())
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .tracking(1.5)
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                }
                Text(meetup.name).font(.body).fontWeight(.medium)

                HStack(spacing: 6) {
                    Image(systemName: "calendar").font(.caption2).foregroundColor(Cumbre.ink.opacity(0.5))
                    let datesText: String = {
                        let days = meetup.days
                        if days.count == 1 { return days[0] }
                        return "\(days.first ?? "") – \(days.last ?? "")"
                    }()
                    Text(datesText).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    if meetup.privacy != "OPEN" {
                        Image(systemName: "lock").font(.caption2).foregroundColor(Cumbre.ink.opacity(0.5))
                        Text(privacyLabel(meetup.privacy)).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    }
                }

                HStack(spacing: 6) {
                    Image(systemName: "person").font(.caption2).foregroundColor(Cumbre.ink.opacity(0.5))
                    let limitText: String = {
                        if let lim = meetup.memberLimit { return "\(meetup.memberCount)/\(lim.int32Value)" }
                        return "\(meetup.memberCount)"
                    }()
                    Text(limitText).font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    if let disc = meetup.discipline {
                        Text("· \(disciplineLabel(disc))").font(.caption).foregroundColor(Cumbre.ink.opacity(0.6))
                    }
                }
            }

            Spacer()

            // Badge
            VStack {
                if meetup.joined {
                    Text("UNIDO")
                        .font(.caption2).fontWeight(.bold)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.terra.opacity(0.15))
                        .foregroundColor(Cumbre.terra)
                        .cornerRadius(4)
                } else if meetup.isFull {
                    Text("LLENO")
                        .font(.caption2).fontWeight(.bold)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Cumbre.ink.opacity(0.08))
                        .foregroundColor(Cumbre.ink.opacity(0.5))
                        .cornerRadius(4)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Cumbre.bg)
        .overlay(Divider(), alignment: .bottom)
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private struct FilterPill: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(selected ? Cumbre.terra.opacity(0.15) : Cumbre.bg)
                .foregroundColor(selected ? Cumbre.terra : Cumbre.ink.opacity(0.6))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(selected ? Cumbre.terra : Cumbre.ink.opacity(0.2), lineWidth: 1))
                .cornerRadius(4)
        }
    }
}

func privacyLabel(_ privacy: String) -> String {
    switch privacy {
    case "FOLLOWERS": return "Solo seguidores"
    case "WOMEN":     return "Solo mujeres"
    default:          return "Abierta"
    }
}

func disciplineLabel(_ discipline: String) -> String {
    switch discipline {
    case "BOULDER": return "Bloque"
    case "ROUTE":   return "Vía"
    case "BOTH":    return "Bloque + Vía"
    default:        return discipline
    }
}
