import SwiftUI
import Shared

/// Portada del panel de admin: cuadraditos llamativos con los números que importan, agrupados por para qué sirven,
/// todo en una sola pantalla con scroll vertical (Álvaro, 2026-10-18: «cuadraditos en los que poder ver las cosas, la
/// gente que ha entrado hoy, 30 días, 3 meses… y que yo haga scroll hacia abajo»). Tocar un cuadradito abre su sección.
struct AdminHome: View {
    @ObservedObject var vm: AdminViewModel
    let openTab: (AdminTab) -> Void
    @State private var health: AdminHealth?
    /// Pantallas propias de los cuadraditos (empujadas en la pila) y hojas de «quién entró» / propuestas pasadas.
    @State private var dest: Dest?
    @State private var showDaily = false
    @State private var historyStatus: String?

    enum Dest: Hashable { case users(AdminUsersMode), notes, maintenance }

    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header("REVISAR")
                grid {
                    let pending = vm.submissions.count + vm.contributions.count
                    tile(value: "\(pending)", label: "PROPUESTAS", note: "por aprobar", hot: pending > 0) { openTab(.propuestas) }
                    tile(symbol: "flag", label: "DENUNCIAS", note: "contenido y personas") { openTab(.denuncias) }
                    tile(symbol: "text.bubble", label: "SUGERENCIAS", note: "lo que cuentan los usuarios") { openTab(.sugerencias) }
                }

                header("USUARIOS")
                grid {
                    if let s = vm.stats {
                        tile(value: "\(s.dailyActiveUsers)", label: "HOY", note: "usuarios distintos", accent: true) { dest = .users(.active(days: 1)) }
                        tile(value: "\(s.weeklyActiveUsers)", label: "7 DÍAS", note: "usuarios distintos", accent: true) { dest = .users(.active(days: 7)) }
                        tile(value: "\(s.monthlyActiveUsers)", label: "30 DÍAS", note: "usuarios distintos", accent: true) { dest = .users(.active(days: 30)) }
                        tile(value: "\(s.quarterlyActiveUsers)", label: "3 MESES", note: "usuarios distintos", accent: true) { dest = .users(.active(days: 90)) }
                        tile(value: "\(s.dailyOpensTotal)", label: "ENTRADAS POR DÍA", note: "\(s.dailyOpensTotal) aperturas hoy · quién entró") { showDaily = true }
                        tile(value: "\(s.totalUsers)", label: "TODOS LOS USUARIOS", note: "del primero al último · \(s.totalAdmins) admins") { dest = .users(.all) }
                    } else {
                        loadingTile
                    }
                }

                header("CONTENIDO")
                grid {
                    if let s = vm.stats {
                        tile(value: "\(s.totalSchools)", label: "ESCUELAS", note: "editar, restricciones") { openTab(.gestionar) }
                        tile(value: "\(s.totalNotes)", label: "NOTAS", note: "de los usuarios") { dest = .notes }
                        tile(value: "\(s.submissionsApproved)", label: "APROBADAS", note: "propuestas pasadas") { historyStatus = "APPROVED" }
                        tile(value: "\(s.submissionsRejected)", label: "RECHAZADAS", note: "propuestas pasadas") { historyStatus = "REJECTED" }
                    } else {
                        tile(symbol: "map", label: "ESCUELAS", note: "editar, restricciones") { openTab(.gestionar) }
                    }
                    tile(symbol: "clock.arrow.circlepath", label: "HISTORIAL", note: "cambios, con restaurar") { openTab(.historial) }
                    tile(symbol: "person.2", label: "APORTACIONES", note: "y confianza") { openTab(.aportaciones) }
                }

                header("SERVIDOR")
                grid {
                    if let h = health {
                        tile(value: "\(h.errors24h)", label: "ERRORES 24 H", note: h.errors24h == 0 ? "todo bien" : "toca para ver cuáles",
                             hot: h.errors24h > 0) { openTab(.salud) }
                        tile(value: uptime(Int(h.uptimeMinutes)), label: "API ARRIBA", note: "desde el último despliegue")
                        if h.assistantDailyLimit > 0 {
                            tile(value: "\(h.assistantUsedToday)/\(h.assistantDailyLimit)", label: "ASISTENTE HOY", note: "preguntas gastadas",
                                 hot: Double(h.assistantUsedToday) / Double(h.assistantDailyLimit) >= 0.8)
                        }
                    } else {
                        tile(symbol: "heart.text.square", label: "SALUD", note: "errores, usuarios, asistente") { openTab(.salud) }
                    }
                    tile(symbol: "wrench.and.screwdriver", label: "MANTENIMIENTO", note: "tareas puntuales") { dest = .maintenance }
                    tile(symbol: "list.bullet.rectangle", label: "ACTIVIDAD", note: "lo que han hecho los admins") { openTab(.actividad) }
                }

                header("COMUNICAR")
                grid {
                    tile(symbol: "bell", label: "NOTIFICACIONES", note: "avisar a los usuarios") { openTab(.push) }
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 40)
        }
        .refreshable { await load() }
        .task { await load() }
        .navigationDestination(item: $dest) { d in
            switch d {
            case .users(let mode): AdminUsersScreen(mode: mode)
            case .notes: AdminNotesScreen()
            case .maintenance: AdminMaintenanceScreen()
            }
        }
        .sheet(isPresented: $showDaily) { DailyActivityView() }
        .sheet(isPresented: Binding(get: { historyStatus != nil }, set: { if !$0 { historyStatus = nil } })) {
            if let status = historyStatus { SubmissionsHistoryView(status: status) }
        }
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
                      accent: Bool = false, action: (() -> Void)? = nil) -> some View {
        Button { action?() } label: {
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
        .allowsHitTesting(action != nil)
    }
}
