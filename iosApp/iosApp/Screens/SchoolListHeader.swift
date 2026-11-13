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

/// Las cuatro baldosas de la cabecera de Escuelas: Gente · Chats · Noche · Avisos, iguales y repartidas por todo el
/// ancho. Espejo de `SchoolsHeader` en Android. Si no caben (iPhone estrecho o letra grande) la fila se desliza.
struct TopIconsRow: View {
    var unreadCount: Int = 0
    var chatUnread: Int = 0
    var onNotificationsClosed: () -> Void = {}
    @State private var showNotifications = false
    @State private var showSearch = false
    @State private var showChats = false
    @ObservedObject private var theme = ThemeManager.shared

    var body: some View {
        ActionTileRow(count: 4) { w in
            ActionTile(label: L("bar_people"), symbol: "person.crop.circle.badge.magnifyingglass",
                       a11y: L("search_users_title")) { showSearch = true }
                .frame(width: w)
            ActionTile(label: L("bar_chats"), symbol: "bubble.left.and.bubble.right", a11y: L("chat_title"),
                       badge: chatUnread) { showChats = true }
                .frame(width: w)
            // Encendido (terracota) = modo noche. El icono es el del modo en que estás.
            ActionTile(label: L("bar_night"), symbol: theme.mode == .dark ? "sun.max" : "moon",
                       a11y: L("bar_night"), active: theme.mode == .dark) { theme.cycle() }
                .frame(width: w)
            ActionTile(label: L("bar_alerts"), symbol: "bell", a11y: L("notifications_title"),
                       badge: unreadCount) { showNotifications = true }
                .frame(width: w)
        }
        .sheet(isPresented: $showNotifications, onDismiss: onNotificationsClosed) { NotificationsView() }
        .sheet(isPresented: $showSearch) { SearchUsersView() }
        .sheet(isPresented: $showChats) { NavigationStack { ChatListView() } }
    }
}

/// Cabecera de Escuelas: arriba «? AYUDA» a la izquierda y "Aportar" (píldora terracota con cámara) a la derecha;
/// debajo, cuatro baldosas iguales (Gente · Chats · Noche · Avisos). Sin el título "Escuelas": ya lo dice la pestaña.
struct HeaderEscuelas: View {
    var unreadCount: Int = 0
    var chatUnread: Int = 0
    var onNotificationsClosed: () -> Void = {}
    /// "Enviar piedra": elegir una foto y proponerla en la escuela donde se hizo.
    var onSubmitBlockPhoto: () -> Void = {}
    @State private var showSubmit = false
    @State private var aportando = false
    @State private var showHelp = false

    /// Ejecuta [accion] cuando la hoja ya se ha cerrado del todo.
    ///
    /// Encadenar dos presentaciones en SwiftUI (cerrar una hoja y abrir otra en
    /// el mismo instante) hace que la segunda se pierda sin decir nada. Es lo
    /// que dejaba "Una piedra, desde una foto" sin hacer nada.
    private func trasCerrar(_ accion: @escaping () -> Void) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: accion)
    }
    var body: some View {
        VStack(spacing: 8) {
        HStack(alignment: .center, spacing: 8) {
            GuidePill(topicKey: "schools") {
                if !GuideStore.shared.start("schools") { showHelp = true }
            }
            Spacer(minLength: 8)
            // UN solo botón: corto, entra en cualquier pantalla. Las dos formas
            // de aportar viven en la hoja, donde cada una cabe con su
            // explicación — "enviar piedra" no se entiende a secas.
            Button { aportando = true } label: {
                HStack(spacing: 5) {
                    Image(systemName: "camera.fill").font(.system(size: 14, weight: .bold))
                    Text(L("bar_contribute")).font(Cumbre.mono(12, .bold)).tracking(0.5)
                }
                .lineLimit(1).fixedSize()
                .foregroundStyle(.white)
                .padding(.horizontal, 12).padding(.vertical, 11)
                .background(Cumbre.terraFill, in: Capsule())
            }
            .buttonStyle(.plain)
        }
        TopIconsRow(unreadCount: unreadCount, chatUnread: chatUnread,
                    onNotificationsClosed: onNotificationsClosed)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .sheet(isPresented: $showSubmit) { SubmitSchoolView() }
        .sheet(isPresented: $showHelp) { HelpSheet(topicKey: "schools") }
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
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .dismissKeyboardOnSubmit()
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
