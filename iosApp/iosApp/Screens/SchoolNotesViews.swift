import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// NOTAS COMUNITARIAS — espejo de NotesSection.kt: lista, composer con foto,
// votos y moderación/denuncia.

extension Note: Identifiable {}

// MARK: - Notas comunitarias (réplica de NotesSection.kt)

/// Sección de notas del detalle de escuela. Leer es público; publicar requiere
/// sesión (garantizada por el gate de login). La foto se muestra con AsyncImage
/// nativo; subir foto necesita el bridge de Firebase Storage (pendiente).
struct NotesSectionView: View {
    let notes: [Note]
    let publishing: Bool
    let onPublish: (String, UIImage?) -> Void
    var onVote: (Note, Int) -> Void = { _, _ in }

    @State private var draft = ""
    @State private var photoNote: Note?
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedImage: UIImage?
    // Plegada por defecto: con muchas notas la pantalla se hacía eterna.
    @State private var expanded = false
    // Moderación: denunciar notas ajenas + ocultar al instante.
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var reportNote: Note? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 8) {
                    Text("NOTAS COMUNITARIAS").eyebrow()
                    if !notes.isEmpty {
                        Text("\(notes.count)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(Cumbre.paper2)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Cumbre.ink2)
                }
            }
            .buttonStyle(.plain)
            .padding(.top, 8)

            if expanded {
            if notes.isEmpty {
                Text("Sin notas aún. ¡Sé el primero!")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(.vertical, 4)
            } else {
                // Llegan del backend ordenadas por utilidad (▲ − ▼).
                ForEach(notes.filter { !moderation.hiddenIds.contains("NOTE:\($0.id)") }, id: \.id) { n in
                    NoteRowView(note: n, onPhotoTap: { photoNote = n },
                                onVote: { v in onVote(n, v) },
                                canReport: Auth.auth().currentUser?.uid != n.uid,
                                onReport: { reportNote = n })
                    Divider().overlay(Cumbre.rule)
                }
            }

            // Composer
            VStack(alignment: .trailing, spacing: 8) {
                TextField(NSLocalizedString("detail_write_note", comment: ""), text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.system(size: 15))
                    .foregroundStyle(Cumbre.ink)
                    .padding(10)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))

                // Vista previa de la foto elegida (con opción de quitarla).
                if let img = pickedImage {
                    HStack(spacing: 8) {
                        Image(uiImage: img).resizable().scaledToFill()
                            .frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 4))
                        Button { pickedImage = nil; pickerItem = nil } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                        Spacer()
                    }
                }

                HStack(spacing: 10) {
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "camera").font(.system(size: 16)).foregroundStyle(Cumbre.terra)
                            .frame(width: 34, height: 34).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    }
                    Spacer()
                    Button {
                        let text = draft; let img = pickedImage
                        draft = ""; pickedImage = nil; pickerItem = nil
                        onPublish(text, img)
                    } label: {
                        HStack(spacing: 6) {
                            if publishing { ProgressView().tint(.white) }
                            Text("PUBLICAR").font(Cumbre.mono(12, .bold)).tracking(0.8)
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(canPublish ? Cumbre.terra : Cumbre.ink3)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canPublish)
                }
            }
            .padding(.top, 8)
            }   // fin if expanded
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) { pickedImage = img }
            }
        }
        .sheet(item: $photoNote) { n in
            NotePhotoSheet(note: n)
        }
        .sheet(item: $reportNote) { n in
            ReportSheet(title: "DENUNCIAR NOTA", authorLabel: n.author ?? "usuario") { reason, alsoBlock in
                moderation.report(targetType: "NOTE", targetId: n.id, reason: reason,
                                  alsoBlockUid: alsoBlock ? n.uid : nil)
            }
        }
    }

    private var canPublish: Bool {
        !publishing && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

private struct NoteRowView: View {
    let note: Note
    let onPhotoTap: () -> Void
    var onVote: (Int) -> Void = { _ in }
    var canReport: Bool = false
    var onReport: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(note.author ?? "Anónimo")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Cumbre.ink)
                Spacer()
                Text(shortDate(note.createdAt))
                    .font(Cumbre.mono(11))
                    .foregroundStyle(Cumbre.ink3)
            }
            Text(note.text)
                .font(.system(size: 15))
                .foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let url = note.photoUrl, let u = URL(string: url) {
                Button(action: onPhotoTap) {
                    AsyncImage(url: u) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(Cumbre.paper)
                    }
                    .frame(height: 120)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            // Voto de utilidad: tocar de nuevo tu voto lo retira.
            HStack(spacing: 6) {
                if canReport {
                    Button(action: onReport) {
                        Image(systemName: "flag")
                            .font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink3.opacity(0.7))
                            .padding(10)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                voteChip("▲ \(note.upvotesCount)", active: note.myVote == 1) { onVote(1) }
                voteChip("▼ \(note.downvotesCount)", active: note.myVote == -1) { onVote(-1) }
            }
        }
        .padding(.vertical, 8)
    }

    private func voteChip(_ label: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(active ? Cumbre.terra : Cumbre.ink2)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(active ? Cumbre.terraBg : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8)
                    .stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Foto de la nota a pantalla completa. `Note` es Identifiable vía su `id`.
private struct NotePhotoSheet: View {
    let note: Note
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let url = note.photoUrl, let u = URL(string: url) {
                        AsyncImage(url: u) { img in
                            img.resizable().scaledToFit()
                        } placeholder: { ProgressView() }
                    }
                    Text(note.text).font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                        .padding(.horizontal, 16)
                }
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.terra)
                }
            }
        }
    }
}

/// Fila de 5 estrellas tocables para valorar una vía.

private func shortDate(_ iso: String) -> String {
    if iso.count >= 10 { return String(iso.prefix(10)) }
    return iso
}
