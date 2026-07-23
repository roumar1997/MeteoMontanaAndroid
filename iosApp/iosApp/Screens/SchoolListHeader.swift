import SwiftUI
import Shared
import CoreLocation

// Lista de escuelas — réplica fiel de SchoolListScreen.kt de Android:
// fila de iconos, header "Escuelas" + count + "+ Enviar escuela", banner ☕,

// Cabecera de Escuelas: iconos, buscador, mapa desplegable, filtros, donar.
// Reparto del antiguo SchoolListView.swift de 1.217 lineas.

struct CompareBar: View {
    let count: Int
    let canCompare: Bool
    let onClear: () -> Void
    let onCompare: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Button(action: onClear) {
                Image(systemName: "xmark").font(.system(size: 16)).foregroundStyle(.white)
                    .frame(width: 36, height: 36)
            }
            Text("\(count) seleccionada\(count == 1 ? "" : "s")")
                .font(.system(size: 14)).foregroundStyle(.white)
            Spacer()
            if canCompare {
                Button(action: onCompare) {
                    Text("COMPARAR ▸").font(Cumbre.mono(13, .bold)).tracking(0.8)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Cumbre.terra, in: RoundedRectangle(cornerRadius: 6))
                }
            } else {
                Text("Elige otra para comparar")
                    .font(.system(size: 13)).foregroundStyle(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .background(Cumbre.ink)
        .padding(.horizontal, 12).padding(.bottom, 8)
    }
}

// MARK: - Header

struct TopIconsRow: View {
    var unreadCount: Int = 0
    var chatUnread: Int = 0
    var onNotificationsClosed: () -> Void = {}
    @State private var showAccount = false
    @State private var showNotifications = false
    @State private var showSearch = false
    @State private var showChats = false
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        HStack(spacing: 4) {
            Spacer()
            HelpButton(topicKey: "schools")
            iconButton("magnifyingglass") { showSearch = true }
            chatButton
            iconButton(theme.iconName) { theme.cycle() }
            bellButton
            // El perfil ya no va aquí: tiene su propia pestaña inferior.
        }
        .padding(.horizontal, 4)
        .padding(.top, 4)
        .sheet(isPresented: $showAccount) { AccountView() }
        .sheet(isPresented: $showNotifications, onDismiss: onNotificationsClosed) { NotificationsView() }
        .sheet(isPresented: $showSearch) { SearchUsersView() }
        .sheet(isPresented: $showChats) { NavigationStack { ChatListView() } }
    }

    private func iconButton(_ name: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 18))
                .foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // Icono de mensajes con badge de chats sin leer (número o "9+").
    private var chatButton: some View {
        Button { showChats = true } label: {
            Image(systemName: "bubble.left")
                .font(.system(size: 18)).foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40).contentShape(Rectangle())
                .overlay(alignment: .topTrailing) {
                    if chatUnread > 0 {
                        Text(chatUnread > 9 ? "9+" : "\(chatUnread)")
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Capsule().fill(Cumbre.bad))
                            .offset(x: -4, y: 4)
                    }
                }
        }
        .buttonStyle(.plain)
    }

    // Campana con badge rojo de no leídas (número o "9+").
    private var bellButton: some View {
        Button { showNotifications = true } label: {
            Image(systemName: "bell")
                .font(.system(size: 18)).foregroundStyle(Cumbre.ink)
                .frame(width: 40, height: 40).contentShape(Rectangle())
                .overlay(alignment: .topTrailing) {
                    if unreadCount > 0 {
                        Text(unreadCount > 9 ? "9+" : "\(unreadCount)")
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Capsule().fill(Cumbre.bad))
                            .offset(x: -4, y: 4)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

struct HeaderEscuelas: View {
    let count: Int?
    @State private var showSubmit = false
    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(NSLocalizedString("schools_title", comment: ""))
                    .font(Cumbre.serif(34, .bold))
                    .foregroundStyle(Cumbre.ink)
                if let count {
                    Text("\(count) escuelas")
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            Button { showSubmit = true } label: {
                OutlinedCumbreButton(text: NSLocalizedString("schools_submit", comment: ""), tint: Cumbre.terra)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showSubmit) { SubmitSchoolView() }
    }
}

struct CoffeeBanner: View {
    @State private var showDonate = false
    var body: some View {
        HStack(spacing: 8) {
            Text("☕").font(.system(size: 30))
            VStack(alignment: .leading, spacing: 1) {
                Text("¿Te ayuda la app?")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Text("Mantenida con amor por la comunidad escaladora")
                    .font(.system(size: 12))
                    .foregroundStyle(Cumbre.ink2.opacity(0.8))
            }
            Spacer()
            Button { showDonate = true } label: { OutlinedCumbreButton(text: "Apóyanos", tint: Cumbre.ink) }
                .buttonStyle(.plain)
        }
        .padding(12)
        .background(Cumbre.terraBg)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showDonate) { DonateView() }
    }
}

/// Diálogo "Apóyanos" — espejo del DonateDialog de Android.
struct DonateView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    var body: some View {
        VStack(spacing: 16) {
            Text("☕").font(.system(size: 56)).padding(.top, 24)
            Text("¿Te ayuda la app?").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text("MeteoMontana es gratis y sin anuncios, mantenida por la comunidad escaladora. Si te resulta útil, invítame a un café.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            VStack(alignment: .leading, spacing: 6) {
                feature("Previsión de escalada por hora")
                feature("Mapas, bloques y vías de cada escuela")
                feature("Notas y fotos de la comunidad")
                feature("Sin anuncios, sin rastreadores")
            }.padding(.horizontal, 24).padding(.top, 4)
            Button {
                openURL(URL(string: "https://ko-fi.com/climbingteams")!)
            } label: {
                Text("☕ INVÍTAME A UN CAFÉ").font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                    .background(Cumbre.terra)
            }
            .buttonStyle(.plain).padding(.horizontal, 24).padding(.top, 8)
            Button("Ahora no") { dismiss() }.foregroundStyle(Cumbre.ink3).padding(.top, 4)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Cumbre.bg.ignoresSafeArea())
    }
    private func feature(_ t: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Cumbre.ok).font(.system(size: 14))
            Text(t).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
        }
    }
}

