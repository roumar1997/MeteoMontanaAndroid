import SwiftUI

/// Cierra el teclado al pulsar Intro en un buscador. Álvaro, 2026-09-30:
/// "después de escribir un nombre completo... y guardar, se queda el
/// teclado subido" — ningún `TextField` de la app tenía `.onSubmit` para
/// esto (comprobado: no hay un solo `resignFirstResponder` en todo el repo).
extension View {
    func dismissKeyboardOnSubmit() -> some View {
        self
            .submitLabel(.search)
            .onSubmit {
                UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
                )
            }
    }
}
