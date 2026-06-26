import SwiftUI
import Shared

/// Botón "?" que abre la ayuda contextual de una pantalla. [topicKey] = clave del
/// HelpCatalog compartido ("schools", "detail", "profile", "chat", "weather"…).
struct HelpButton: View {
    let topicKey: String
    @State private var show = false
    var body: some View {
        Button { show = true } label: {
            Image(systemName: "questionmark.circle")
                .foregroundStyle(Cumbre.ink2)
        }
        .sheet(isPresented: $show) { HelpSheet(topicKey: topicKey) }
    }
}

/// Hoja de ayuda: título, intro y lista de "qué puedes hacer aquí". El contenido
/// viene del catálogo compartido en Kotlin → mismo texto que Android.
struct HelpSheet: View {
    let topicKey: String
    @Environment(\.dismiss) private var dismiss
    private var topic: HelpTopic? { AppDependencies.shared.container.helpTopic(key: topicKey) }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let t = topic {
                    VStack(alignment: .leading, spacing: 18) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("AYUDA").font(Cumbre.mono(11, .bold)).tracking(1.8)
                                .foregroundStyle(Cumbre.terra)
                            Text(t.title).font(Cumbre.serif(26, .bold)).foregroundStyle(Cumbre.ink)
                        }
                        // Intro en caja tintada.
                        Text(t.intro).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Cumbre.terra.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                        // Filas: icono en círculo + título + descripción.
                        ForEach(Array(t.items.enumerated()), id: \.offset) { _, item in
                            HStack(alignment: .top, spacing: 12) {
                                ZStack {
                                    Circle().fill(Cumbre.terra.opacity(0.12)).frame(width: 40, height: 40)
                                    Image(systemName: Self.icon(item.icon))
                                        .font(.system(size: 18)).foregroundStyle(Cumbre.terra)
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Cumbre.ink)
                                    Text(item.body).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                        // Botón "Volver a ver las pistas"
                        Button {
                            FirstTimeHint.resetAll()
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle").font(.system(size: 16))
                                Text("Volver a ver todas las pistas").font(.system(size: 15))
                            }
                            .foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity)
                            .padding(12)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }

    /// Mapea el nombre semántico del catálogo a un SF Symbol.
    static func icon(_ name: String) -> String {
        switch name {
        case "filter": return "line.3.horizontal.decrease.circle"
        case "calendar": return "calendar"
        case "star": return "star"
        case "compare": return "square.on.square"
        case "map": return "map"
        case "plus": return "plus.circle"
        case "clock": return "clock"
        case "download": return "arrow.down.circle"
        case "tick": return "checkmark.circle"
        case "edit": return "pencil"
        case "wall": return "skew"
        case "book": return "book"
        case "person": return "person.2"
        case "bell": return "bell"
        case "chat": return "bubble.left"
        case "reply": return "arrowshape.turn.up.left"
        case "wifioff": return "wifi.slash"
        default: return "info.circle"
        }
    }
}
