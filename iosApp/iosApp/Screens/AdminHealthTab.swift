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
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(e.endpoint).font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                Spacer()
                                Text("\(e.count)").font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.bad)
                            }
                            // Qué falló la última vez (clase de la excepción y mensaje) y cuándo.
                            if let why = e.lastSummary, !why.isEmpty {
                                Text(why).font(.system(size: 12)).foregroundStyle(Cumbre.bad).lineLimit(4)
                            }
                            if let at = e.last { Text(L("Último: %@", clock(at))).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3) }
                        }
                        Divider().overlay(Cumbre.rule)
                    }

                    if !h.recentFailures.isEmpty {
                        Text(L("ÚLTIMOS FALLOS")).eyebrow().padding(.top, 6)
                        ForEach(Array(h.recentFailures.enumerated()), id: \.offset) { _, f in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text("\(f.status) · \(f.endpoint)").font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                    Spacer()
                                    Text(clock(f.at)).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
                                }
                                Text(f.summary).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(4)
                            }
                            Divider().overlay(Cumbre.rule)
                        }
                    }

                    if !h.slowest.isEmpty {
                        Text(L("ENDPOINTS MÁS LENTOS")).eyebrow().padding(.top, 6)
                        ForEach(h.slowest, id: \.endpoint) { l in
                            HStack {
                                Text(l.endpoint).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink).lineLimit(2)
                                Spacer()
                                Text(L("%@ ms de media · máx %@ · %@ llamadas", l.avgMs, l.maxMs, l.calls))
                                    .font(Cumbre.mono(10)).foregroundStyle(l.avgMs > 1500 ? Cumbre.warn : Cumbre.ink2)
                            }
                            Divider().overlay(Cumbre.rule)
                        }
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

    /// «d MMM HH:mm» de un instante ISO del servidor (UTC), en la hora del móvil.
    private func clock(_ iso: String) -> String {
        let p = ISO8601DateFormatter()
        p.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let d = p.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return iso }
        let f = DateFormatter()
        f.dateFormat = "d MMM HH:mm"
        f.locale = LanguageManager.shared.locale
        return f.string(from: d)
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