struct SearchField: View {
    @Binding var text: String
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink3)
            TextField("Busca tu escuela o vía/bloque…", text: $text)
                .foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

/// Toggle "VER MAPA" + panel con todas las escuelas filtradas como marcadores
/// coloreados por score (tap → detalle). Espejo de SchoolsMapPanel.kt.
struct MapToggleAndPanel: View {
    @ObservedObject var vm: SchoolListViewModel
    let onOpen: (School) -> Void
    @State private var show = false
    @State private var popup: School?
    @State private var mapStyle: MapStyleKind = .topo
    @State private var zoom: Double = 8

    var body: some View {
        VStack(spacing: 0) {
            // Botón terracota (borde + texto + tinte) para que se vea claramente
            // pulsable (antes era texto gris que no parecía botón).
            Button { withAnimation { show.toggle() } } label: {
                HStack(spacing: 6) {
                    Image(systemName: "map").font(.system(size: 13))
                    Text(show ? NSLocalizedString("schools_hide_map", comment: "") : NSLocalizedString("schools_view_map", comment: "")).font(Cumbre.mono(11, .bold)).tracking(0.8)
                    Spacer()
                    Image(systemName: show ? "chevron.up" : "chevron.down").font(.system(size: 11))
                }
                .foregroundStyle(Cumbre.terra)
                .padding(.horizontal, 14).padding(.vertical, 11)
                .frame(maxWidth: .infinity)
                .background(Cumbre.terra.opacity(0.08))
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra, lineWidth: 1))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16).padding(.vertical, 4)

            if show {
                ZStack(alignment: .topLeading) {
                    MapLibreView(center: center, zoom: vm.userLat != nil ? 8 : 6,
                                 markers: markers, style: mapStyle,
                                 autoFitToMarkers: true,
                                 refitOnAnyChange: true,
                                 onZoomChange: { zoom = $0 },
                                 onTapMarker: { id in
                                     popup = vm.filtered.first { $0.id == id }
                                 })
                    .frame(height: 300)
                    MapStyleChips(selection: $mapStyle)
                }
                Divider().overlay(Cumbre.rule)
            }
        }
        // Popup al tocar un marcador: nombre, score, tags, CÓMO LLEGAR + VER DETALLE
        // (espejo de SchoolsMapPanel.kt).
        .sheet(item: $popup) { s in
            SchoolMapPopup(school: s, score: vm.scores[s.id].map { Int($0.todayScore) }) {
                popup = nil; onOpen(s)
            }
            .presentationDetents([.height(280)])
        }
    }

    private var markers: [CumbreMarker] {
        var ms: [CumbreMarker] = []
        // Punto azul de mi ubicación (confirma que se cogió la ubicación).
        if let la = vm.userLat, let lo = vm.userLon {
            ms.append(CumbreMarker(
                id: "__USER__",
                coordinate: CLLocationCoordinate2D(latitude: la, longitude: lo),
                title: "", kind: .user))
        }
        for s in vm.filtered.prefix(200) {
            let score = vm.scores[s.id].map { Int($0.todayScore) }
            ms.append(CumbreMarker(
                id: s.id,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                title: s.name,
                subtitle: score.map { "\($0)/100" },
                kind: .score,
                color: UIColor(score.map { Cumbre.score($0) } ?? Cumbre.rule),
                score: score,
                name: s.name,
                showName: zoom >= 8.5))
        }
        return ms
    }

    private var center: CLLocationCoordinate2D {
        if let la = vm.userLat, let lo = vm.userLon {
            return CLLocationCoordinate2D(latitude: la, longitude: lo)
        }
        let pts = vm.filtered
        if pts.isEmpty { return CLLocationCoordinate2D(latitude: 40.2, longitude: -3.7) }
        let lat = pts.map { $0.lat }.reduce(0, +) / Double(pts.count)
        let lon = pts.map { $0.lon }.reduce(0, +) / Double(pts.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }
}

