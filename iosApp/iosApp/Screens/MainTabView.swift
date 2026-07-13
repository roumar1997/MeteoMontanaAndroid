import SwiftUI

// Barra de tabs inferior — réplica de MainScreen.kt / NavGraph.kt de Android:
// Radar (radar + conmutador TIEMPO ⇄ RADAR) · Escuelas · Quedadas ·
// Comunidad (feed social + ranking) · Perfil. La pestaña Radar suelta
// desapareció: el radar ES la vista por defecto de la primera pestaña.
struct MainTabView: View {
    @State private var tab = 1 // 0=Radar/Tiempo, 1=Escuelas, 2=Quedadas, 3=Comunidad, 4=Perfil

    init() {
        // Tab bar con estilo Cumbre: fondo papel, acento terracota.
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Cumbre.bg)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        TabView(selection: $tab) {
            WeatherRadarTab()
                .tabItem { Label("Radar", systemImage: "dot.radiowaves.left.and.right") }
                .tag(0)
            SchoolListView()
                .tabItem { Label(NSLocalizedString("tab_schools", comment: ""), systemImage: "list.bullet") }
                .tag(1)
            MeetupsView()
                .tabItem { Label(NSLocalizedString("tab_meetups", comment: ""), systemImage: "person.3") }
                .tag(2)
            FeedView()
                .tabItem { Label(NSLocalizedString("tab_community", comment: ""), systemImage: "person.2") }
                .tag(3)
            AccountView(showClose: false)
                .tabItem { Label("Perfil", systemImage: "person.crop.circle") }
                .tag(4)
        }
        .tint(Cumbre.terra)
    }
}

/// Primera pestaña: Radar (por defecto) + Tiempo, con conmutador
/// TIEMPO ⇄ RADAR. Las dos capas quedan compuestas (keep-alive, mismo truco
/// zIndex/alpha que Android) → alternar no recrea el mapa del radar.
struct WeatherRadarTab: View {
    /// true = Radar visible — ES la vista por defecto (la pestaña se llama Radar).
    @State private var showRadar = true

    var body: some View {
        ZStack {
            // ── Capa TIEMPO ──
            VStack(spacing: 0) {
                WeatherRadarToggle(showRadar: false) { showRadar = $0 }
                    .padding(.top, 8)
                WeatherView()
            }
            .opacity(showRadar ? 0 : 1)
            .zIndex(showRadar ? 0 : 1)
            .allowsHitTesting(!showRadar)

            // ── Capa RADAR (mapa a pantalla completa) ──
            ZStack(alignment: .top) {
                // El rótulo "Lluvia en directo" baja 48pt para no quedar
                // debajo del conmutador flotante (paridad Android).
                RadarView(titleTopInset: 48)
                WeatherRadarToggle(showRadar: true) { showRadar = $0 }
                    .padding(.top, 8)
            }
            .opacity(showRadar ? 1 : 0)
            .zIndex(showRadar ? 1 : 0)
            .allowsHitTesting(showRadar)
        }
    }
}

/// Conmutador TIEMPO ⇄ RADAR (segmented estilo Cumbre: cápsula con borde
/// Rule, segmento activo Terra) — espejo de WeatherRadarToggle de MainScreen.kt.
struct WeatherRadarToggle: View {
    let showRadar: Bool
    let onSelect: (_ radar: Bool) -> Void

    var body: some View {
        HStack(spacing: 0) {
            segment("TIEMPO", active: !showRadar) { onSelect(false) }
            segment("RADAR", active: showRadar) { onSelect(true) }
        }
        .background(Cumbre.bg.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
    }

    private func segment(_ label: String, active: Bool,
                         action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(active ? .white : Cumbre.ink3)
                .padding(.horizontal, 18).padding(.vertical, 9)
                .background(active ? Cumbre.terra : Color.clear)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
