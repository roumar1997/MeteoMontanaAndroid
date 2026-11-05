import SwiftUI
import UIKit
import Shared

// Hoja de ASCENSO (al marcar una vía como hecha) — Álvaro, 2026-10-31.
//
// Antes: una hoja alta cuyo botón grande era "PUBLICAR EN EL FEED" y un ajuste
// Preguntar/Siempre/Nunca que publicaba en silencio. Ahora: hoja corta
// (cuándo, estilo, grado) donde compartir en Comunidad es un interruptor que
// recuerda lo último que dejaste, y el botón dice lo que va a pasar.

/// Lo que recuerda el interruptor "Compartir en Comunidad" entre una hoja y la
/// siguiente. Sustituye al ajuste antiguo Preguntar/Siempre/Nunca: quien tenía
/// "Siempre" sigue empezando con el interruptor encendido; el resto, apagado.
enum FeedSharePref {
    private static let key = "feed_share_default"
    private static let legacyKey = "feed_publish_mode"

    static var enabled: Bool {
        get {
            if let saved = UserDefaults.standard.object(forKey: key) as? Bool { return saved }
            return UserDefaults.standard.string(forKey: legacyKey) == "always"
        }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}

/// Tick pendiente de confirmar (hoja de ascenso).
struct PendingFeedTick: Identifiable {
    let id = UUID()
    let line: BlockLine
    let index: Int
    let wasProject: Bool
}

/// Todo lo que el usuario dejó elegido al guardar la hoja.
struct AscentChoice {
    /// nil = hoy. La fecha se guarda en el diario SIEMPRE, publiques o no.
    var sessionDate: String?
    var aVista: Bool
    var alFlash: Bool
    /// Voto de grado (nil = no votar). Solo se manda después de guardar la vía en el diario.
    var gradeVote: String?
    var publish: Bool
    var caption: String?
    var photo: UIImage?
}

/// Hoja de ascenso (estilo Cumbre): vía + grado, CUÁNDO, ESTILO, ¿QUÉ GRADO LE DAS?,
/// interruptor de Comunidad y un único botón que dice qué hará. Cerrar la hoja = no marcar nada.
struct AscentSheet: View {
    /// "vía · grado": clave del búfer de rescate de la foto de celebración.
    let lineLabel: String
    let lineName: String
    let setterGrade: String?
    let wasProject: Bool
    @ObservedObject var votes: CommunityVoteStore
    let onSave: (AscentChoice) -> Void

    @State private var sessionDate: String? = nil
    // Estilo de ascensión: independientes entre sí, se puede marcar 1, el
    // otro, los dos o ninguno (Rodrigo, 2026-08-21).
    @State private var aVista = false
    @State private var alFlash = false
    // Voto de grado elegido, y el que espera a que se diga la altura (solo la primera vez).
    @State private var vote: String? = nil
    @State private var pendingVote: String? = nil
    @State private var showAllGrades = false
    @State private var choosingHeight = false
    @State private var share = FeedSharePref.enabled
    // Descripción opcional del autor (viaja como "caption", max 500).
    @State private var caption = ""
    @State private var showDatePicker = false
    @State private var pickedDate = Date()
    // Foto de celebración: hecha en el momento con la cámara del sistema. Se
    // guarda en un ObservableObject (no @State) porque la cámara se presenta
    // por UIKit y un @State captado en el callback no refrescaba la hoja.
    @ObservedObject private var photoStore = CapturedPhotoStore.shared
    @State private var showCameraDenied = false

    private static let suffixes = ["a", "a+", "b", "b+", "c", "c+"]
    /// Todos los grados en orden (4a, 4a+, ... 8c+).
    private static let allGrades = ["4", "5", "6", "7", "8"].flatMap { n in AscentSheet.suffixes.map { n + $0 } }

    /// Dos por abajo, el del equipador y dos por arriba: lo que se vota casi siempre.
    /// Vacío si el grado de la vía no es uno de la lista (doble, fuera de rango…).
    private var quickGrades: [String] {
        guard let g = setterGrade?.trimmingCharacters(in: .whitespaces).lowercased(),
              let i = Self.allGrades.firstIndex(of: g) else { return [] }
        return [i - 2, i - 1, i, i + 1, i + 2].compactMap { Self.allGrades.indices.contains($0) ? Self.allGrades[$0] : nil }
    }

