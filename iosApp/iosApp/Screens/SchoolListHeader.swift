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
            Text(L("%@ seleccionada%@", count, count == 1 ? "" : "s"))
                .font(.system(size: 14)).foregroundStyle(.white)
            Spacer()
            if canCompare {
                Button(action: onCompare) {
                    Text("COMPARAR ▸").font(Cumbre.mono(13, .bold)).tracking(0.8)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 6))
                }
            } else {
                Text("Elige otra para comparar")
                    .font(.system(size: 13)).foregroundStyle(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        // Fondo FIJO oscuro: Cumbre.ink se invierte en modo oscuro y la barra
        // salía blanca y deslumbrante (feedback de Rodrigo). El texto es
        // blanco en ambos temas; el borde la despega del fondo oscuro.
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(Color(red: 0.09, green: 0.09, blue: 0.08))
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(Cumbre.rule, lineWidth: 1))
        )
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
            // Los cinco iconos dentro de una píldora, como en Android
            // (CumbrePillGroup) — sueltos sobre el fondo ocupaban toda la
            // cabecera y era lo que hacía que no se pareciera a la de Android
            // (Rodrigo, 2026-08-21).
            HStack(spacing: 0) {
                HelpButton(topicKey: "schools")
                iconButton("magnifyingglass") { showSearch = true }
                chatButton
                iconButton(theme.iconName) { theme.cycle() }
                bellButton
                // El perfil ya no va aquí: tiene su propia pestaña inferior.
            }
            .padding(.horizontal, 2)
            .background(Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
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
    /// "Enviar piedra": elegir una foto y proponerla en la escuela donde se hizo.
    var onSubmitBlockPhoto: () -> Void = {}
    @State private var showSubmit = false
    @State private var aportando = false

    /// Ejecuta [accion] cuando la hoja ya se ha cerrado del todo.
    ///
    /// Encadenar dos presentaciones en SwiftUI (cerrar una hoja y abrir otra en
    /// el mismo instante) hace que la segunda se pierda sin decir nada. Es lo
    /// que dejaba "Una piedra, desde una foto" sin hacer nada.
    private func trasCerrar(_ accion: @escaping () -> Void) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: accion)
    }
    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(NSLocalizedString("schools_title", comment: ""))
                    .font(Cumbre.serif(34, .bold))
                    .foregroundStyle(Cumbre.ink)
                if let count {
                    Text(L("%@ escuelas", count))
                        .font(.system(size: 14))
                        .foregroundStyle(Cumbre.ink3)
                }
            }
            Spacer()
            // UN solo botón: corto, entra en cualquier pantalla. Las dos formas
            // de aportar viven en la hoja, donde cada una cabe con su
            // explicación — "enviar piedra" no se entiende a secas.
            Button { aportando = true } label: {
                OutlinedCumbreButton(text: L("+ Aportar"), tint: Cumbre.terra)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .sheet(isPresented: $showSubmit) { SubmitSchoolView() }
        .sheet(isPresented: $aportando) {
            AportarSheet(
                onPiedra: { aportando = false; trasCerrar { onSubmitBlockPhoto() } },
                onEscuela: { aportando = false; trasCerrar { showSubmit = true } })
                .presentationDetents([.height(260)])
        }
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
            Button { showDonate = true } label: { OutlinedCumbreButton(text: L("Apóyanos"), tint: Cumbre.ink) }
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
                feature(L("Previsión de escalada por hora"))
                feature(L("Mapas, bloques y vías de cada escuela"))
                feature(L("Notas y fotos de la comunidad"))
                feature(L("Sin anuncios, sin rastreadores"))
            }.padding(.horizontal, 24).padding(.top, 4)
            Button {
                openURL(URL(string: "https://ko-fi.com/climbingteams")!)
            } label: {
                Text("☕ INVÍTAME A UN CAFÉ").font(Cumbre.mono(13, .bold)).tracking(0.8)
                    .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                    .background(Cumbre.terraFill)
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
            TextField("Busca escuelas, vías y bloques…", text: $text)
                .foregroundStyle(Cumbre.ink)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
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
    // Satélite por defecto, paridad con el mapa de detalle de escuela
    // (Álvaro, 2026-09-01: "que se abra en satélite por defecto").
    @State private var mapStyle: MapStyleKind = .satellite
    @State private var zoom: Double = 8
    @State private var fullscreenMap = false

    private func mapBox(height: CGFloat, isFullscreen: Bool = false) -> some View {
        ZStack(alignment: .topLeading) {
            MapLibreView(center: center, zoom: vm.userLat != nil ? 8 : 6,
                         markers: markers, style: mapStyle,
                         autoFitToMarkers: true,
                         refitOnAnyChange: true,
                         onZoomChange: { zoom = $0 },
                         onTapMarker: { id in
                             popup = vm.filtered.first { $0.id == id }
                         },
                         // A pantalla completa los botones de arriba bajan más
                         // (bajo la isla/notch) — la brújula tiene que bajar con
                         // ellos o se solapan (Álvaro, 2026-09-01).
                         compassTopMargin: isFullscreen ? 106 : 56)
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
            // Ampliar / salir de pantalla completa — arriba a la izquierda,
            // misma posición y forma que en el detalle de escuela. En
            // pantalla completa se baja bajo la isla/notch (antes quedaba
            // debajo del reloj y no se podía pulsar — Álvaro, 2026-09-01).
            VStack {
                HStack {
                    Button { fullscreenMap.toggle() } label: {
                        Image(systemName: fullscreenMap
                              ? "arrow.down.right.and.arrow.up.left"
                              : "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    // Topo/satélite de un toque — mismo botón que el detalle
                    // de escuela.
                    Button {
                        mapStyle = (mapStyle == .satellite) ? .topo : .satellite
                    } label: {
                        Image(systemName: "square.3.layers.3d")
                            .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                            .frame(width: 34, height: 34)
                            .background(Cumbre.bg)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, isFullscreen ? 50 : 0)
                Spacer()
            }
            .padding(10)
            .frame(maxWidth: .infinity, maxHeight: isFullscreen ? .infinity : height)
            .allowsHitTesting(true)
        }
        // Altura fija SOLO para la tarjeta inline (300pt); a pantalla
        // completa se deja que el ZStack tome el espacio que le proponga el
        // fullScreenCover (ya es toda la pantalla) — forzarla ANTES con
        // UIScreen.main.bounds.height y añadir DESPUÉS la barra de abajo con
        // safeAreaInset sumaba las dos alturas y desbordaba la pantalla: la
        // fila ESTILO quedaba cortada y los botones de arriba se dejaban de
        // poder pulsar bien (Álvaro, 2026-09-01: "ahora va mucho peor").
        .frame(height: isFullscreen ? nil : height)
        // A pantalla completa, hay demasiadas escuelas para verlas bien sin
        // filtrar — DISTANCIA y ESTILO en una barra fija ABAJO (no un panel
        // lateral que tapaba el mapa). safeAreaInset coloca la barra POR
        // ENCIMA del indicador de inicio y RESERVA su alto (el mapa no
        // desborda la pantalla).
        .safeAreaInset(edge: .bottom) {
            if isFullscreen { fullscreenFilters }
        }
        // Popup al tocar un marcador: nombre, score, tags, CÓMO LLEGAR + VER
        // DETALLE (espejo de SchoolsMapPanel.kt). Colgado de ESTA vista (mapBox)
        // y no del body de fuera: a pantalla completa el mapa vive dentro de un
        // fullScreenCover, una presentación aparte — un .sheet colgado de la
        // vista de DETRÁS del cover no puede aparecer mientras el cover está
        // abierto (iOS lo deja pendiente y solo lo muestra al cerrar el cover,
        // con el valor de `popup` que hubiera en ESE momento: por eso al pulsar
        // una escuela en pantalla completa "no pasaba nada" y al salir aparecía
        // otra). Mismo patrón que ya usa mapArea en SchoolMapSection.swift.
        .sheet(item: $popup) { s in
            SchoolMapPopup(school: s, score: vm.scores[s.id].map { Int($0.todayScore) }) {
                popup = nil; onOpen(s)
            }
            .presentationDetents([.height(280)])
        }
    }

    private var fullscreenFilters: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("DIST.").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                    ForEach(SchoolListViewModel.distanceOptions, id: \.self) { d in
                        filterPill(
                            label: d == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int(d!)) km",
                            selected: d == vm.maxDistanceKm) { vm.maxDistanceKm = d }
                    }
                }
            }
            HStack(spacing: 6) {
                Text("ESTILO").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                ForEach([String?.none] + vm.styles.map { Optional($0) }, id: \.self) { s in
                    filterPill(
                        label: s ?? NSLocalizedString("schools_filter_all", comment: ""),
                        selected: s == vm.style) { vm.style = s }
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        // Material de cristal nativo (blur real), no un color plano con
        // opacidad — Álvaro, 2026-09-01: "no está en liquid glass".
        .background(.ultraThinMaterial)
    }

    private func filterPill(label: String, selected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(Cumbre.mono(11, .bold))
                .foregroundStyle(selected ? .white : Cumbre.ink2)
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(selected ? Cumbre.terraFill : Color.clear,
                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                    .stroke(selected ? Color.clear : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

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
                .background(Cumbre.terra.opacity(0.08), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.terra, lineWidth: 1))
                .contentShape(RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16).padding(.vertical, 4)

            // !fullscreenMap: si no, esta tarjeta se queda montada DETRÁS del
            // fullScreenCover de abajo mientras dura pantalla completa — dos
            // mapBox vivos a la vez, cada uno con su propio .sheet(item:
            // $popup) colgando del MISMO estado. Al tocar un marcador en
            // pantalla completa, los dos intentaban presentar el aviso a la
            // vez y SwiftUI resolvía el conflicto cerrando el fullScreenCover
            // para que el de detrás (el único no cubierto) pudiera mostrarlo
            // (Álvaro, 2026-09-30). Mismo guardado que ya usa mapArea en
            // SchoolMapSection.swift.
            if show && !fullscreenMap {
                mapBox(height: 300)
                Divider().overlay(Cumbre.rule)
            }
        }
        .fullScreenCover(isPresented: $fullscreenMap) {
            ZStack {
                Cumbre.bg.ignoresSafeArea()
                mapBox(height: UIScreen.main.bounds.height, isFullscreen: true)
            }
        }
        // El popup de la escuela pulsada ahora cuelga de mapBox (ver ahí el
        // porqué) — a pantalla completa mapBox vive dentro del fullScreenCover
        // de arriba, así que su propio .sheet se presenta en ese contexto.
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
                        .background(Cumbre.terraFill)
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
        [school.rockType.map { rockLabel($0).uppercased() }, school.region.map(regionLabel), school.style.map(styleLabel)]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: "  ·  ")
    }
}

