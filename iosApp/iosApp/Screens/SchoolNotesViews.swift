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
    /// Ficha de escuela con pestañas: las notas SIEMPRE abiertas (la pestaña ya es el desplegable).
    var alwaysOpen = false
    /// Nombre de la escuela, para el texto de la caja de escribir.
    var schoolName: String? = nil
    @State private var myPhoto: String?
    @State private var myName: String?

    @State private var draft = ""
    @State private var photoNote: Note?
    @State private var pickerItem: PhotosPickerItem?
    @State private var pickedImage: UIImage?
    // Plegada por defecto: con muchas notas la pantalla se hacía eterna.
    @State private var expanded = false
    /// Orden de la lista: por utilidad (▲ − ▼), de más a menos votada o al revés.
    private enum NoteOrder { case most, least }
    @State private var order: NoteOrder = .most
    // Moderación: denunciar notas ajenas + ocultar al instante.
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var reportNote: Note? = nil

    private var orderedNotes: [Note] {
        let visible = notes.filter { !moderation.hiddenIds.contains("NOTE:\($0.id)") }
        return NoteOrdering.shared.byVotes(notes: visible, mostVotedFirst: order == .most)
    }

    /// Un único menú pequeño en vez de dos botones siempre a la vista.
    private var orderMenu: some View {
        Menu {
            Button(L("MÁS VOTADAS")) { order = .most }
            Button(L("MENOS VOTADAS")) { order = .least }
        } label: {
            HStack(spacing: 4) {
                Text(order == .most ? L("MÁS VOTADAS") : L("MENOS VOTADAS")).font(Cumbre.mono(10.5, .bold))
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 9))
            }
            .foregroundStyle(Cumbre.ink2)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack(spacing: 8) {
                    Text("NOTAS COMUNITARIAS").eyebrow()
                    if !notes.isEmpty {
                        Text(notes.count == 1 ? L("1 nota") : L("%@ notas", notes.count))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Cumbre.ink)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(Cumbre.paper2)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    Spacer()
                    if !alwaysOpen {
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Cumbre.ink2)
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(alwaysOpen)
            .padding(.top, 8)

            if expanded || alwaysOpen {
            // Composer: mi avatar, cámara redonda y enviar redondo terracota
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    AvatarCircle(url: myPhoto, size: 32, uid: Auth.auth().currentUser?.uid)
                    TextField(schoolName.map { L("Escribe una nota sobre %@…", $0) } ?? NSLocalizedString("detail_write_note", comment: ""),
                              text: $draft, axis: .vertical)
                        .lineLimit(1...5)
                        .guideTarget("detail_notes.write")
                        .font(.system(size: 15))
                        .foregroundStyle(Cumbre.ink)
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Image(systemName: "camera").font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                            .frame(width: 36, height: 36).background(Cumbre.paper2).clipShape(Circle())
                    }
                    .accessibilityLabel(L("Añadir foto"))
                    Button {
                        let text = draft; let img = pickedImage
                        draft = ""; pickedImage = nil; pickerItem = nil
                        onPublish(text, img)
                    } label: {
                        Group {
                            if publishing { ProgressView().tint(.white) }
                            else { Image(systemName: "paperplane.fill").font(.system(size: 15)).foregroundStyle(.white) }
                        }
                        .frame(width: 36, height: 36)
                        .background(canPublish ? Cumbre.terra : Cumbre.terra.opacity(0.35))
                        .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!canPublish)
                    .accessibilityLabel(L("Enviar nota"))
                }
                .padding(8)
                .background(Cumbre.paper)
                .clipShape(RoundedRectangle(cornerRadius: 28))
                .overlay(RoundedRectangle(cornerRadius: 28).stroke(Cumbre.rule, lineWidth: 1))

                // Vista previa de la foto elegida (con opción de quitarla).
                if let img = pickedImage {
                    HStack(spacing: 8) {
                        Image(uiImage: img).resizable().scaledToFill()
                            .frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 8))
                        Button { pickedImage = nil; pickerItem = nil } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(Cumbre.ink3)
                        }.buttonStyle(.plain)
                        Spacer()
                    }
                }
            }
            .padding(.top, 8)

            if notes.count > 1 {
                orderMenu
                    .guideTarget("detail_notes.order")
            }
            if notes.isEmpty {
                Text("Sin notas aún. ¡Sé el primero!")
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
                    .padding(.vertical, 4)
            } else {
                // Llegan del backend ordenadas por utilidad (▲ − ▼); aquí se puede dar la vuelta.
                ForEach(orderedNotes, id: \.id) { n in
                    NoteRowView(note: n, onPhotoTap: { photoNote = n },
                                onVote: { v in onVote(n, v) },
                                canReport: Auth.auth().currentUser?.uid != n.uid,
                                onReport: { reportNote = n })
                }
            }

            }   // fin if expanded
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .task {
            guard myPhoto == nil, let uid = Auth.auth().currentUser?.uid else { return }
            if let p = try? await AppDependencies.shared.container.getPublicProfile.invoke(uid: uid) {
                myPhoto = p.photoUrl; myName = p.displayName ?? p.username
            }
        }
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
            ReportSheet(title: L("DENUNCIAR NOTA"), authorLabel: n.author ?? "usuario") { reason, alsoBlock, details in
                moderation.report(targetType: "NOTE", targetId: n.id, reason: reason, details: details,
                                  alsoBlockUid: alsoBlock ? n.uid : nil)
            }
        }
    }

    private var canPublish: Bool {
        !publishing && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// Una nota con el aspecto de una publicación del feed: autor, texto, foto y votos de utilidad.
private struct NoteRowView: View {
    let note: Note
    let onPhotoTap: () -> Void
    var onVote: (Int) -> Void = { _ in }
    var canReport: Bool = false
    var onReport: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                AvatarCircle(url: note.authorPhotoUrl, size: 36, uid: note.uid)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(note.author ?? L("Anónimo"))
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                        VerifiedCheck(uid: note.uid, size: 13)
                    }
                    Text((note.authorUsername.map { "@" + $0 + " · " } ?? "") + feedRelativeTime(note.createdAt))
                        .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
                Spacer()
                if canReport {
                    Button(action: onReport) {
                        Image(systemName: "flag").font(.system(size: 13))
                            .foregroundStyle(Cumbre.ink3.opacity(0.7)).padding(10).contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
            }
            Text(note.text)
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let url = note.photoUrl, let u = URL(string: url) {
                Button(action: onPhotoTap) {
                    CachedImage(url: u) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(Cumbre.paper)
                    }
                    .frame(height: 180)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            // Voto de utilidad: tocar de nuevo tu voto lo retira.
            HStack(spacing: 6) {
                voteChip("▲ \(note.upvotesCount)", active: note.myVote == 1) { onVote(1) }
                voteChip("▼ \(note.downvotesCount)", active: note.myVote == -1) { onVote(-1) }
                Spacer()
            }
        }
        .padding(12)
        .background(Cumbre.paper)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
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
                        CachedImage(url: u) { img in
                            img.resizable().scaledToFit()
                        } placeholder: { Bone(height: 220).shimmer() }
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
