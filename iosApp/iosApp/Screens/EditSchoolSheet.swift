import SwiftUI
import Shared

/// Editar nombre/ubicación/región/estilo/roca de una escuela ya creada
/// (admin). Antes solo se podía mover el pin en el mapa — Álvaro, 2026-09-30:
/// "y si es una escuela? tambien quiero poder... el donde esta o el nombre".
struct EditSchoolSheet: View {
    let school: School
    var onDone: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var location: String
    @State private var region: String
    @State private var style: String
    @State private var rockType: String
    @State private var busy = false

    init(school: School, onDone: @escaping () -> Void = {}) {
        self.school = school
        self.onDone = onDone
        _name = State(initialValue: school.name)
        _location = State(initialValue: school.location ?? "")
        _region = State(initialValue: school.region ?? "")
        _style = State(initialValue: school.style ?? "")
        _rockType = State(initialValue: school.rockType ?? "")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("ESCUELA").font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.terra)
                    field("NOMBRE", $name, "Nombre")
                    field("DÓNDE ESTÁ", $location, "Ubicación (texto libre)")
                    field("REGIÓN", $region, "Región")
                    field("ESTILO", $style, "Boulder / Deportiva / ambos…")
                    field("TIPO DE ROCA", $rockType, "Caliza / granito…")
                    Text("La posición del pin se cambia desde GESTIONAR → mapa, no aquí.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    Button { Task { await save() } } label: {
                        HStack { if busy { ProgressView().tint(.white) }
                            Text("GUARDAR CAMBIOS").font(Cumbre.mono(12, .bold)).tracking(0.8) }
                        .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 13).background(Cumbre.terraFill)
                    }.buttonStyle(.plain).disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }.padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle(school.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) {
                Button(NSLocalizedString("common_close", comment: "")) { dismiss() }.foregroundStyle(Cumbre.ink3) } }
        }
    }

    private func save() async {
        busy = true
        _ = try? await AppDependencies.shared.container.editSchool.invoke(
            schoolId: school.id,
            name: name.trimmingCharacters(in: .whitespaces),
            location: location.trimmingCharacters(in: .whitespaces),
            region: region.trimmingCharacters(in: .whitespaces),
            style: style.trimmingCharacters(in: .whitespaces),
            rockType: rockType.trimmingCharacters(in: .whitespaces)
        )
        busy = false; dismiss(); onDone()
    }

    private func field(_ label: String, _ text: Binding<String>, _ ph: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).eyebrow()
            TextField(ph, text: text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        }
    }
}