/// Popup de una escuela al tocar su marcador en el panel de mapa de la lista.
/// Nombre + score + tags + "CÓMO LLEGAR" y "VER DETALLE" (espejo de SchoolsMapPanel).
struct SchoolMapPopup: View {
    let school: School
    let score: Int?
    let onDetail: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                if let s = score {
                    // Chip redondeado (estilo mini-ficha, adiós al cuadrado duro).
                    VStack(spacing: 0) {
                        Text("\(s)").font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.score(s))
                        Text(Cumbre.scoreLabel(s)).font(.system(size: 8, weight: .bold)).foregroundStyle(Cumbre.score(s))
                    }
                    .frame(width: 60, height: 60)
                    .background(Cumbre.score(s).opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.score(s), lineWidth: 1.5))
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(school.name).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                    Text(tags).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            HStack(spacing: 10) {
                DirectionsButton(lat: school.lat, lon: school.lon, label: school.name)
                Button(action: onDetail) {
                    Text("VER DETALLE ▸").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Cumbre.terra)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }.buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private var tags: String {
        [school.rockType?.uppercased(), school.region, school.style]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
    }
}

/// Barra de filtros — réplica de SchoolFiltersBar.kt: secciones apiladas
/// (DISTANCIA, ESTILO, TIPO DE ROCA, FAVORITOS, ORDENAR POR), cada una con su
/// eyebrow y una fila horizontal de chips seleccionables.
struct FilterChips: View {
    @ObservedObject var vm: SchoolListViewModel
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            section("DISTANCIA") {
                chipRow(SchoolListViewModel.distanceOptions, id: { $0.map { String(Int($0)) } ?? "all" },
                        isSel: { $0 == vm.maxDistanceKm },
                        label: { $0 == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int($0!)) km" }) { vm.maxDistanceKm = $0 }
            }
            section("ESTILO") {
                chipRow([String?.none] + vm.styles.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.style },
                        label: { $0 ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.style = $0 }
            }
            section("TIPO DE ROCA") {
                chipRow([String?.none] + vm.rocks.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.rock },
                        label: { $0 ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.rock = $0 }
            }
            section("MOSTRAR") {
                chipRow(SchoolListViewModel.ShowMode.allCases, id: { $0.rawValue },
                        isSel: { $0 == vm.showMode },
                        label: { $0.rawValue }) { vm.showMode = $0 }
            }
            section("ORDENAR POR") {
                chipRow(SchoolListViewModel.SortMode.allCases, id: { $0.rawValue },
                        isSel: { $0 == vm.sortBy },
                        label: { $0.rawValue }) { vm.sortBy = $0 }
            }
        }
        .padding(.vertical, 8)
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).eyebrow().padding(.horizontal, 12)
            content()
        }
    }

    private func chipRow<T>(_ items: [T], id: @escaping (T) -> String,
                            isSel: @escaping (T) -> Bool, label: @escaping (T) -> String,
                            onPick: @escaping (T) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    Button { onPick(item) } label: { chip(label(item), active: isSel(item)) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func chip(_ t: String, active: Bool) -> some View {
        Text(t)
            .font(Cumbre.mono(11, .bold))
            .tracking(0.8)
            .foregroundStyle(active ? .white : Cumbre.ink2)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? Cumbre.terra : Cumbre.paper)
            .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
    }
}