    private func requestCamera() {
        CameraAccess.request { granted in
            guard granted else { showCameraDenied = true; return }
            // Presentación por UIKit (sin el parpadeo del fullScreenCover dentro
            // de la hoja). La foto capturada va al ObservableObject, que sí
            // refresca la miniatura. El lineLabel es la clave del búfer de
            // rescate (si la hoja se recrea, la recuperada es de ESTA vía).
            presentSystemCamera(context: lineLabel) { _ in }   // el store compartido ya la recibe
        }
    }

    /// Elegir de la galería (PHPicker: sin permiso, mismo patrón UIKit).
    private func requestGallery() {
        presentSystemPhotoPicker(context: lineLabel) { _ in }  // idem
    }

    /// Pulsar un grado: si aún no se ha dicho la altura se pide primero (una sola vez en la vida); pulsar el mismo otra vez lo quita.
    private func tapGrade(_ g: String) {
        if votes.myHeight == nil { pendingVote = nextGradeVote(tapping: g, current: pendingVote); return }
        vote = nextGradeVote(tapping: g, current: vote)
    }

    private func save() {
        let text = caption.trimmingCharacters(in: .whitespacesAndNewlines)
        let photo = share ? photoStore.image(for: lineLabel) : nil
        CapturedPhotoStore.forget()   // consumida (o descartada): no re-adoptar después
        FeedSharePref.enabled = share
        onSave(AscentChoice(sessionDate: sessionDate, aVista: aVista, alFlash: alFlash,
                            gradeVote: vote, publish: share,
                            caption: share && !text.isEmpty ? text : nil, photo: photo))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                sectionLabel(L("CUÁNDO"))
                dateSegment
                sectionLabel(L("ESTILO · OPCIONAL"))
                HStack(spacing: 8) {
                    styleTile(icon: "eye", title: L("A vista"), subtitle: L("Primer intento, sin ver la beta"), isOn: Binding(get: { aVista }, set: { aVista = $0; if $0 { alFlash = false } }))
                    styleTile(icon: "bolt", title: L("Al flash"), subtitle: L("Primer intento, con beta"), isOn: Binding(get: { alFlash }, set: { alFlash = $0; if $0 { aVista = false } }))
                }
                .fixedSize(horizontal: false, vertical: true)
                // TU ALTURA: con la que cuenta este encadene (+1,70 / −1,70). Si ya la dijiste, una línea; si no, aquí mismo.
                sectionLabel(L("TU ALTURA"))
                heightRow
                sectionLabel(L("¿QUÉ GRADO LE DAS? · OPCIONAL"))
                gradeSection
                shareRow.padding(.top, 16)
                if share { shareDetails }
                Button(action: save) {
                    Text(share ? L("GUARDAR Y PUBLICAR") : L("GUARDAR EN MI DIARIO"))
                        .font(Cumbre.mono(11, .bold)).tracking(1.4)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(Cumbre.terraFill)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .padding(.top, 16)
            }
            .padding(.horizontal, 16).padding(.top, 22).padding(.bottom, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.fraction(0.86), .large])
        .presentationDragIndicator(.visible)
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
        .sheet(isPresented: $showDatePicker) {
            VStack(spacing: 12) {
                DatePicker("", selection: $pickedDate, in: ...Date(),
                           displayedComponents: .date)
                    .datePickerStyle(.graphical)
                Button("OK") {
                    let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
                    sessionDate = df.string(from: pickedDate)
                    showDatePicker = false
                }
                .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
            }
            .padding(16)
            .presentationDetents([.medium])
        }
    }

    // MARK: - Secciones

