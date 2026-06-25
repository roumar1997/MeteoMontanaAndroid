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
                    VStack(alignment: .leading, spacing: 16) {
                        Text("AYUDA").font(Cumbre.mono(11, .bold)).tracking(1.8)
                            .foregroundStyle(Cumbre.terra)
                        Text(t.title).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                        Text(t.intro).font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                        ForEach(Array(t.items.enumerated()), id: \.offset) { _, item in
                            HStack(alignment: .top, spacing: 8) {
                                Text("•").foregroundStyle(Cumbre.terra)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Cumbre.ink)
                                    Text(item.body).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                                }
                            }
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
}
