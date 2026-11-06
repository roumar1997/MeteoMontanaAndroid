import SwiftUI
import Shared

// La burbuja flotante del asistente y las piezas pequeñas del chat (avatar, puntos de escribir, pulso).

/// Burbuja flotante del asistente: un círculo con la mascota, siempre abajo a
/// la derecha, que abre el chat. La coloca MainTabView por encima de la barra.
struct AssistantBubble: View {
    /// La conversación es de toda la app (la crea MeteoMontanaApp): cerrar el chat, abrir una
    /// piedra y volver a abrir el círculo no la pierde.
    @EnvironmentObject private var assistant: AssistantViewModel
    @State private var open = false
    /// Dónde la dejaste: se puede arrastrar y se recuerda (Álvaro, 2026-10-19). Desplazamiento desde su esquina.
    @AppStorage("assistant_bubble_dx") private var savedX: Double = 0
    @AppStorage("assistant_bubble_dy") private var savedY: Double = 0
    @GestureState private var drag: CGSize = .zero
    /// Quedada que el asistente dejó preparada: se abre "Nueva quedada" ya rellena al cerrarse el chat.
    @State private var meetupPrefill: AssistantViewModel.MeetupRequest?
    @State private var showMeetup = false
    /// El servidor puede apagar el asistente para todos (interruptor «assistant»).
    @ObservedObject private var featureFlags = FeatureFlagsModel.shared

    /// La burbuja solo existe para quien tiene el asistente (admin, abierto a todos o activado a mano por un admin)
    /// y mientras el servidor no lo tenga en pausa.
    var body: some View {
        Group {
            if assistant.hasAccess && featureFlags.isEnabled(FeatureKey.shared.ASSISTANT) { bubble }
        }
    }

    private var bubble: some View {
        Image("assistant_bubble")
            .resizable().scaledToFit()
            .frame(width: 58, height: 58)
            .shadow(color: .black.opacity(0.28), radius: 3, x: 0, y: 2)
            .contentShape(Circle())
            .offset(x: savedX + drag.width, y: savedY + drag.height)
            // Arrastrar = moverla a otro sitio (sin abrir el chat); se queda dentro de la pantalla.
            .gesture(
                DragGesture(minimumDistance: 8)
                    .updating($drag) { value, state, _ in state = value.translation }
                    .onEnded { value in
                        let bounds = UIScreen.main.bounds
                        savedX = min(0, max(-(bounds.width - 90), savedX + value.translation.width))
                        savedY = min(0, max(-(bounds.height - 260), savedY + value.translation.height))
                    }
            )
            // Toque = abrir el chat. Mantener pulsado = abrirlo YA escuchando: hablas y miras lo que te ofrece.
            .onTapGesture { open = true }
            .onLongPressGesture(minimumDuration: 0.45) {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                assistant.listenOnOpen = true
                open = true
            }
            .accessibilityElement()
            .accessibilityLabel(L("Asistente de Cumbre"))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction(named: Text(L("Hablar"))) {
                assistant.listenOnOpen = true
                open = true
            }
        // Al tocar un resultado el chat se cierra y, cuando ya se ha cerrado del todo, se abre
        // el destino (una hoja no puede presentarse mientras otra se está retirando).
        .onChange(of: assistant.openRequest) { _, request in
            if request != nil { open = false }
        }
        .onChange(of: assistant.meetupRequest) { _, request in
            if request != nil { open = false }
        }
        .background(
            EmptyView().sheet(isPresented: $showMeetup) {
                CreateMeetupView(onCreated: { _ in showMeetup = false }, prefill: meetupPrefill)
            }
        )
        .sheet(isPresented: $open, onDismiss: openPendingTarget) {
            AssistantChatView()
                .presentationDragIndicator(.visible)
        }
    }
}

extension AssistantBubble {
    private func openPendingTarget() {
        if let meetup = assistant.meetupRequest {
            assistant.meetupRequest = nil
            meetupPrefill = meetup
            showMeetup = true
            return
        }
        guard let request = assistant.openRequest else { return }
        assistant.openRequest = nil
        Task { await ShareLinkRouter.shared.openSchoolInTabs(id: request.schoolId, viaId: request.viaId) }
    }
}

/// Cumbri en círculo: cabecera del chat y al lado de cada respuesta.
struct AssistantAvatar: View {
    let size: CGFloat
    var body: some View {
        Image("assistant_bubble")
            .resizable().scaledToFit()
            .frame(width: size, height: size)
            .clipShape(Circle())
            .accessibilityHidden(true)
    }
}

/// Tres puntos que laten mientras el asistente piensa. Con "Reducir movimiento" se quedan quietos.
struct TypingDots: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var animating = false

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(Cumbre.ink3)
                    .frame(width: 7, height: 7)
                    .opacity(reduceMotion ? 0.7 : (animating ? 1 : 0.25))
                    .animation(reduceMotion ? nil
                               : .easeInOut(duration: 0.55).repeatForever().delay(Double(i) * 0.18),
                               value: animating)
            }
        }
        .onAppear { animating = true }
        .accessibilityElement()
        .accessibilityLabel(L("Pensando…"))
    }
}

/// Anillo que se expande alrededor del micrófono mientras escucha. Con "Reducir movimiento", fijo.
struct PulseRing: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var expanded = false

    var body: some View {
        Circle()
            .stroke(Cumbre.terra, lineWidth: 2)
            .frame(width: 42, height: 42)
            .scaleEffect(reduceMotion ? 1.15 : (expanded ? 1.45 : 1))
            .opacity(reduceMotion ? 0.5 : (expanded ? 0 : 0.7))
            .animation(reduceMotion ? nil : .easeOut(duration: 1.1).repeatForever(autoreverses: false), value: expanded)
            .onAppear { expanded = true }
            .accessibilityHidden(true)
    }
}