    private var header: some View {
        HStack(alignment: .bottom, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(wasProject ? L("PROYECTO CONSEGUIDO") : L("HECHO"))
                    .font(Cumbre.mono(10, .bold)).tracking(1.8)
                    .foregroundStyle(Cumbre.terra)
                Text(lineName)
                    .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            if let g = setterGrade, !g.isEmpty {
                let dark = GradeColor.chipStyle(g).dark
                Text(g).font(Cumbre.mono(14, .bold))
                    .foregroundStyle(dark ? Color.black : Color.white)
                    .padding(.horizontal, 13).padding(.vertical, 6)
                    .background(Capsule().fill(GradeColor.chip(g)))
                    .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(Cumbre.mono(10, .bold)).tracking(1.4)
            .foregroundStyle(Cumbre.ink3)
            .padding(.top, 18).padding(.bottom, 7)
    }

    /// Hoy | Ayer | Otra fecha…: la fecha se guarda en el diario SIEMPRE, publiques o no.
    private var dateSegment: some View {
        let yesterday = Self.iso(daysAgo: 1)
        let custom = sessionDate.flatMap { $0 == yesterday ? nil : $0 }
        return HStack(spacing: 3) {
            segment(L("Hoy"), on: sessionDate == nil) { sessionDate = nil }
            segment(L("Ayer"), on: sessionDate == yesterday) { sessionDate = yesterday }
            segment(custom.map { Self.shortDate($0) } ?? L("Otra fecha…"), on: custom != nil) { showDatePicker = true }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 11).fill(Cumbre.paper))
    }

    private func segment(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(on ? Cumbre.ink : Cumbre.ink2)
                .lineLimit(1).minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity).padding(.vertical, 9).padding(.horizontal, 4)
                .background(RoundedRectangle(cornerRadius: 9).fill(on ? Cumbre.bg : Color.clear))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(on ? Cumbre.rule : Color.clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func styleTile(icon: String, title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        let on = isOn.wrappedValue
        return Button { isOn.wrappedValue.toggle() } label: {
            VStack(alignment: .leading, spacing: 2) {
                Image(systemName: icon).font(.system(size: 18))
                Text(title).font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink).padding(.top, 4)
                Text(subtitle).font(.system(size: 11.5)).foregroundStyle(Cumbre.ink3)
                    .multilineTextAlignment(.leading)
            }
            .foregroundStyle(on ? Cumbre.terra : Cumbre.ink2)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(on ? Cumbre.terra.opacity(0.11) : Cumbre.paper))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Grado

    /// «Mides más de 1,70 · CAMBIAR», o las tres opciones si aún no la has dicho (o al pulsar CAMBIAR).
    @ViewBuilder
    private var heightRow: some View {
        if votes.myHeight == nil || choosingHeight {
            VStack(alignment: .leading, spacing: 6) {
                if votes.myHeight == nil {
                    Text(L("¿Quieres poner tu altura?")).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                }
                HeightChoiceView(selected: votes.myHeight) { group in
                    choosingHeight = false
                    Task {
                        await votes.setMyHeight(group)
                        if votes.myHeight != nil, let p = pendingVote { vote = p; pendingVote = nil }
                    }
                }
                Text(L("Ayuda a la comunidad: quien mida parecido a ti sabrá cuánta gente de su altura encadena cada vía. Se guarda en tu perfil, no tienes que ponerla allí."))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
        } else {
            HStack {
                Text(votes.myHeight == .tall ? L("Mides más de 1,70")
                     : votes.myHeight == .short ? L("Mides menos de 1,70") : L("Prefieres no decir tu altura"))
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Spacer()
                Button { choosingHeight = true } label: {
                    Text(L("CAMBIAR")).font(Cumbre.mono(10, .bold)).tracking(1.2).foregroundStyle(Cumbre.terra)
                }.buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var gradeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !quickGrades.isEmpty {
                HStack(spacing: 5) { ForEach(quickGrades, id: \.self) { gradeButton($0) } }
            }
            if showAllGrades || quickGrades.isEmpty {
                ForEach(["4", "5", "6", "7", "8"], id: \.self) { n in
                    HStack(spacing: 5) { ForEach(Self.suffixes, id: \.self) { gradeButton(n + $0) } }
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(quickGrades.isEmpty
                     ? L("Toca solo si quieres votar un grado. Si dudas entre dos, pulsa también el de al lado.")
                     : L("Está en %@. Toca solo si te parece otro grado. Si dudas entre dos, pulsa también el de al lado.", setterGrade ?? ""))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    .fixedSize(horizontal: false, vertical: true)
                if !quickGrades.isEmpty {
                    Button { showAllGrades.toggle() } label: {
                        Text(showAllGrades ? L("MENOS") : L("OTRO GRADO…"))
                            .font(Cumbre.mono(10.5, .bold)).tracking(1.2).foregroundStyle(Cumbre.terra)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// Cada grado con SU color; el elegido va relleno.
    private func gradeButton(_ g: String) -> some View {
        let selected = gradeParts(vote ?? pendingVote).contains(g)
        let style = GradeColor.chipStyle(g)
        let accent = style.dark ? Cumbre.ink : style.stroke
        return Button { tapGrade(g) } label: {
            Text(g).font(Cumbre.mono(12, .bold))
                .foregroundStyle(selected ? (style.dark ? Color.black : Color.white) : accent)
                .frame(maxWidth: .infinity).padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 8).fill(selected ? GradeColor.chip(g) : Cumbre.paper))
                .overlay(RoundedRectangle(cornerRadius: 8)
                    .stroke(selected && !style.dark ? Color.clear : accent, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Comunidad

    private var shareRow: some View {
        Toggle(isOn: $share) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Compartir en Comunidad").font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                Text("Lo verá quien te sigue en el feed").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
            }
        }
        .toggleStyle(SwitchToggleStyle(tint: Cumbre.terra))
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Cumbre.paper))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
    }

    /// Descripción y foto de celebración: solo si vas a publicar.
    @ViewBuilder
    private var shareDetails: some View {
        TextField("Añade una descripción (opcional)", text: $caption, axis: .vertical)
            .lineLimit(2...4)
            .font(.system(size: 14))
            .foregroundStyle(Cumbre.ink)
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(Cumbre.paper)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.top, 10)
            .onChange(of: caption) { _, new in
                if new.count > 500 { caption = String(new.prefix(500)) }
            }
        // Autocompletado de @menciones al escribir la descripción.
        MentionSuggestionsView(text: $caption)

        if let photo = photoStore.image(for: lineLabel) {
            HStack(spacing: 12) {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: photo)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 88, height: 110)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Cumbre.rule, lineWidth: 1))
                    // ✕ quita la foto (y olvida el búfer: quitarla es
                    // deliberado, que no reaparezca si la hoja se recrea).
                    Button {
                        CapturedPhotoStore.forget()
                    } label: {
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
                VStack(alignment: .leading, spacing: 4) {
                    Button(action: requestCamera) {
                        Text("REPETIR FOTO")
                            .font(Cumbre.mono(10, .bold)).tracking(1.8)
                            .foregroundStyle(Cumbre.terra)
                            .padding(8)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button(action: requestGallery) {
                        Text("GALERÍA")
                            .font(Cumbre.mono(10, .bold)).tracking(1.8)
                            .foregroundStyle(Cumbre.terra)
                            .padding(8)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(.top, 12)
        } else {
            // Dos accesos: hacer la foto ahora o elegirla de la galería
            // (para publicar después sin estar con el móvil en el momento).
            HStack(spacing: 8) {
                photoButton(icon: "camera", title: "Hacer foto", action: requestCamera)
                photoButton(icon: "photo.on.rectangle", title: "Galería", action: requestGallery)
            }
            .padding(.top, 10)
        }
    }

    private func photoButton(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundStyle(Cumbre.terra)
                Text(LocalizedStringKey(title))
                    .font(.system(size: 14))
                    .foregroundStyle(Cumbre.ink2)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

extension AscentSheet {
    static func iso(daysAgo: Int) -> String {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Calendar.current.date(byAdding: .day, value: -daysAgo, to: Date())!)
    }

    static func shortDate(_ iso: String) -> String {
        let parts = iso.split(separator: "-")
        guard parts.count == 3, let m = Int(parts[1]), let d = Int(parts[2]) else { return iso }
        let months = CalendarLabels.monthsShort().map { $0.uppercased() }
        return "\(d) \(months[m - 1])"
    }
}
