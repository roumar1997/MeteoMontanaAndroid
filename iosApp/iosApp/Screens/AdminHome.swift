import SwiftUI
import Shared

/// Portada del panel de admin: cuadraditos llamativos con los números que importan, agrupados por para qué sirven,
/// todo en una sola pantalla con scroll vertical (Álvaro, 2026-10-18: «cuadraditos en los que poder ver las cosas, la
/// gente que ha entrado hoy, 30 días, 3 meses… y que yo haga scroll hacia abajo»). Tocar un cuadradito abre su sección.
struct AdminHome: View {
    @ObservedObject var vm: AdminViewModel
    let onOpen: (AdminTab) -> Void
    @State private var health: AdminHealth?

    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header("REVISAR")
                grid {
                    let pending = vm.submissions.count + vm.contributions.count
                    tile(value: "\(pending)", label: "PROPUESTAS", note: "por aprobar", hot: pending > 0, tab: .propuestas)
                    tile(symbol: "flag", label: "DENUNCIAS", note: "contenido y personas", tab: .denuncias)
                    tile(symbol: "text.bubble", label: "SUGERENCIAS", note: "lo que cuentan los usuarios", tab: .sugerencias)
                }

                header("USUARIOS")
                grid {
                    if let s = vm.stats {
                        tile(value: "\(s.dailyActiveUsers)", label: "HOY", note: "usuarios distintos", tab: .stats, accent: true)
                        tile(value: "\(s.weeklyActiveUsers)", label: "7 DÍAS", note: "usuarios distintos", tab: .stats, accent: true)
                        tile(value: "\(s.monthlyActiveUsers)", label: "30 DÍAS", note: "usuarios distintos", tab: .stats, accent: true)
                        tile(value: "\(s.quarterlyActiveUsers)", label: "3 MESES", note: "usuarios distintos", tab: .stats, accent: true)
                        tile(value: "\(s.dailyOpensTotal)", label: "APERTURAS HOY", note: "de la app, en total", tab: .stats)
                        tile(value: "\(s.totalUsers)", label: "REGISTRADOS", note: "\(s.totalAdmins) admins", tab: .stats)
                    } else {
                        loadingTile
                    }
                }

                header("CONTENIDO")
                grid {
                    if let s = vm.stats {
                        tile(value: "\(s.totalSchools)", label: "ESCUELAS", note: "editar, restricciones", tab: .gestionar)
                        tile(value: "\(s.totalNotes)", label: "NOTAS", note: "de los usuarios", tab: .stats)
                        tile(value: "\(s.submissionsApproved)", label: "APROBADAS", note: "propuestas", tab: .stats)
                        tile(value: "\(s.submissionsRejected)", label: "RECHAZADAS", note: "propuestas", tab: .stats)
                    } else {
                        tile(symbol: "map", label: "ESCUELAS", note: "editar, restricciones", tab: .gestionar)
                    }
                    tile(symbol: "clock.arrow.circlepath", label: "HISTORIAL", note: "cambios, con restaurar", tab: .historial)
                    tile(symbol: "person.2", label: "APORTACIONES", note: "y confianza", tab: .aportaciones)
                }

                header("SERVIDOR")
                grid {
                    if let h = health {
                        tile(value: "\(h.errors24h)", label: "ERRORES 24 H", note: h.errors24h == 0 ? "todo bien" : "toca para ver cuáles",
                             hot: h.errors24h > 0, tab: .salud)
                        tile(value: uptime(Int(h.uptimeMinutes)), label: "API ARRIBA", note: "desde el último despliegue", tab: .salud)
                        if h.assistantDailyLimit > 0 {
                            tile(value: "\(h.assistantUsedToday)/\(h.assistantDailyLimit)", label: "ASISTENTE HOY", note: "preguntas gastadas",
                                 hot: Double(h.assistantUsedToday) / Double(h.assistantDailyLimit) >= 0.8, tab: .salud)
                        }
                    } else {
                        tile(symbol: "heart.text.square", label: "SALUD", note: "errores, usuarios, asistente", tab: .salud)
                    }
                    tile(symbol: "chart.bar", label: "ESTADÍSTICAS", note: "listas y mantenimiento", tab: .stats)
                    tile(symbol: "list.bullet.rectangle", label: "ACTIVIDAD", note: "lo que han hecho los admins", tab: .actividad)
                }

                header("COMUNICAR")
                grid {
                    tile(symbol: "bell", label: "NOTIFICACIONES", note: "avisar a los usuarios", tab: .push)
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 40)
        }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        await vm.loadStats()
        health = try? await AppDependencies.shared.container.getAdminHealth.invoke()
    }

    private func uptime(_ minutes: Int) -> String {
        minutes >= 1440 ? "\(minutes / 1440) d" : minutes >= 60 ? "\(minutes / 60) h" : "\(minutes) min"
    }

    // MARK: - Piezas

    private func header(_ title: String) -> some View {
        Text(title).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.terra).padding(.top, 22).padding(.bottom, 8)
    }

    private func grid<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        LazyVGrid(columns: columns, spacing: 10) { content() }
    }

    private var loadingTile: some View {
        ProgressView().frame(maxWidth: .infinity, minHeight: 92)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
    }

    /// Un cuadradito: número grande (o un icono), nombre en mayúsculas y una línea de pista. `hot` = pide atención
    /// (terracota); `accent` = actividad de usuarios (fondo cálido).
    private func tile(value: String? = nil, symbol: String? = nil, label: String, note: String, hot: Bool = false,
                      tab: AdminTab, accent: Bool = false) -> some View {
        Button { onOpen(tab) } label: {
            VStack(alignment: .leading, spacing: 4) {
                if let value {
                    Text(value).font(Cumbre.serif(30, .bold)).foregroundStyle(hot ? .white : (accent ? Cumbre.terra : Cumbre.ink))
                        .lineLimit(1).minimumScaleFactor(0.6)
                } else if let symbol {
                    Image(systemName: symbol).font(.system(size: 26, weight: .regular)).foregroundStyle(Cumbre.terra).frame(height: 36, alignment: .leading)
                }
                Text(label).font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(hot ? .white : Cumbre.ink)
                Text(note).font(.system(size: 11)).foregroundStyle(hot ? Color.white.opacity(0.85) : Cumbre.ink3).lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .leading)
            .padding(12)
            .background(hot ? Cumbre.terraFill : (accent ? Cumbre.terraBg : Cumbre.paper), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(hot ? Cumbre.terraFill : (accent ? Cumbre.terra.opacity(0.5) : Cumbre.rule), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
