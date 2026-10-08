import SwiftUI
import Shared

/// Burbuja flotante del asistente: un círculo con la mascota, siempre abajo a
/// la derecha, que abre el chat. La coloca MainTabView por encima de la barra.
struct AssistantBubble: View {
    /// La conversación es de toda la app (la crea MeteoMontanaApp): cerrar el chat, abrir una
    /// piedra y volver a abrir el círculo no la pierde.
    @EnvironmentObject private var assistant: AssistantViewModel
    @State private var open = false

    var body: some View {
        Button { open = true } label: {
            Image("logo_cumbre")
                .resizable().scaledToFill()
                .frame(width: 54, height: 54)
                .clipShape(Circle())
                .background(Circle().fill(Cumbre.paper))
                .overlay(Circle().stroke(Cumbre.terra, lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Asistente de Cumbre"))
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
        Task { await ShareLinkRouter.shared.openSchool(id: request.schoolId, viaId: request.viaId) }
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

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                messagesList
                inputBar
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Cumbre")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !vm.messages.isEmpty {
                        Button(L("NUEVA")) { voice.stop(); vm.reset() }
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(NSLocalizedString("common_close", comment: "")) { voice.stop(); dismiss() }
                        .foregroundStyle(Cumbre.terra)
                }
            }
        }
        .onAppear { voice.onText = { vm.input = String($0.prefix(AssistantViewModel.maxText)) } }
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
                        HStack(spacing: 8) {
                            ProgressView().tint(Cumbre.terra)
                            Text(L("Pensando…")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                        }
                        .id("loading")
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: vm.messages.count) { _, _ in
                if let last = vm.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .top) } }
            }
            .onChange(of: vm.isLoading) { _, loading in
                if loading { withAnimation { proxy.scrollTo("loading", anchor: .bottom) } }
            }
        }
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L("¿Qué buscas hoy?"))
                .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(L("Pregúntame por escuelas, bloques, vías, el tiempo o la sombra de un sector. También puedes dictarlo."))
                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
            ForEach(vm.suggestions, id: \.self) { s in
                Button { Task { await vm.send(s) } } label: {
                    Text(s)
                        .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                        .multilineTextAlignment(.leading)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Cumbre.paper)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 8)
    }

    @ViewBuilder
    private func messageView(_ m: AssistantMessage) -> some View {
        if m.isUser {
            HStack {
                Spacer(minLength: 44)
                Text(m.text)
                    .font(.system(size: 14)).foregroundStyle(.white)
                    .padding(.horizontal, 13).padding(.vertical, 10)
                    .background(Cumbre.inkButton, in: RoundedRectangle(cornerRadius: 12))
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                Text(m.text)
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                    .padding(.horizontal, 13).padding(.vertical, 10)
                    .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
                if !m.chips.isEmpty { chipsRow(m.chips) }
                if let r = m.recommendation { AssistantRecommendationView(recommendation: r) }
                if let b = m.breakdown { AssistantBreakdownView(breakdown: b) }
                if !m.hits.isEmpty { AssistantHitsView(hits: m.hits, total: m.hitsTotal) }
                if m.restored, m.recommendation != nil || m.breakdown != nil || !m.hits.isEmpty,
                   let note = AssistantPresenter.savedNote(createdAt: m.createdAt) {
                    Text(note)
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                if !m.options.isEmpty { optionsRow(m.options) }
            }
        }
    }

    private func chipsRow(_ chips: [String]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(chips.enumerated()), id: \.offset) { _, chip in
                    Text(chip)
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(Cumbre.terraBg)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra.opacity(0.35), lineWidth: 1))
                }
            }
        }
    }

    private func optionsRow(_ options: [AssistantOption]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(options, id: \.id) { option in
                Button { Task { await vm.send(option.name) } } label: {
                    Text(option.name)
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .background(Cumbre.bg)
                        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
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
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink, lineWidth: 1))
                .onChange(of: vm.input) { _, value in
                    if value.count > AssistantViewModel.maxText {
                        vm.input = String(value.prefix(AssistantViewModel.maxText))
                    }
                }

            Button { focused = false; voice.toggle() } label: {
                Image(systemName: voice.isListening ? "stop.fill" : "mic.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(voice.isListening ? .white : Cumbre.terra)
                    .frame(width: 42, height: 42)
                    .background(voice.isListening ? Cumbre.terra : Cumbre.terraBg)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.terra, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(voice.isListening ? L("Parar el dictado") : L("Dictar por voz"))

            Button { send() } label: {
                Image(systemName: "arrow.up")
                    .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(vm.canSend ? Cumbre.terra : Cumbre.ink3)
            }
            .buttonStyle(.plain)
            .disabled(!vm.canSend)
            .accessibilityLabel(L("Enviar"))
        }
        .padding(12)
        .background(Cumbre.bg)
        .overlay(alignment: .top) { Rectangle().fill(Cumbre.rule).frame(height: 1) }
    }

    private func send() {
        voice.stop()
        focused = false
        Task { await vm.send() }
    }
}
