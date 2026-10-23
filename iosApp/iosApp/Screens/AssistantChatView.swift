import SwiftUI
import Shared

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

    /// La burbuja solo existe para quien tiene el asistente (admin, abierto a todos o activado a mano por un admin).
    var body: some View {
        Group {
            if assistant.hasAccess { bubble }
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
        .sheet(isPresented: $open, onDismiss: openPendingTarget) {
            AssistantChatView()
                .presentationDragIndicator(.visible)
        }
    }
}

extension AssistantBubble {
    private func openPendingTarget() {
        guard let request = assistant.openRequest else { return }
        assistant.openRequest = nil
        Task { await ShareLinkRouter.shared.openSchoolInTabs(id: request.schoolId, viaId: request.viaId) }
    }
}

/// Chat con el asistente. Escribe o dicta; las respuestas son tarjetas con datos
/// reales (escuelas, sectores) y botones que abren la escuela.
struct AssistantChatView: View {
    @EnvironmentObject private var vm: AssistantViewModel
    @StateObject private var voice = AssistantVoiceInput()
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focused: Bool
    @State private var showVoiceDenied = false

    /// Icono de cada frase de ejemplo, en el mismo orden que `vm.suggestions` (cerca de mí, sábado, sombra).
    private static let suggestionIcons = ["location.fill", "sun.max.fill", "circle.lefthalf.filled"]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                messagesList
                inputBar
                voiceHint
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { header }
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 6) {
                        if !vm.messages.isEmpty {
                            Button { voice.stop(); vm.reset() } label: { toolbarIcon("square.and.pencil") }
                                .accessibilityLabel(L("Nueva conversación"))
                        }
                        Button { voice.stop(); dismiss() } label: { toolbarIcon("xmark") }
                            .accessibilityLabel(NSLocalizedString("common_close", comment: ""))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .onAppear {
            voice.onText = { vm.input = String($0.prefix(AssistantViewModel.maxText)) }
            // Al callar, la pregunta se envía sola.
            voice.onFinished = { spoken in Task { await vm.send(spoken) } }
            if vm.listenOnOpen {
                vm.listenOnOpen = false
                voice.start()
            }
        }
        .onDisappear { voice.stop() }
        .onChange(of: voice.permissionDenied) { _, denied in showVoiceDenied = denied }
        .alert(L("Micrófono"), isPresented: $showVoiceDenied) {
            Button(L("Ajustes")) {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
            Button(L("Cancelar"), role: .cancel) {}
        } message: {
            Text(L("Para dictar necesito permiso de micrófono y de reconocimiento de voz. Puedes dárselo en Ajustes."))
        }
    }

    // MARK: Cabecera

    /// Cumbri, "Asistente" y debajo qué está haciendo: así se ve que piensa aunque la respuesta quede fuera de la vista.
    private var header: some View {
        HStack(spacing: 8) {
            AssistantAvatar(size: 30)
            VStack(alignment: .leading, spacing: 0) {
                Text(L("Asistente"))
                    .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text(vm.isLoading ? L("Escribiendo…") : L("Responde con datos reales"))
                    .font(.system(size: 11)).foregroundStyle(vm.isLoading ? Cumbre.terra : Cumbre.ink3)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func toolbarIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
            .frame(width: 32, height: 32)
            .background(Cumbre.paper, in: Circle())
            .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
    }

    // MARK: Mensajes

    private var messagesList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    if vm.messages.isEmpty { welcome }
                    ForEach(vm.messages) { message in
                        messageView(message).id(message.id)
                    }
                    if vm.isLoading {
                        botRow {
                            TypingDots()
                                .padding(.horizontal, 14).padding(.vertical, 13)
                                .background(Cumbre.paper, in: Self.botShape)
                                .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
                        }
                        .id("loading")
                    }
                    // Preguntas que esperan su turno (se contestan por orden).
                    ForEach(Array(vm.queued.enumerated()), id: \.offset) { index, pending in
                        HStack {
                            Spacer(minLength: 44)
                            VStack(alignment: .trailing, spacing: 3) {
                                Text(pending)
                                    .font(.system(size: 14)).foregroundStyle(.white)
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .background(Cumbre.terra, in: Self.userShape)
                                    .opacity(0.55)
                                Text(L("en cola · %@", index + 1))
                                    .font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.ink3)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            // Al abrir el chat, directamente en lo último que se habló (no arriba del todo).
            // Dos intentos: el primero ya, el segundo cuando la lista perezosa ha medido las filas.
            .onAppear {
                scrollToLast(proxy)
                scrollToLast(proxy, after: 0.3)
            }
            .onChange(of: vm.messages.count) { _, _ in
                if let last = vm.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .top) } }
            }
            .onChange(of: vm.isLoading) { _, loading in
                if loading { withAnimation { proxy.scrollTo("loading", anchor: .bottom) } }
            }
        }
    }

    private func scrollToLast(_ proxy: ScrollViewProxy, after delay: Double = 0) {
        guard let last = vm.messages.last else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            proxy.scrollTo(last.id, anchor: .bottom)
        }
    }

    private var welcome: some View {
        VStack(spacing: 12) {
            Image("cumbri_welcome")
                .resizable().scaledToFit()
                .frame(height: 130)
                .accessibilityHidden(true)
            Text(AssistantPresenter.greeting(displayName: AppDependencies.shared.authBridge.currentDisplayName()))
                .font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text(L("Pregúntame por escuelas, bloques, vías, el tiempo o la sombra de un sector. También puedes dictarlo."))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 8) {
                ForEach(Array(vm.suggestions.enumerated()), id: \.offset) { index, s in
                    suggestionCard(s, icon: Self.suggestionIcons[index % Self.suggestionIcons.count])
                }
            }
            .padding(.top, 4)
            Text(L("Mantén pulsado el círculo para hablar directamente."))
                .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                .multilineTextAlignment(.center)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    private func suggestionCard(_ text: String, icon: String) -> some View {
        Button { Task { await vm.send(text) } } label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.terra)
                    .frame(width: 34, height: 34)
                    .background(Cumbre.terraBg, in: Circle())
                Text(text)
                    .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                Image(systemName: "arrow.up.right")
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
            .shadow(color: .black.opacity(0.06), radius: 4, y: 1)
        }
        .buttonStyle(.plain)
    }

    // MARK: Burbujas

    /// Bocadillos con la esquina del lado de quien habla casi en punta (estilo mensajería).
    private static let userShape = UnevenRoundedRectangle(
        topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 5, topTrailingRadius: 18)
    private static let botShape = UnevenRoundedRectangle(
        topLeadingRadius: 18, bottomLeadingRadius: 5, bottomTrailingRadius: 18, topTrailingRadius: 18)

    /// Fila del asistente: Cumbri pequeño a la izquierda y su contenido al lado.
    private func botRow<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(alignment: .top, spacing: 8) {
            AssistantAvatar(size: 22).padding(.top, 2)
            VStack(alignment: .leading, spacing: 10) { content() }
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder
    private func messageView(_ m: AssistantMessage) -> some View {
        if m.isUser {
            HStack {
                Spacer(minLength: 44)
                Text(m.text)
                    .font(.system(size: 14)).foregroundStyle(.white)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Cumbre.terra, in: Self.userShape)
            }
        } else {
            botRow {
                Text(m.text)
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Cumbre.paper, in: Self.botShape)
                    .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
                if !m.chips.isEmpty { chipsRow(m.chips) }
                if let r = m.recommendation { AssistantRecommendationView(recommendation: r) }
                if let b = m.breakdown { AssistantBreakdownView(breakdown: b) }
                if let w = m.weather { AssistantWeatherCard(weather: w) }
                if !m.journalEntries.isEmpty { AssistantJournalListView(entries: m.journalEntries, total: m.journalTotal) }
                if let retry = m.retryText { retryButton(retry) }
                if m.pendingActions.contains(where: { $0.state == .waiting }) { confirmButtons(m) }
                if !m.hits.isEmpty { AssistantHitsView(hits: m.hits, total: m.hitsTotal) }
                if m.restored, m.recommendation != nil || m.breakdown != nil || m.weather != nil || !m.hits.isEmpty,
                   let note = AssistantPresenter.savedNote(createdAt: m.createdAt) {
                    Text(note)
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                if !m.options.isEmpty { optionsRow(m.options) }
                if m.id == vm.messages.last?.id, !vm.isLoading, vm.queued.isEmpty {
                    followUpsRow(AssistantPresenter.followUps(for: m))
                }
            }
        }
    }

    /// CONFIRMAR / CANCELAR para una acción propuesta: la app nunca hace nada por su cuenta.
    private func confirmButtons(_ m: AssistantMessage) -> some View {
        let waiting = m.pendingActions.filter { $0.state == .waiting }
        return VStack(alignment: .leading, spacing: 8) {
            if waiting.count > 1 {
                // Varias candidatas: una tarjeta por cada una para elegir, y CANCELAR debajo.
                ForEach(waiting) { action in
                    Button { Task { await vm.confirm(m.id, action.id) } } label: {
                        optionLabel(confirmTitle(action, several: true))
                    }
                }
                cancelButton(m)
            } else {
                HStack(spacing: 8) {
                    ForEach(waiting) { action in
                        Button { Task { await vm.confirm(m.id, action.id) } } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "checkmark").font(.system(size: 12, weight: .bold))
                                Text(confirmTitle(action, several: false)).font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(Cumbre.terra, in: Capsule())
                        }
                    }
                    cancelButton(m)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(vm.isLoading)
    }

    private func cancelButton(_ m: AssistantMessage) -> some View {
        Button { vm.cancel(m.id) } label: {
            Text(L("Cancelar")).font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Cumbre.ink)
                .padding(.horizontal, 16).padding(.vertical, 9)
                .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1.5))
        }
    }

    /// "Confirmar", o con varias candidatas el nombre de cada una para poder elegir.
    private func confirmTitle(_ action: PendingAction, several: Bool) -> String {
        guard several, let hit = action.hit else { return L("Confirmar") }
        let name = hit.lineName ?? hit.blockName
        let grade = hit.grade.map { " · \($0)" } ?? ""
        return "\(name)\(grade) · \(hit.schoolName)"
    }

    /// Tras un fallo pasajero, repetir la misma pregunta con un toque (sin escribirla de nuevo).
    private func retryButton(_ text: String) -> some View {
        Button { Task { await vm.retry(text) } } label: {
            HStack(spacing: 6) {
                Image(systemName: "arrow.clockwise").font(.system(size: 12, weight: .bold))
                Text(L("Reintentar")).font(.system(size: 14, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16).padding(.vertical, 9)
            .background(Cumbre.terra, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(vm.isLoading)
    }

    /// Lo que ha entendido, en pastillas con un icono pequeño (sitio, sol/sombra, cuándo…).
    private func chipsRow(_ chips: [String]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(chips.enumerated()), id: \.offset) { _, chip in
                    HStack(spacing: 4) {
                        if let icon = AssistantPresenter.chipIcon(chip) {
                            Image(systemName: icon).font(.system(size: 9, weight: .bold))
                        }
                        Text(chip).font(Cumbre.mono(11, .bold))
                    }
                    .foregroundStyle(Cumbre.terra)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(Cumbre.terraBg, in: Capsule())
                }
            }
        }
    }

    private func optionsRow(_ options: [AssistantOption]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(options, id: \.id) { option in
                Button { Task { await vm.send(option.name) } } label: { optionLabel(option.name) }
                    .buttonStyle(.plain)
            }
        }
    }

    /// Tarjeta redondeada tocable con flecha: opciones de "¿a cuál te refieres?" y candidatas a confirmar.
    private func optionLabel(_ text: String) -> some View {
        HStack(spacing: 8) {
            Text(text)
                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.leading)
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.06), radius: 3, y: 1)
    }

    /// Ideas para seguir preguntando debajo de la última respuesta: se envían tal cual o abren la escuela.
    @ViewBuilder
    private func followUpsRow(_ items: [AssistantPresenter.FollowUp]) -> some View {
        if !items.isEmpty {
            FlowLayoutView(spacing: 6) {
                ForEach(items) { item in
                    Button {
                        switch item.action {
                        case .ask(let text): Task { await vm.send(text) }
                        case .open(let schoolId): vm.requestOpen(schoolId: schoolId)
                        }
                    } label: {
                        Text(item.title)
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.terra)
                            .lineLimit(1)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 2)
        }
    }

    // MARK: Entrada

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField(L("Pregunta sobre escuelas, tiempo o bloques…"), text: $vm.input, axis: .vertical)
                .lineLimit(1...4)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit { send() }
                .padding(.horizontal, 16).padding(.vertical, 11)
                .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 21))
                .shadow(color: .black.opacity(0.07), radius: 4, y: 1)
                .onChange(of: vm.input) { _, value in
                    if value.count > AssistantViewModel.maxText {
                        vm.input = String(value.prefix(AssistantViewModel.maxText))
                    }
                }

            Button { focused = false; voice.toggle() } label: {
                ZStack {
                    if voice.isListening { PulseRing() }
                    Image(systemName: voice.isListening ? "stop.fill" : "mic.fill")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(voice.isListening ? .white : Cumbre.terra)
                        .frame(width: 42, height: 42)
                        .background(voice.isListening ? Cumbre.terra : Cumbre.terraBg, in: Circle())
                }
                .frame(width: 42, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(voice.isListening ? L("Parar el dictado") : L("Dictar por voz"))

            Button { send() } label: {
                Image(systemName: "arrow.up")
                    .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(vm.canSend ? Cumbre.terra : Cumbre.ink3, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(!vm.canSend)
            .accessibilityLabel(L("Enviar"))
        }
        .padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 8)
        .background(Cumbre.bg)
    }

    /// Estado del micrófono, debajo de la caja: corto, para no tapar la conversación.
    @ViewBuilder
    private var voiceHint: some View {
        if voice.isListening {
            Text(L("Escuchando · se envía solo al callar"))
                .font(.system(size: 11, weight: .medium)).foregroundStyle(Cumbre.terra)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 6)
        } else if voice.heardNothing {
            Text(L("No te he oído. Toca el micrófono e inténtalo otra vez."))
                .font(.system(size: 11)).foregroundStyle(Cumbre.bad)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 6)
        }
    }

    private func send() {
        voice.stop()
        focused = false
        Task { await vm.send() }
    }
}

/// Cumbri en círculo: cabecera del chat y al lado de cada respuesta.
private struct AssistantAvatar: View {
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
private struct TypingDots: View {
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
private struct PulseRing: View {
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
