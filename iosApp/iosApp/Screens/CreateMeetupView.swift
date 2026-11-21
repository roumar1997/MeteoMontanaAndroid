import SwiftUI
import Shared
import PhotosUI

extension MeetupDraft {
    /// Copia con cambios (Swift no ve los parámetros por defecto del `copy` de Kotlin).
    func with(name: String? = nil, description: String?? = nil, privacy: String? = nil, photoUrl: String?? = nil,
              schoolId: String? = nil, gymId: KotlinLong?? = nil, placeName: String? = nil) -> MeetupDraft {
        doCopy(placeIsGym: placeIsGym, schoolId: schoolId ?? self.schoolId, gymId: gymId ?? self.gymId,
               placeName: placeName ?? self.placeName, days: days, discipline: discipline, partners: partners,
               gradeMin: gradeMin, gradeMax: gradeMax, gear: gear, privacy: privacy ?? self.privacy,
               memberLimit: memberLimit, photoUrl: photoUrl ?? self.photoUrl, name: name ?? self.name,
               description: description ?? self.description_)
    }

    static func empty() -> MeetupDraft {
        MeetupDraft(placeIsGym: false, schoolId: "", gymId: nil, placeName: "", days: [], discipline: nil,
                    partners: MeetupDraft.Companion.shared.DEFAULT_PARTNERS, gradeMin: nil, gradeMax: nil, gear: [:], privacy: "OPEN",
                    memberLimit: nil, photoUrl: nil, name: "", description: nil)
    }
}

/// Nueva quedada en 3 pasos (maqueta A): 1 dónde y cuándo, 2 qué y con quién, 3 material y detalles. Atrás conserva
/// lo elegido. La lógica (qué falta, el grado, el material, la petición) vive en `MeetupDraft` de shared.
/// Espejo de `CreateMeetupScreen.kt`.
struct CreateMeetupView: View {
    let onCreated: (String) -> Void
    /// Lo que trae el asistente: el lugar (y el día) ya puestos.
    var prefill: AssistantViewModel.MeetupRequest?
    /// Desde la ficha de un rocódromo: el lugar va fijado y solo se pregunta cuándo.
    var gym: GymPlace?

    @Environment(\.dismiss) private var dismiss
    @State private var draft = MeetupDraft.empty()
    @State private var step = 1
    @State private var placeLocked = false
    @State private var prefillApplied = false
    @State private var submitting = false
    @State private var createError: String?
    @State private var showGenderGate = false
    @State private var uploadingPhoto = false
    @State private var dayScores: [String: Int] = [:]
    @ObservedObject private var featureFlags = FeatureFlagsModel.shared
    @State private var paused: FeatureNotice?

    private let createMeetup = AppDependencies.shared.container.createMeetup
    private let getRangeScores = AppDependencies.shared.container.getRangeScores

    init(onCreated: @escaping (String) -> Void, prefill: AssistantViewModel.MeetupRequest? = nil, gym: GymPlace? = nil) {
        self.onCreated = onCreated
        self.prefill = prefill
        self.gym = gym
    }

    private var pausedNotice: FeatureNotice? {
        if !featureFlags.isEnabled(FeatureKey.shared.MEETUP_CREATE) {
            return FeatureNotice(message: featureFlags.message(FeatureKey.shared.MEETUP_CREATE))
        }
        return paused
    }

