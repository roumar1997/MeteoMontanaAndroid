import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// FLUJO DE CONTRIBUCIÓN (¿Falta algo?) — espejo de ProposeContributionFlow.kt:
// selector de tipo, formulario PARKING/SECTOR y confirmación.

struct ContributionTypePicker: View {
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("¿Falta algo en esta escuela? Propón una mejora y un admin la revisará (24-48 h).")
                    .font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // Mini-guía del flujo
                Text("Cómo funciona: elige qué añadir → toca el mapa para fijar la posición → rellena los datos → enviar. ¡Así de fácil!")
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Cumbre.terra.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))

                row("PIEDRA", "Una roca con sus vías. Podrás añadir fotos y dibujar las líneas.", "mountain.2.fill", enabled: true) { onPick("BOULDER") }
                row("SECTOR", "Una zona que agrupa piedras (ej: \"La Isla\"). Después asignarás piedras al sector.", "square.dashed", enabled: true) { onPick("SECTOR") }
                row("PARKING", "El punto de aparcamiento. Otros escaladores verán \"Cómo llegar\".", "car.fill", enabled: true) { onPick("PARKING") }
                row("CORREGIR", "¿Algo está mal colocado en el mapa? Tócalo y muévelo al sitio correcto.", "mappin.and.ellipse", enabled: true) { onPick("CORRECTION") }
                Spacer()
            }
            .padding(16)
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Proponer mejora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
    }
    private func row(_ t: String, _ sub: String, _ icon: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 18)).foregroundStyle(enabled ? Cumbre.terra : Cumbre.ink3).frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(t).font(Cumbre.mono(13, .bold)).tracking(0.6).foregroundStyle(enabled ? Cumbre.ink : Cumbre.ink3)
                    Text(enabled ? sub : "\(sub) · próximamente").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                Spacer()
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Cumbre.paper)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// Formulario de propuesta PARKING/SECTOR — espejo de ParkingFormDialog.kt.
/// Coords fijadas por el tap en el mapa. Envía POST /contributions y devuelve ok.
struct ContributionFormSheet: View {
    let type: String           // "PARKING" | "SECTOR"
    let schoolId: String
    let coord: CLLocationCoordinate2D
    let onDone: (Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var notes = ""
    @State private var sending = false
    @State private var sendError: String? = nil
    @State private var queued = false

    private var isSector: Bool { type == "SECTOR" }
    private var title: String { isSector ? "Nueva zona" : "Nuevo parking" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    field("NOMBRE (opcional)", $name, isSector ? "ej: Sector Bajo" : "ej: Parking de arriba")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }
                    field("NOTAS (opcional)", $notes, isSector ? "Descripción de la zona" : "Cómo es el acceso, etc.")
                    if let sendError {
                        Text(sendError).font(.system(size: 12)).foregroundStyle(Cumbre.bad)
                        // Cola offline: guarda la propuesta y el flusher la envía
                        // solo al recuperar cobertura (espejo de Android).
                        Button { Task { await saveOffline() } } label: {
                            Text("GUARDAR Y ENVIAR CON COBERTURA").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.ink)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                    Button { Task { await send() } } label: {
                        HStack { if sending { ProgressView().tint(.white) }
                            Text(sendError != nil ? "REINTENTAR" : NSLocalizedString("propose_submit", comment: "")).font(Cumbre.mono(13, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity).background(Cumbre.terra)
                    }.buttonStyle(.plain).disabled(sending).padding(.top, 4)
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button(NSLocalizedString("common_cancel", comment: "")) { dismiss(); onDone(false) }.foregroundStyle(Cumbre.ink3) } }
            .alert("Guardada en tu móvil", isPresented: $queued) {
                Button("CERRAR") { dismiss(); onDone(false) }
            } message: {
                Text("Se enviará automáticamente en cuanto haya cobertura. No tienes que hacer nada.")
            }
        }
    }

    private func send() async {
        sending = true
        let req = ContributionRequest(
            type: type,
            name: name.trimmingCharacters(in: .whitespaces).isEmpty ? nil : name,
            lat: coord.latitude, lon: coord.longitude,
            notes: notes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : notes,
            description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: nil, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok { dismiss(); onDone(true) }
        else { sendError = "No se pudo enviar. Revisa la conexión — tus datos siguen aquí." }
    }

    /// Encola la propuesta para que ContributionOutboxFlusher la envíe al
    /// recuperar cobertura. El JSON usa las MISMAS claves que ContributionRequest
    /// (lo decodifica kotlinx en el contenedor).
    private func saveOffline() async {
        var dict: [String: Any] = ["type": type, "lat": coord.latitude, "lon": coord.longitude]
        let nm = name.trimmingCharacters(in: .whitespaces)
        if !nm.isEmpty { dict["name"] = nm }
        let nt = notes.trimmingCharacters(in: .whitespaces)
        if !nt.isEmpty { dict["notes"] = nt }
        guard let d = try? JSONSerialization.data(withJSONObject: dict),
              let json = String(data: d, encoding: .utf8) else { return }
        try? await AppDependencies.shared.container.enqueueContribution(schoolId: schoolId, requestJson: json)
        queued = true
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).background(Cumbre.paper).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}

/// Confirmación tras enviar una propuesta — espejo de SuccessDialog.kt.
struct ContributionSuccessSheet: View {
    // Admin publica directo (auto-aprobado) → el mensaje lo refleja en vez de
    // decir "la revisaremos en 24-48 h".
    var isAdmin: Bool = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.seal.fill").font(.system(size: 56)).foregroundStyle(Cumbre.ok)
            Text(isAdmin ? "¡Publicado!" : "¡Propuesta enviada!").font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
            Text(isAdmin
                 ? "Ya está en el mapa para toda la comunidad."
                 : "La revisaremos en 24-48 h. Gracias por mejorar el mapa de la comunidad.")
                .font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.horizontal, 24)
            Button("CERRAR") { dismiss() }
                .font(Cumbre.mono(12, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                .padding(.vertical, 14).padding(.horizontal, 32)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1)).padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(32)
        .background(Cumbre.bg.ignoresSafeArea())
    }
}

/// Hoja con la info de un bloque tocado en el mapa (nombre, tipo, descripción,
/// vías y "CÓMO LLEGAR"). Espejo simplificado de BlockDetailDialog.kt.
