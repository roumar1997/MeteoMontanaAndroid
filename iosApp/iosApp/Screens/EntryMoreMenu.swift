import SwiftUI

/// «⋯» of a logged entry with «Editar» and «Borrar» (journal «Mis vías/bloques» and board days, §26). Either action may be nil.
/// The labels default to the journal ones. Mirror of `EntryMoreMenu.kt`.
struct EntryMoreMenu: View {
    var onEdit: (() -> Void)?
    var onDelete: (() -> Void)?
    var editLabel: String = L("Editar ascenso")
    var deleteLabel: String = L("Borrar del diario")

    var body: some View {
        Menu {
            if let onEdit {
                Button(action: onEdit) { Label(editLabel, systemImage: "pencil") }
            }
            if let onDelete {
                Button(role: .destructive, action: onDelete) { Label(deleteLabel, systemImage: "trash") }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Cumbre.ink2)
                .frame(width: 36, height: 36)
                .background(Cumbre.paper, in: Circle())
                .contentShape(Circle())
        }
        .accessibilityLabel(L("Más opciones"))
    }
}