/// Escalera REAL de grados — la MISMA lista que usa el editor de vías
/// (`BOULDER_GRADES` en ProposeFlow.swift), sin inventar nada. "PROY" (proyecto,
/// sin grado) no es un punto de la escalera, así que se excluye del rango.
let EXPLORE_GRADE_LADDER: [String] = BOULDER_GRADES.filter { $0 != "PROY" }

/// Barra de filtros — réplica de SchoolFiltersBar.kt, con el selector
/// Escuelas/Bloques arriba (Álvaro, 2026-10-01: la versión sin pestaña no
/// convencía en uso real — vuelta al mockup con pestaña + selector de
/// escuelas + slider de grado + orientación, BLOCK_SEARCH_DESIGN.md §4/§8).
struct FilterChips: View {
    @ObservedObject var vm: SchoolListViewModel
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            tabSwitcher
            section(L("DISTANCIA")) {
                chipRow(SchoolListViewModel.distanceOptions, id: { $0.map { String(Int($0)) } ?? "all" },
                        isSel: { $0 == vm.maxDistanceKm },
                        label: { $0 == nil ? NSLocalizedString("schools_filter_all", comment: "") : "\(Int($0!)) km" }) { vm.maxDistanceKm = $0 }
            }
            // Sin esto, un permiso denegado se quedaba sin forma de arreglarse
            // desde aquí — el mapa de Escuelas no pintaba el punto azul ni
            // aplicaba "cercanía" en silencio, para siempre (Álvaro, 2026-10-01).
            if vm.userLat == nil {
                Button { vm.requestLocation() } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "location.slash").foregroundStyle(Cumbre.terra)
                        Text(L("Sin ubicación — toca para activarla"))
                            .font(.system(size: 12.5)).foregroundStyle(Cumbre.ink2)
                        Spacer()
                        Text(L("ACTIVAR")).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                    }
                    .padding(10)
                    .background(Cumbre.terraBg).overlay(Rectangle().stroke(Cumbre.terra.opacity(0.4), lineWidth: 1))
                }.buttonStyle(.plain)
            }
            if vm.exploreTab == .blocks {
                section(L("ESCUELAS EN ESTE RADIO · ELIGE 1 O VARIAS")) { schoolPicker }
                section(L("GRADO")) { gradeRangeSection }
                section(L("ORIENTACIÓN")) { orientationChips }
            }
            section(L("ESTILO")) {
                chipRow([String?.none] + vm.styles.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.style },
                        label: { $0.map(styleLabel) ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.style = $0 }
            }
            section(L("TIPO DE ROCA")) {
                chipRow([String?.none] + vm.rocks.map { Optional($0) }, id: { $0 ?? "all" },
                        isSel: { $0 == vm.rock },
                        label: { $0.map(rockLabel) ?? NSLocalizedString("schools_filter_all", comment: "") }) { vm.rock = $0 }
            }
            if vm.exploreTab == .schools {
                section(L("MOSTRAR")) {
                    chipRow(SchoolListViewModel.ShowMode.allCases, id: { $0.rawValue },
                            isSel: { $0 == vm.showMode },
                            label: { $0.label }) { vm.showMode = $0 }
                }
            }
            section(L("ORDENAR POR")) {
                if vm.exploreTab == .blocks {
                    chipRow(SchoolListViewModel.ExploreSort.allCases, id: { $0.rawValue },
                            isSel: { $0 == vm.exploreSort },
                            label: { $0.label }) { vm.exploreSort = $0 }
                } else {
                    chipRow(SchoolListViewModel.SortMode.allCases, id: { $0.rawValue },
                            isSel: { $0 == vm.sortBy },
                            label: { $0.label }) { vm.sortBy = $0 }
                }
            }
        }
        .padding(.vertical, 8)
    }

    /// Segmented control Escuelas / Bloques — cambia la MISMA lista de abajo
    /// entre escuelas y vías, sin pantalla nueva.
    private var tabSwitcher: some View {
        HStack(spacing: 2) {
            tabButton(L("Escuelas"), active: vm.exploreTab == .schools) { vm.exploreTab = .schools }
            tabButton(L("Vías/Bloques"), active: vm.exploreTab == .blocks) { vm.exploreTab = .blocks; vm.dispatchExplore() }
        }
        .padding(3)
        .background(Cumbre.rule.opacity(0.18), in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
        .padding(.horizontal, 12)
    }

    private func tabButton(_ t: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(Cumbre.mono(12, .bold))
                .foregroundStyle(active ? .white : Cumbre.ink2)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(active ? Cumbre.terra : Color.clear, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius - 2))
        }
        .buttonStyle(.plain)
    }

    /// §8.2b: escuelas dentro del radio elegido. Rejilla compacta (varias por
    /// fila, sin el score del tiempo) — Álvaro, 2026-10-01: "que sea mucho más
    /// resumido... que no se note tanto que se puede hacer scroll". Al fluir
    /// con la página (sin su propio ScrollView) no hay altura que recortar.
    private var schoolPicker: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 6)], spacing: 6) {
            ForEach(vm.schoolsInRadius, id: \.id) { s in
                let checked = vm.selectedSchoolIds.contains(s.id)
                let wet = vm.scores[s.id]?.dryRock == false
                Button {
                    if checked { vm.selectedSchoolIds.remove(s.id) } else { vm.selectedSchoolIds.insert(s.id) }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: checked ? "checkmark.square.fill" : "square")
                            .font(.system(size: 12))
                            .foregroundStyle(checked ? .white : Cumbre.ink3)
                        Text(s.name).font(.system(size: 12, weight: .medium)).lineLimit(1)
                            .foregroundStyle(checked ? .white : Cumbre.ink)
                        if wet {
                            Circle().fill(checked ? .white : Cumbre.bad).frame(width: 5, height: 5)
                        }
                    }
                    .padding(.horizontal, 8).padding(.vertical, 7)
                    .frame(maxWidth: .infinity)
                    .background(checked ? Cumbre.terra : Cumbre.paper,
                                in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                    .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                        .stroke(checked ? Color.clear : Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
    }

    /// Dos campos MÍN/MÁX, cada uno con su desplegable de grados — Álvaro,
    /// 2026-10-01 (2ª vuelta): en vez de dos filas de chips, "uno mínimo...
    /// abres y se te abre un desplegable... y al lado máximo". El último
    /// grado usado se recuerda entre sesiones (UserDefaults, vía el VM).
    private var gradeRangeSection: some View {
        HStack(spacing: 10) {
            gradeDropdown(L("MÍN"), selected: vm.gradeMin) { vm.gradeMin = $0 }
            gradeDropdown(L("MÁX"), selected: vm.gradeMax) { vm.gradeMax = $0 }
        }
        .padding(.horizontal, 12)
    }

    private func gradeDropdown(_ label: String, selected: String?, onPick: @escaping (String?) -> Void) -> some View {
        Menu {
            Button(L("Sin filtro")) { onPick(nil) }
            ForEach(EXPLORE_GRADE_LADDER, id: \.self) { g in
                Button(g) { onPick(g) }
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                HStack {
                    Text(selected ?? L("—")).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    private var orientationChips: some View {
        chipRow(["N", "NE", "E", "SE", "S", "SO", "O", "NO"], id: { $0 },
                isSel: { vm.orientations.contains($0) },
                label: { $0 }) { o in
            if vm.orientations.contains(o) { vm.orientations.remove(o) } else { vm.orientations.insert(o) }
        }
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
            .background(active ? Cumbre.terra : Cumbre.paper,
                        in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
    }
}

/// Las dos formas de aportar al catálogo, cada una con su porqué.
/// Espejo de `AportarSheet` en Android.
struct AportarSheet: View {
    var onPiedra: () -> Void
    var onEscuela: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("APORTAR AL CATÁLOGO")
                .font(Cumbre.mono(11, .bold)).tracking(1.2)
                .foregroundStyle(Cumbre.terra)
            opcion(L("Una piedra, desde una foto"),
                   L("La foto dice en qué escuela se hizo"), onPiedra)
            opcion(L("Una escuela nueva"),
                   L("Si el sitio no está en el catálogo"), onEscuela)
            Spacer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cumbre.bg.ignoresSafeArea())
    }

    private func opcion(_ titulo: String, _ detalle: String,
                        _ accion: @escaping () -> Void) -> some View {
        Button(action: accion) {
            VStack(alignment: .leading, spacing: 2) {
                Text(titulo).font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                Text(detalle).font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .overlay(Rectangle().stroke(Cumbre.ink, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
