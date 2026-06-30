import SwiftUI

/// Selector de idioma — espejo de LanguagePickerDialog.kt en Android.
struct LanguagePickerView: View {
    let onSelected: (String) -> Void
    /// Callback de cierre cuando se usa como gate (no como sheet).
    /// Cuando se usa en un .sheet, `dismiss()` es suficiente; este callback
    /// permite al padre (RootView) actualizar su estado sin depender de dismiss.
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    private var current: String { LanguageManager.shared.currentLanguage }

    private func close() { onClose?(); dismiss() }

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                option("Español", code: "es")
                Divider()
                option("English", code: "en")
            }
            .padding(.top, 8)
            .navigationTitle("Idioma / Language")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(NSLocalizedString("common_close", comment: "")) { close() }
                }
            }
        }
    }

    private func option(_ label: String, code: String) -> some View {
        Button {
            onSelected(code)
            close()
        } label: {
            HStack {
                Text(label).foregroundStyle(Cumbre.ink)
                Spacer()
                if current == code {
                    Image(systemName: "checkmark").foregroundStyle(Cumbre.terra)
                }
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 16)
        }
        .buttonStyle(.plain)
    }
}
