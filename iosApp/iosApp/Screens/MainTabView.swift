import SwiftUI

// Barra de tabs inferior — réplica de MainScreen.kt de Android:
// Tiempo · Escuelas · Radar. Escuelas es el tab inicial (como en Android).
struct MainTabView: View {
    @State private var tab = 1 // 0=Tiempo, 1=Escuelas, 2=Radar

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
                .tabItem { Label("Tiempo", systemImage: "cloud") }
                .tag(0)
            SchoolListView()
                .tabItem { Label("Escuelas", systemImage: "list.bullet") }
                .tag(1)
            RadarView()
                .tabItem { Label("Radar", systemImage: "dot.radiowaves.left.and.right") }
                .tag(2)
        }
        .tint(Cumbre.terra)
    }
}
