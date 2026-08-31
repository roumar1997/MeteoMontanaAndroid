import SwiftUI
import Shared

/// Diálogo simple de texto libre → POST /api/suggestions (sin cola de
/// revisión). Paridad Android (HelpSheet.kt → SuggestionDialog).
struct SuggestionSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var texto = ""
    @State private var enviando = false
    @State private var enviado = false
    @State private var error = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                if enviado {
                    Text("¡Gracias! Lo hemos recibido.")
                        .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                } else {
                    Text("Cuéntanos qué te gustaría que hiciera la app o qué no funciona bien.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                    TextEditor(text: $texto)
                        .frame(height: 140)
                        .padding(6)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
                        .disabled(enviando)
                    if error {
                        Text("No se pudo enviar. Inténtalo otra vez.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.bad)
                    }
                    Button {
                        Task { await enviar() }
                    } label: {
                        HStack {
                            if enviando { ProgressView().tint(.white) } else { Text("ENVIAR") }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Cumbre.terra, in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                        .foregroundStyle(.white)
                    }
                    .disabled(texto.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || enviando)
                }
            }
            .padding(20)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Sugerir algo o reportar un fallo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }
                        .foregroundStyle(Cumbre.terra)
                }
            }
        }
    }

    private func enviar() async {
        enviando = true
        error = false
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        do {
            try await AppDependencies.shared.container.submitSuggestion.invoke(
                message: texto.trimmingCharacters(in: .whitespacesAndNewlines),
                platform: "IOS",
                appVersion: version)
            enviando = false
            enviado = true
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            dismiss()
        } catch {
            enviando = false
            self.error = true
        }
    }
}
