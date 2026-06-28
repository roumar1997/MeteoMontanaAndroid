import SwiftUI
import Shared
import PhotosUI

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
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var photoImage: UIImage? = nil
    @State private var photoUrl: String? = nil
    @State private var uploadingPhoto = false
    @State private var showSchoolPicker = false
    @State private var schoolPickerQuery = ""
    @State private var schoolPickerResults: [School] = []
    @State private var schoolName = ""

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
                        // Foto del grupo
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("FOTO DEL GRUPO (opcional)")
                            PhotosPicker(selection: $photoItem, matching: .images) {
                                ZStack {
                                    if let img = photoImage {
                                        Image(uiImage: img)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 140)
                                            .clipped()
                                            .cornerRadius(4)
                                    } else {
                                        RoundedRectangle(cornerRadius: 4)
                                            .fill(Cumbre.bg)
                                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2)))
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 140)
                                    }
                                    if uploadingPhoto {
                                        ProgressView().tint(Cumbre.terra)
                                    } else if photoImage == nil {
                                        VStack(spacing: 4) {
                                            Image(systemName: "camera.fill").font(.title2).foregroundColor(Cumbre.ink.opacity(0.4))
                                            Text("AÑADIR FOTO").font(.system(size: 10, weight: .bold, design: .monospaced))
                                                .tracking(1.8).foregroundColor(Cumbre.ink.opacity(0.4))
                                        }
                                    }
                                }
                            }
                            .onChange(of: photoItem) { item in
                                guard let item else { return }
                                Task {
                                    guard let data = try? await item.loadTransferable(type: Data.self),
                                          let uiImg = UIImage(data: data) else { return }
                                    photoImage = uiImg
                                    uploadingPhoto = true
                                    do {
                                        let url = try await StorageUploader.uploadMeetupPhoto(
                                            uiImg,
                                            tempId: "new_\(Int(Date().timeIntervalSince1970))"
                                        )
                                        photoUrl = url
                                    } catch { photoUrl = nil }
                                    uploadingPhoto = false
                                }
                            }
                        }

                        // Nombre
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("NOMBRE")
                            TextField("Ej. Quedar en Pedriza", text: $name)
                                .textFieldStyle(CumbreFieldStyle())
                        }

                        // Escuela
                        VStack(alignment: .leading, spacing: 6) {
                            FieldLabel("ESCUELA")
                            Button { showSchoolPicker = true } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: "building.2").foregroundColor(Cumbre.ink.opacity(0.5))
                                    Text(schoolName.isEmpty ? "Buscar escuela…" : schoolName)
                                        .foregroundStyle(schoolName.isEmpty ? Cumbre.ink.opacity(0.4) : Cumbre.ink)
                                        .font(.system(size: 15))
                                    Spacer()
                                    Image(systemName: "chevron.right").foregroundColor(Cumbre.ink.opacity(0.3))
                                }
                                .padding(10)
                                .background(Cumbre.bg)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                            }
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
                        if privacy == "WOMEN" {
                            Text("Solo mujeres. Asegúrate de tener género = Mujer en tu perfil (Perfil → Editar perfil).")
                                .font(.system(size: 12))
                                .foregroundStyle(Cumbre.ink.opacity(0.5))
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
                        description: nil,
                        discipline: discipline,
                        privacy: privacy,
                        memberLimit: {
                            guard !limitText.isEmpty, let n = Int32(limitText) else { return nil }
                            return KotlinInt(int: n)
                        }(),
                        photoUrl: photoUrl,
                        days: selectedDays.sorted()
                    )
                    Task {
                        do {
                            let meetup = try await uc.execute(req: req)
                            submitting = false
                            onCreated(meetup.id)
                        } catch {
                            let msg = error.localizedDescription
                            if msg.contains("GENDER_REQUIRED") {
                                createError = "Solo puedes crear quedadas NO MIXTO si tienes género Mujer en tu perfil."
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
            .sheet(isPresented: $showSchoolPicker) {
                SchoolPickerSheet(
                    query: $schoolPickerQuery,
                    results: $schoolPickerResults,
                    onQueryChange: { q in
                        guard q.count >= 2,
                              let uc = AppDependencies.shared.container.searchSchools else { return }
                        Task {
                            if let results = try? await uc.invoke(query: q, limit: 8) {
                                schoolPickerResults = results
                            }
                        }
                    },
                    onSelect: { school in
                        schoolId = school.id
                        schoolName = school.name
                        showSchoolPicker = false
                        schoolPickerQuery = ""
                        schoolPickerResults = []
                    }
                )
            }
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
    let options: [(String, String)] = [("OPEN","Abierta"),("FOLLOWERS","Solo seguidores"),("WOMEN","No mixto")]
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

private struct SchoolPickerSheet: View {
    @Binding var query: String
    @Binding var results: [School]
    let onQueryChange: (String) -> Void
    let onSelect: (School) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                TextField("Ej. Zarzalejo, Pedriza…", text: $query)
                    .padding(10)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    .padding(12)
                    .onChange(of: query) { _, q in onQueryChange(q) }
                List(results, id: \.id) { school in
                    Button {
                        onSelect(school)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(school.name).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                            if let loc = school.location {
                                Text(loc).font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle("Buscar escuela")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
    }
}
