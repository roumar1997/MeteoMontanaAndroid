import SwiftUI
import UIKit
import Shared

// Publicar ascensos en el feed Comunidad — espejo de FeedPublishPrefs.kt y
// FeedPublishSheet (SchoolMap.kt) de Android.

/// Preferencia "Publicar ascensos en el feed":
/// - ask (default): al marcar HECHO se abre la hoja de publicar.
/// - always: publica directo sin preguntar.
/// - never: ni pregunta ni publica (solo diario).
enum FeedPublishMode: String, CaseIterable {
    case ask, always, never

    var label: String {
        switch self {
        case .ask: return "Preguntar"
        case .always: return "Siempre"
        case .never: return "Nunca"
        }
    }
}

enum FeedPublishPrefs {
    private static let key = "feed_publish_mode"

    static var mode: FeedPublishMode {
        get {
            FeedPublishMode(rawValue: UserDefaults.standard.string(forKey: key) ?? "") ?? .ask
        }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: key) }
    }
}

/// Tick pendiente de confirmar (hoja "Publicar en el feed").
struct PendingFeedTick: Identifiable {
    let id = UUID()
    let line: BlockLine
    let index: Int
    let wasProject: Bool
}

/// Hoja de publicar un ascenso (estilo Cumbre): eyebrow del tipo, vía + grado,
/// checkbox "Publicar siempre sin preguntar", PUBLICAR EN EL FEED (primario
/// Terra) y SOLO EN MI DIARIO. Cerrar la hoja = no marcar nada.
struct FeedPublishSheet: View {
    let lineLabel: String
    let wasProject: Bool
    let onPublish: (_ always: Bool, _ caption: String?, _ photo: UIImage?) -> Void
    let onDiaryOnly: () -> Void

    @State private var always = false
    // Descripción opcional del autor (viaja como "caption", max 500).
    @State private var caption = ""
    // Foto de celebración: hecha en el momento con la cámara del sistema. Se
    // guarda en un ObservableObject (no @State) porque la cámara se presenta
    // por UIKit y un @State captado en el callback no refrescaba la hoja.
    @StateObject private var photoStore = CapturedPhotoStore()
    @State private var showCameraDenied = false

    private func requestCamera() {
        CameraAccess.request { granted in
            guard granted else { showCameraDenied = true; return }
            // Presentación por UIKit (sin el parpadeo del fullScreenCover dentro
            // de la hoja). La foto capturada va al ObservableObject, que sí
            // refresca la miniatura.
            presentSystemCamera { img in photoStore.image = img }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(wasProject ? "PROYECTO CONSEGUIDO" : "HECHO")
                .font(Cumbre.mono(10, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
            Text("¿Marcar como hecha?")
                .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                .padding(.top, 4)
            Text(lineLabel)
                .font(.system(size: 16, weight: .bold)).foregroundStyle(Cumbre.ink2)
                .padding(.top, 2)

            // Descripción opcional del post (paridad con FeedPublishSheet de
            // SchoolMap.kt: placeholder + límite 500).
            TextField("Añade una descripción (opcional)", text: $caption, axis: .vertical)
                .lineLimit(2...4)
                .font(.system(size: 14))
                .foregroundStyle(Cumbre.ink)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(Cumbre.paper)
                .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                .onChange(of: caption) { _, new in
                    if new.count > 500 { caption = String(new.prefix(500)) }
                }
                .padding(.top, 16)

            // Foto de celebración (opcional): fila para abrir la cámara o
            // miniatura 88×110 con ✕ para quitarla + "REPETIR FOTO".
            if let photo = photoStore.image {
                HStack(spacing: 12) {
                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: photo)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 88, height: 110)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .overlay(RoundedRectangle(cornerRadius: 6)
                                .stroke(Cumbre.rule, lineWidth: 1))
                        // ✕ quita la foto (se puede volver a hacer otra).
                        Button { photoStore.image = nil } label: {
                            Text("✕")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 22, height: 22)
                                .background(Color.black.opacity(0.55))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                    }
                    Button(action: requestCamera) {
                        Text("REPETIR FOTO")
                            .font(Cumbre.mono(10, .bold)).tracking(1.8)
                            .foregroundStyle(Cumbre.terra)
                            .padding(8)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Spacer()
                }
                .padding(.top, 16)
            } else {
                Button(action: requestCamera) {
                    HStack(spacing: 10) {
                        Image(systemName: "camera")
                            .font(.system(size: 16))
                            .foregroundStyle(Cumbre.terra)
                        Text("Añadir foto de celebración")
                            .font(.system(size: 14))
                            .foregroundStyle(Cumbre.ink2)
                        Spacer()
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 16)
            }

            // Checkbox "Publicar siempre sin preguntar".
            Button { always.toggle() } label: {
                HStack(spacing: 10) {
                    Image(systemName: always ? "checkmark.square.fill" : "square")
                        .foregroundStyle(always ? Cumbre.terra : Cumbre.ink3)
                    Text("Publicar siempre sin preguntar")
                        .font(.system(size: 14))
                        .foregroundStyle(always ? Cumbre.terra : Cumbre.ink3)
                    Spacer()
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .overlay(RoundedRectangle(cornerRadius: 2)
                    .stroke(always ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, 16)

            // Primario: PUBLICAR EN EL FEED (Terra, texto blanco).
            Button {
                let c = caption.trimmingCharacters(in: .whitespacesAndNewlines)
                onPublish(always, c.isEmpty ? nil : c, photoStore.image)
            } label: {
                Text("PUBLICAR EN EL FEED")
                    .font(Cumbre.mono(11, .bold)).tracking(1.4)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Cumbre.terra)
                    .clipShape(RoundedRectangle(cornerRadius: 2))
            }
            .buttonStyle(.plain)
            .padding(.top, 16)

            // Secundario: solo diario.
            Button(action: onDiaryOnly) {
                Text("SOLO EN MI DIARIO")
                    .font(Cumbre.mono(11, .bold)).tracking(1.4)
                    .foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.plain)
            .padding(.top, 8)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.top, 20)
        .background(Cumbre.bg.ignoresSafeArea())
        // Más alta que antes: descripción + fila/miniatura de foto.
        .presentationDetents([.height(photoStore.image == nil ? 480 : 560)])
        // Permiso denegado: llevar a Ajustes (iOS no re-pregunta).
        .alert("Cumbre necesita acceso a la cámara", isPresented: $showCameraDenied) {
            Button("Cancelar", role: .cancel) {}
            Button("AJUSTES") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        } message: {
            Text("Activa el permiso de cámara en Ajustes para añadir la foto de celebración.")
        }
    }
}

/// Fila de ajuste "Publicar ascensos en el feed" del Perfil (Preguntar /
/// Siempre / Nunca) — espejo de FeedPublishSettingRow de ProfileScreen.kt.
struct FeedPublishSettingRow: View {
    @State private var mode = FeedPublishPrefs.mode

    var body: some View {
        Menu {
            ForEach(FeedPublishMode.allCases, id: \.rawValue) { m in
                Button {
                    FeedPublishPrefs.mode = m
                    mode = m
                } label: {
                    if m == mode {
                        Label(m.label, systemImage: "checkmark")
                    } else {
                        Text(m.label)
                    }
                }
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "person.2").font(.system(size: 16))
                    .foregroundStyle(Cumbre.terra).frame(width: 24)
                Text("Publicar ascensos en el feed")
                    .font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Spacer()
                Text(mode.label)
                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink3)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