    private var defaultName: String {
        L("gym_meetup_default_name", draft.placeName.isEmpty ? L("create_meetup_title") : draft.placeName)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button { if step > 1 { step -= 1 } else { dismiss() } } label: {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain).accessibilityLabel(L("common_back"))
                Text(L("create_meetup_title")).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            if let notice = pausedNotice {
                FeatureOffNotice(message: notice.message).padding(16)
                Spacer()
            } else {
                WizardProgress(step: step)
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        switch step {
                        case 1: MeetupPlaceStep(draft: $draft, dayScores: dayScores, placeLocked: placeLocked)
                        case 2: MeetupDetailsStep(draft: $draft)
                        default: MeetupFinalStep(draft: $draft, uploadingPhoto: $uploadingPhoto, defaultName: defaultName)
                        }
                        if let createError { Text(createError).font(.system(size: 12)).foregroundStyle(Cumbre.bad) }
                    }
                    .padding(.horizontal, 20).padding(.vertical, 10)
                }
                .scrollDismissesKeyboard(.interactively)
                Divider().overlay(Cumbre.rule)
                HStack(spacing: 10) {
                    if step > 1 {
                        GymActionPill(text: L("mw_back")) { step -= 1 }.frame(maxWidth: 140)
                    }
                    if step < 3 {
                        GymActionPill(text: L("mw_next"), primary: true, enabled: draft.canAdvance(step: Int32(step))) { step += 1 }
                    } else {
                        GymActionPill(text: submitting ? "…" : L("create_meetup_create"), primary: true,
                                      enabled: !submitting && draft.canAdvance(step: 1)) { submit() }
                    }
                }
                .padding(.horizontal, 20).padding(.vertical, 12)
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationBarHidden(true)
        .onAppear(perform: applyPrefill)
        .onChange(of: draft.schoolId) { _, _ in loadDayScores() }
        .onChange(of: draft.days) { _, _ in loadDayScores() }
        .alert("Quedadas «No Mixto»", isPresented: $showGenderGate) {
            Button("Entendido", role: .cancel) {}
        } message: {
            Text("Las quedadas «No Mixto» son solo para perfiles con género Mujer. Si eres mujer y aún no lo has indicado, ponlo en Perfil → Editar perfil y podrás crearlas y unirte.")
        }
    }

    /// Lo que trae el asistente o la ficha se aplica UNA vez.
    private func applyPrefill() {
        guard !prefillApplied else { return }
        prefillApplied = true
        if let gym {
            placeLocked = true
            draft = draft.withGym(id: gym.id, name: gym.name)
        } else if let p = prefill {
            if !p.schoolId.isEmpty {
                placeLocked = true
                draft = draft.withSchool(id: p.schoolId, name: p.schoolName)
            }
            if let day = p.day { draft = draft.toggleDay(iso: day) }
            draft = draft.with(name: p.name, description: .some(p.description))
        }
    }

    private func submit() {
        guard let uc = createMeetup, !submitting else { return }
        submitting = true
        createError = nil
        let req = draft.toRequest(defaultName: defaultName)
        Task {
            do {
                let meetup = try await uc.execute(req: req)
                submitting = false
                // Mi mochila pasa a la quedada al crearla (igual que al unirse), como en Android.
                MeetupGearCopy.copyMyBackpack(meetupId: meetup.id)
                onCreated(meetup.id)
            } catch {
                let msg = error.localizedDescription
                if let off = FeatureOff.notice(from: error) {
                    paused = off
                } else if msg.contains("GENDER_REQUIRED") {
                    showGenderGate = true
                } else {
                    createError = msg
                }
                submitting = false
            }
        }
    }

    private func loadDayScores() {
        let sid = draft.schoolId.trimmingCharacters(in: .whitespaces)
        guard !draft.placeIsGym, !sid.isEmpty, !draft.days.isEmpty else { dayScores = [:]; return }
        Task {
            guard let results = try? await getRangeScores.invoke(ids: [sid], dates: draft.days.sorted()) else { return }
            var map: [String: Int] = [:]
            if let school = results.first { for day in school.days { map[day.date] = Int(day.score) } }
            dayScores = map
        }
    }
}

/// Paso 1 de 3: ¿Dónde vais a escalar? Días, Escuela | Rocódromo y buscador.
struct MeetupPlaceStep: View {
    @Binding var draft: MeetupDraft
    let dayScores: [String: Int]
    let placeLocked: Bool
    @State private var query = ""
    @State private var schools: [School] = []
    @StateObject private var gyms = GymPickerViewModel()

