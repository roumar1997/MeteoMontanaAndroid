import SwiftUI
import Shared

/// SALUD (admin): cómo está el servidor ahora. Los números viven en memoria del servidor y se reinician con cada
/// despliegue. Los cierres de las apps están en Crashlytics (botón). Las alertas por Telegram llegarán con el bot.
struct AdminHealthTab: View {
    @State private var health: AdminHealth?
    @State private var failed = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let h = health {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        tile(uptime(Int(h.uptimeMinutes)), L("API ARRIBA"), Cumbre.ok)
                        tile("\(h.errors24h)", L("ERRORES 24 H"), h.errors24h == 0 ? Cumbre.ok : Cumbre.bad)
                        tile("\(h.activeUsersToday)", L("USUARIOS HOY"), Cumbre.ink)
                        tile(h.pendingContributions < 0 ? "—" : "\(h.pendingContributions)", L("PROPUESTAS PENDIENTES"),
                             h.pendingContributions > 20 ? Cumbre.warn : Cumbre.ink)
                        tile(h.assistantDailyLimit < 0 ? "—" : "\(h.assistantUsedToday)/\(h.assistantDailyLimit)",
                             L("ASISTENTE HOY"), assistantColor(h))
                    }
                    Text(L("ERRORES POR ENDPOINT (24 H)")).eyebrow()
                    if h.topErrors.isEmpty {
                        Text(L("Ningún error en las últimas 24 horas.")).font(.system(size: 14)).foregroundStyle(Cumbre.ok)
                    }
                    ForEach(h.topErrors, id: \.endpoint) { e in
                        HStack {
                            Text(e.endpoint).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink).lineLimit(2)
                            Spacer()
                            Text("\(e.count)").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.bad)
                        }
                        Divider().overlay(Cumbre.rule)
                    }
                    Text(L("Se reinicia con cada despliegue. Cierres de las apps: Crashlytics."))
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                } else if failed {
                    Text(L("No se pudo leer la salud del servidor. Si la API está caída, este es el aviso."))
                        .font(.system(size: 14)).foregroundStyle(Cumbre.bad)
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 30)
                }
                Button {
                    if let url = URL(string: "https://console.firebase.google.com/project/climbingteams/crashlytics") { openURL(url) }
                } label: {
                    Text(L("ABRIR CRASHLYTICS")).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                }.buttonStyle(.plain)
            }
            .padding(16)
        }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        if let h = try? await AppDependencies.shared.container.getAdminHealth.invoke() {
            health = h; failed = false
        } else {
            failed = true
        }
    }

    private func assistantColor(_ h: AdminHealth) -> Color {
        guard h.assistantDailyLimit > 0 else { return Cumbre.ink }
        return Double(h.assistantUsedToday) / Double(h.assistantDailyLimit) >= 0.8 ? Cumbre.warn : Cumbre.ink
    }

    private func uptime(_ minutes: Int) -> String {
        minutes >= 1440 ? "\(minutes / 1440) d" : minutes >= 60 ? "\(minutes / 60) h" : "\(minutes) min"
    }

    private func tile(_ value: String, _ label: String, _ color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(Cumbre.mono(8, .bold)).tracking(1).foregroundStyle(Cumbre.ink3).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(10)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
    }
}
