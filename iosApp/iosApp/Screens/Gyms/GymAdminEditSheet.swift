import SwiftUI
import Shared

/// Editar una ficha de rocódromo (admin, solo iOS): `PUT /api/admin/gyms/{id}`. Solo se envía lo que ha cambiado
/// (el servidor no toca lo que va a nulo). Horario: una línea por tramo, «DÍAS APERTURA-CIERRE», p. ej.
/// «MON-FRI 10:00-23:00» o «SAT,SUN 10:00-21:00».
struct GymAdminEditSheet: View {
    let gym: Gym
    let onSave: (GymAdminEdit) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var kind: GymKind
    @State private var discipline: GymDiscipline
    @State private var verification: GymVerification
    @State private var hours: String
    @State private var single: String
    @State private var pass: String
    @State private var passLabel: String
    @State private var reviewed: String
    @State private var website: String
    @State private var instagram: String
    @State private var hidden: Bool
    @State private var closed: Bool
    @State private var busy = false
    @State private var error: String?

    init(gym: Gym, onSave: @escaping (GymAdminEdit) async -> Bool) {
        self.gym = gym
        self.onSave = onSave
        _kind = State(initialValue: gym.kind)
        _discipline = State(initialValue: gym.discipline)
        _verification = State(initialValue: gym.verification)
        _hours = State(initialValue: Self.hoursText(gym))
        _single = State(initialValue: gym.priceSingleEur.map { Self.number($0.doubleValue) } ?? "")
        _pass = State(initialValue: gym.pricePassEur.map { Self.number($0.doubleValue) } ?? "")
        _passLabel = State(initialValue: gym.pricePassLabel ?? "")
        _reviewed = State(initialValue: gym.priceUpdatedAt ?? "")
        _website = State(initialValue: gym.website ?? "")
        _instagram = State(initialValue: gym.instagram ?? "")
        _hidden = State(initialValue: gym.hidden)
        _closed = State(initialValue: gym.closed)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Tipo y modalidad") {
                    Picker("Tipo", selection: $kind) {
                        Text("Comercial").tag(GymKind.commercial)
                        Text("Municipal").tag(GymKind.municipal)
                        Text("Muro de calle").tag(GymKind.free)
                    }
                    Picker("Modalidad", selection: $discipline) {
                        Text("Sin confirmar").tag(GymDiscipline.unknown)
                        Text("Bloque").tag(GymDiscipline.boulder)
                        Text("Cuerda").tag(GymDiscipline.rope)
                        Text("Bloque + cuerda").tag(GymDiscipline.both)
                    }
                    Picker("Verificación", selection: $verification) {
                        Text("Sin confirmar").tag(GymVerification.unconfirmed)
                        Text("Comunidad").tag(GymVerification.community)
                        Text("Confirmado").tag(GymVerification.verified)
                    }
                }
                Section("Horario (una línea por tramo)") {
                    TextEditor(text: $hours).font(Cumbre.mono(13)).frame(minHeight: 90)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                }
                Section("Precio (€) y fecha de revisión") {
                    TextField("Entrada suelta", text: $single).keyboardType(.decimalPad)
                    TextField("Bono", text: $pass).keyboardType(.decimalPad)
                    TextField("Nombre del bono (Bono 10…)", text: $passLabel)
                    TextField("Revisado el (AAAA-MM-DD)", text: $reviewed).autocorrectionDisabled()
                }
                Section("Enlaces") {
                    TextField("Web", text: $website).textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("Instagram", text: $instagram).textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                Section {
                    Toggle(L("gym_admin_closed"), isOn: $closed)
                    Toggle("Ocultar de la app", isOn: $hidden)
                }
                if let error { Section { Text(error).foregroundStyle(Cumbre.bad) } }
            }
            .navigationTitle(gym.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "Guardando…" : "Guardar") { save() }.disabled(busy)
                }
            }
        }
    }

    // MARK: - Guardar

    private enum HoursResult { case unchanged, invalid, slots([OpeningSlot]) }

    private func save() {
        error = nil
        let slots: [OpeningSlot]?
        switch hoursResult() {
        case .invalid:
            error = "Horario no válido: usa «DÍAS APERTURA-CIERRE», p. ej. MON-FRI 10:00-23:00 (no se puede dejar vacío)."
            return
        case .unchanged: slots = nil
        case .slots(let parsed): slots = parsed
        }
        let singleValue = Self.parse(single)
        let passValue = Self.parse(pass)
        let priceChanged = singleValue != gym.priceSingleEur?.doubleValue || passValue != gym.pricePassEur?.doubleValue
            || passLabel != (gym.pricePassLabel ?? "")
        let review = reviewed.trimmingCharacters(in: .whitespaces)
        let reviewChanged = review != (gym.priceUpdatedAt ?? "")
        if priceChanged && review.isEmpty {
            error = "Pon la fecha de revisión del precio (AAAA-MM-DD)."
            return
        }
        let edit = makeEdit(slots: slots, priceChanged: priceChanged, sendReview: (priceChanged || reviewChanged) && !review.isEmpty,
                            review: review, singleValue: singleValue, passValue: passValue)
        busy = true
        Task {
            let ok = await onSave(edit)
            busy = false
            if ok { dismiss() } else { error = "No se pudo guardar. Revisa los datos e inténtalo de nuevo." }
        }
    }

    private func makeEdit(slots: [OpeningSlot]?, priceChanged: Bool, sendReview: Bool, review: String,
                          singleValue: Double?, passValue: Double?) -> GymAdminEdit {
        GymAdminEdit(
            kind: Self.diff(kind, gym.kind),
            discipline: Self.diff(discipline, gym.discipline),
            verification: Self.diff(verification, gym.verification),
            openingHours: slots,
            priceSingleEur: priceChanged ? singleValue.map { KotlinDouble(value: $0) } : nil,
            pricePassEur: priceChanged ? passValue.map { KotlinDouble(value: $0) } : nil,
            pricePassLabel: priceChanged && !passLabel.isEmpty ? passLabel : nil,
            priceUpdatedAt: sendReview ? review : nil,
            website: changedText(website, gym.website),
            instagram: changedText(instagram, gym.instagram),
            hidden: Self.diff(hidden, gym.hidden).map { KotlinBoolean(bool: $0) },
            closed: Self.diff(closed, gym.closed).map { KotlinBoolean(bool: $0) })
    }

    private static func diff<T: Equatable>(_ new: T, _ old: T) -> T? { new == old ? nil : new }

    private func changedText(_ new: String, _ old: String?) -> String? {
        let trimmed = new.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty || trimmed == (old ?? "") ? nil : trimmed
    }

    /// Sin cambios no se envía; un horario vacío o mal escrito no se puede enviar (no se puede borrar desde aquí).
    private func hoursResult() -> HoursResult {
        let text = hours.trimmingCharacters(in: .whitespacesAndNewlines)
        if text == Self.hoursText(gym).trimmingCharacters(in: .whitespacesAndNewlines) { return .unchanged }
        var out: [OpeningSlot] = []
        for line in text.split(separator: "\n") {
            let parts = line.split(separator: " ", omittingEmptySubsequences: true)
            let times = parts.count == 2 ? parts[1].split(separator: "-") : []
            guard times.count == 2, Self.isTime(String(times[0])), Self.isTime(String(times[1])) else { return .invalid }
            out.append(OpeningSlot(days: String(parts[0]).uppercased(), open: String(times[0]), close: String(times[1])))
        }
        return out.isEmpty ? .invalid : .slots(out)
    }

    private static func hoursText(_ gym: Gym) -> String {
        (gym.openingHours ?? []).map { "\($0.days) \($0.open)-\($0.close)" }.joined(separator: "\n")
    }

    private static func isTime(_ t: String) -> Bool {
        t.range(of: "^\\d{1,2}:\\d{2}$", options: .regularExpression) != nil
    }

    private static func number(_ v: Double) -> String { v == v.rounded() ? String(Int(v)) : String(v) }

    private static func parse(_ text: String) -> Double? {
        Double(text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: "."))
    }
}