    var body: some View {
        if placeLocked && draft.hasPlace {
            WizardQuestion(text: L("mw_step1_when_only"))
            lockedCard
            WizardEyebrow(text: L("mw_when"))
            MeetupDayPicker(selected: draft.days, scores: dayScores) { draft = draft.toggleDay(iso: $0) }
        } else {
            WizardQuestion(text: L("mw_step1_q"))
            WizardEyebrow(text: L("mw_when"))
            MeetupDayPicker(selected: draft.days, scores: dayScores) { draft = draft.toggleDay(iso: $0) }
            WizardEyebrow(text: L("mw_where"))
            WizardSegmented(first: L("create_meetup_place_school"), second: L("create_meetup_place_gym"), firstSelected: !draft.placeIsGym,
                            onFirst: { reset(); draft = draft.withPlaceKind(gym: false) },
                            onSecond: { reset(); draft = draft.withPlaceKind(gym: true) })
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Cumbre.ink2)
                TextField(L(draft.placeIsGym ? "gym_search_hint" : "mw_place_school_hint"), text: $query).font(.system(size: 15))
            }
            .padding(.horizontal, 14).padding(.vertical, 12).background(Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
            .onChange(of: query) { _, q in search(q) }
            if draft.hasPlace {
                WizardEyebrow(text: L(draft.placeIsGym ? "create_meetup_place_gym" : "create_meetup_place_school"))
                placeCard(draft.placeName, nil, selected: true) { draft = draft.with(schoolId: "", gymId: .some(nil), placeName: "") }
            }
            if !draft.hasPlace || !query.isEmpty { results }
        }
    }

    @ViewBuilder private var results: some View {
        if draft.placeIsGym {
            let list = (query.isEmpty ? gyms.nearby + gyms.favorites : gyms.results).filter { $0.gym.id != draft.gymId?.int64Value }
            if !list.isEmpty {
                WizardEyebrow(text: L(query.isEmpty ? "mw_nearby" : "mw_results"))
                ForEach(list) { e in
                    let place = [e.gym.city, e.gym.province].filter { !$0.isEmpty }
                    let sub = [place.isEmpty ? nil : Array(Set(place)).sorted().joined(separator: ", "), GymPresentation.distanceText(e.distanceKm)]
                        .compactMap { $0 }.joined(separator: " · ")
                    placeCard(e.gym.name, sub.isEmpty ? nil : sub, selected: false) {
                        query = ""
                        draft = draft.withGym(id: e.gym.id, name: e.gym.name)
                    }
                }
            } else if !query.isEmpty {
                Text(L("mw_no_results")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            }
        } else {
            let list = schools.filter { $0.id != draft.schoolId }
            if !list.isEmpty {
                WizardEyebrow(text: L("mw_results"))
                ForEach(list, id: \.id) { s in
                    placeCard(s.name, s.location, selected: false) {
                        query = ""
                        schools = []
                        draft = draft.withSchool(id: s.id, name: s.name)
                    }
                }
            } else if query.count >= 2 {
                Text(L("mw_no_results")).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
            }
        }
    }

    private func reset() {
        query = ""
        schools = []
        gyms.query = ""
    }

    private func search(_ q: String) {
        if draft.placeIsGym { gyms.query = q; return }
        guard q.count >= 2 else { schools = []; return }
        Task {
            if let r = try? await AppDependencies.shared.container.searchSchools.invoke(query: q, limit: 8), q == query {
                var seen = Set<String>()
                schools = r.filter { seen.insert($0.id).inserted }
            }
        }
    }

    private var lockedCard: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.22)).frame(width: 42, height: 42)
                .overlay(Image("gym_ic_gym").renderingMode(.template).resizable().scaledToFit().frame(width: 22, height: 22).foregroundStyle(.white))
            VStack(alignment: .leading, spacing: 2) {
                Text(L(draft.placeIsGym ? "create_meetup_place_gym" : "create_meetup_place_school").uppercased()).eyebrow(Color.white.opacity(0.85))
                Text(draft.placeName).font(Cumbre.serif(18, .bold)).foregroundStyle(.white).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(L("mw_fixed").uppercased()).eyebrow(Cumbre.terraFill)
                .padding(.horizontal, 10).padding(.vertical, 5).background(Color.white, in: Capsule())
        }
        .padding(14)
        .background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 20))
    }

    private func placeCard(_ name: String, _ sub: String?, selected: Bool, _ action: @escaping () -> Void) -> some View {
        WizardOptionCard(selected: selected, action: action) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 14).fill(draft.placeIsGym ? Cumbre.ink : Cumbre.terraFill).frame(width: 42, height: 42)
                    .overlay(Image("gym_ic_gym").renderingMode(.template).resizable().scaledToFit().frame(width: 22, height: 22)
                        .foregroundStyle(Cumbre.bg))
                WizardCardTitle(title: name, subtitle: sub)
            }
        }
    }
}

/// Próximos 14 días en tarjetas redondeadas (varios a la vez); con escuela, la nota del día debajo.
struct MeetupDayPicker: View {
    let selected: Set<String>
    let scores: [String: Int]
    let onToggle: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<14, id: \.self) { i in
                    let date = Calendar.current.date(byAdding: .day, value: i, to: Date()) ?? Date()
                    let iso = Self.iso(date)
                    let on = selected.contains(iso)
                    Button { onToggle(iso) } label: {
                        VStack(spacing: 0) {
                            Text(i == 0 ? L("w_today").uppercased() : Self.dow(date)).eyebrow(on ? .white : Cumbre.ink2).lineLimit(1)
                            Text("\(Calendar.current.component(.day, from: date))").font(Cumbre.serif(20, .bold))
                                .foregroundStyle(on ? .white : Cumbre.ink)
                            if let s = scores[iso] {
                                Text("\(s)").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                    .padding(.horizontal, 6).padding(.vertical, 1).background(Cumbre.score(s), in: Capsule()).padding(.top, 2)
                            }
                        }
                        .frame(width: 58).padding(.vertical, 10)
                        .background(on ? Cumbre.terraFill : Cumbre.paper, in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    static func iso(_ d: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: d)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func dow(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = LanguageManager.shared.locale
        f.dateFormat = "EEE"
        return f.string(from: d).replacingOccurrences(of: ".", with: "").uppercased()
    }
}
