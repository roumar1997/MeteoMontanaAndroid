import SwiftUI
import Shared

struct CreateMeetupView: View {
    let onCreated: (String) -> Void   // meetupId

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var schoolId = ""
    @State private var selectedDays = Set<String>()
    @State private var privacy = "OPEN"
    @State private var discipline: String? = nil
    @State private var limitText = ""
    @State private var submitting = false
    @State private var createError: String?

    private let createMeetup = AppDependencies.shared.container.createMeetup

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !schoolId.trimmingCharacters(in: .whitespaces).isEmpty &&
        !selectedDays.isEmpty && !submitting
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Toolbar
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").foregroundColor(Cumbre.ink)
                    }
                    Text("Nueva quedada").font(.headline).padding(.leading, 8)
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(Cumbre.bg)
                .overlay(Divider(), alignment: .bottom)

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // Nombre
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("NOMBRE")
                            TextField("Ej. Quedar en Pedriza", text: $name)
                                .textFieldStyle(CumbreFieldStyle())
                        }

                        // Escuela
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("ID DE ESCUELA")
                            TextField("ID de la escuela del backend", text: $schoolId)
                                .textFieldStyle(CumbreFieldStyle())
                                .autocapitalization(.none)
                        }

                        // Días
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("DÍAS (elige uno o varios)")
                            DayPickerView(selected: $selectedDays)
                        }

                        // Privacidad
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("PRIVACIDAD")
                            PrivacyPickerView(selected: $privacy)
                        }

                        // Disciplina
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("DISCIPLINA (opcional)")
                            DisciplinePickerView(selected: $discipline)
                        }

                        // Límite
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("LÍMITE DE PARTICIPANTES (opcional)")
                            TextField("Sin límite", text: $limitText)
                                .keyboardType(.numberPad)
                                .textFieldStyle(CumbreFieldStyle())
                        }

                        // Error
                        if let err = createError {
                            Text(err).font(.caption).foregroundColor(.red)
                        }
                    }
                    .padding(16)
                }

                // Botón
                Divider()
                Button {
                    guard canSubmit, let uc = createMeetup else { return }
                    submitting = true
                    createError = nil
                    let req = CreateMeetupRequest(
                        schoolId: schoolId.trimmingCharacters(in: .whitespaces),
                        name: name.trimmingCharacters(in: .whitespaces),
                        discipline: discipline,
                        privacy: privacy,
                        memberLimit: {
                            guard !limitText.isEmpty, let n = Int32(limitText) else { return nil }
                            return KotlinInt(int: n)
                        }(),
                        photoUrl: nil,
                        days: selectedDays.sorted()
                    )
                    Task {
                        do {
                            let meetup = try await uc.execute(req: req)
                            onCreated(meetup.id)
                        } catch {
                            let msg = error.localizedDescription
                            if msg.contains("GENDER_REQUIRED") {
                                createError = "Solo puedes crear quedadas SOLO MUJERES si tienes género Mujer en tu perfil."
                            } else {
                                createError = msg
                            }
                            submitting = false
                        }
                    }
                } label: {
                    HStack {
                        if submitting { ProgressView().scaleEffect(0.8).tint(.white) }
                        else { Text("CREAR QUEDADA").fontWeight(.bold) }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Cumbre.terra)
                .disabled(!canSubmit)
                .padding(16)
                .background(Cumbre.bg)
            }
            .navigationBarHidden(true)
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────

private struct FieldLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .tracking(1.8)
            .foregroundColor(Cumbre.ink.opacity(0.5))
    }
}

private struct DayPickerView: View {
    @Binding var selected: Set<String>
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(nextNDays(14), id: \.iso) { day in
                    let on = selected.contains(day.iso)
                    Button {
                        if on { selected.remove(day.iso) } else { selected.insert(day.iso) }
                    } label: {
                        VStack(spacing: 2) {
                            Text(day.dow).font(.system(size: 10, weight: .bold))
                            Text(day.short).font(.system(size: 10))
                        }
                        .padding(.horizontal, 8).padding(.vertical, 5)
                        .background(on ? Cumbre.terra.opacity(0.15) : Cumbre.bg)
                        .foregroundColor(on ? Cumbre.terra : Cumbre.ink.opacity(0.6))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(on ? Cumbre.terra : Cumbre.ink.opacity(0.2), lineWidth: 1))
                        .cornerRadius(4)
                    }
                }
            }
        }
    }
}

private struct PrivacyPickerView: View {
    @Binding var selected: String
    let options: [(String, String)] = [("OPEN","Abierta"),("FOLLOWERS","Solo seguidores"),("WOMEN","Solo mujeres")]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(options, id: \.0) { (key, label) in
                    ToggleChipView(label: label, selected: selected == key) { selected = key }
                }
            }
        }
    }
}

private struct DisciplinePickerView: View {
    @Binding var selected: String?
    let options: [(String?, String)] = [(nil,"Cualquiera"),("BOULDER","Bloque"),("ROUTE","Vía"),("BOTH","Ambas")]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(0..<options.count, id: \.self) { i in
                    let (key, label) = options[i]
                    ToggleChipView(label: label, selected: selected == key) { selected = key }
                }
            }
        }
    }
}

private struct ToggleChipView: View {
    let label: String; let selected: Bool; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if selected { Image(systemName: "checkmark").font(.caption2) }
                Text(label).font(.system(size: 11))
            }
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(selected ? Cumbre.terra.opacity(0.15) : Cumbre.bg)
            .foregroundColor(selected ? Cumbre.terra : Cumbre.ink.opacity(0.6))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(selected ? Cumbre.terra : Cumbre.ink.opacity(0.2), lineWidth: 1))
            .cornerRadius(4)
        }
    }
}

private struct CumbreFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<_Label>) -> some View {
        configuration
            .padding(10)
            .background(Cumbre.bg)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
            .cornerRadius(4)
    }
}

// ── Day generation (mirrors Android nextNDays) ─────────────────────────────

private struct DayInfo { let dow: String; let short: String; let iso: String }

private func nextNDays(_ n: Int) -> [DayInfo] {
    let dowNames = ["DOM","LUN","MAR","MIÉ","JUE","VIE","SÁB"]
    var result: [DayInfo] = []
    let cal = Calendar.current
    let today = cal.startOfDay(for: Date())
    for i in 0..<n {
        guard let date = cal.date(byAdding: .day, value: i, to: today) else { continue }
        let comps = cal.dateComponents([.year, .month, .day, .weekday], from: date)
        let y  = comps.year ?? 0
        let mo = comps.month ?? 0
        let d  = comps.day ?? 0
        let wd = (comps.weekday ?? 1) - 1
        let iso   = String(format: "%04d-%02d-%02d", y, mo, d)
        let short = String(format: "%02d/%02d", d, mo)
        result.append(DayInfo(dow: dowNames[wd], short: short, iso: iso))
    }
    return result
}
