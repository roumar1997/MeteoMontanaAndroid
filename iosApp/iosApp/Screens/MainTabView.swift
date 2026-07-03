import SwiftUI

// Barra de tabs inferior — réplica de MainScreen.kt de Android:
// Tiempo · Escuelas · Radar · Quedadas (radar propio AEMET desde 2026-07-03).
struct MainTabView: View {
    @State private var tab = 1 // 0=Tiempo, 1=Escuelas, 2=Radar, 3=Quedadas

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
            WeatherView()
                .tabItem { Label(NSLocalizedString("tab_weather", comment: ""), systemImage: "cloud") }
                .tag(0)
            SchoolListView()
                .tabItem { Label(NSLocalizedString("tab_schools", comment: ""), systemImage: "list.bullet") }
                .tag(1)
            RadarView()
                .tabItem { Label("Radar", systemImage: "dot.radiowaves.left.and.right") }
                .tag(2)
            MeetupsView()
                .tabItem { Label(NSLocalizedString("tab_meetups", comment: ""), systemImage: "person.3") }
                .tag(3)
        }
        .tint(Cumbre.terra)
    }
}
