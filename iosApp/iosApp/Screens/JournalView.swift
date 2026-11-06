import SwiftUI
import Shared

// Diario de escalada — espejo de JournalEntriesScreen.kt + stats del perfil.

@MainActor
final class JournalViewModel: ObservableObject {
    @Published var entries: [JournalSession] = []
    @Published var stats: JournalStats?
    @Published var loading = true

    private let getMyJournal: GetMyJournalUseCase
    private let getMyStats: GetMyJournalStatsUseCase
    private let createEntry: CreateJournalEntryUseCase
    private let deleteEntry: DeleteJournalEntryUseCase

    init(
        getMyJournal: GetMyJournalUseCase = AppDependencies.shared.container.getMyJournal,
        getMyStats: GetMyJournalStatsUseCase = AppDependencies.shared.container.getMyJournalStats,
        createEntry: CreateJournalEntryUseCase = AppDependencies.shared.container.createJournalEntry,
        deleteEntry: DeleteJournalEntryUseCase = AppDependencies.shared.container.deleteJournalEntry
    ) {
        self.getMyJournal = getMyJournal
        self.getMyStats = getMyStats
        self.createEntry = createEntry
        self.deleteEntry = deleteEntry
    }

    /** C3: agrupacion por mes PRECOMPUTADA (el body solo la lee). */
    @Published var entriesByMonth: [String: [JournalSession]] = [:]
    @Published var monthKeys: [String] = []
    /// G: filtro por grado activo (nil = todos) + grados presentes ordenados.
    @Published var gradeFilters: Set<String> = []
    @Published var availableGrades: [String] = []

    func load() async {
        loading = true
        entries = (try? await getMyJournal.invoke()) ?? []
        stats = try? await getMyStats.invoke()
        let calc = JournalStatsCalculator.shared
        // Un grado doble ("6b+/6c") cuenta para los DOS chips.
        availableGrades = Set(entries.flatMap { gradeParts($0.grade) })
            .sorted { calc.gradeRank(grade: $0) > calc.gradeRank(grade: $1) }
        regroup()
        loading = false
    }

    /// Reagrupa por mes aplicando el filtro de grado (PRECOMPUTADO: nada de
    /// agrupar en el body — lección del watchdog).
    func regroup() {
        let visible = entries.filter { gradeMatches($0.grade, anyOf: gradeFilters) }
        entriesByMonth = Dictionary(grouping: visible.sorted { $0.date > $1.date },
                                    by: { String($0.date.prefix(7)) })
        monthKeys = entriesByMonth.keys.sorted(by: >)
    }

    func add(blockName: String, grade: String, schoolId: String?, schoolName: String, sector: String, notes: String, discipline: String) async {
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
        let req = CreateJournalRequest(
            schoolId: schoolId,
            schoolName: schoolName.nilIfBlank,
            sector: sector.nilIfBlank,
            blockName: blockName.trimmingCharacters(in: .whitespaces),
            grade: grade.nilIfBlank,
            notes: notes.nilIfBlank,
            date: df.string(from: Date()),
            discipline: discipline,   // antes iba nil → todo caía en "Bloques"
            lineId: nil,
            status: nil,
            aVista: false,
            alFlash: false
        )
        _ = try? await createEntry.invoke(req: req)
        await load()
    }

    /** C3: cambiar la fecha de una entrada y recargar. */
    func changeDate(_ id: String, _ newDate: String) async {
        if await reporting(L("No se pudo cambiar la fecha"), {
            try await AppDependencies.shared.container.updateJournalDate.invoke(id: id, date: newDate)
        }) != nil {
            await load()
        }
    }

    func delete(_ id: String) {
        entries.removeAll { $0.id == id }
        Task { try? await deleteEntry.invoke(id: id); await load() }
    }
}

struct JournalView: View {
    @StateObject private var vm = JournalViewModel()
    @State private var showAdd = false

    var body: some View {
        Group {
            if vm.loading {
                SkeletonList(count: 8) { SkeletonGradeRow() }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        if let s = vm.stats { JournalStatsRow(stats: s) }
                        Button { showAdd = true } label: {
                            Text("+ AÑADIR BLOQUE").font(Cumbre.mono(12, .bold)).tracking(0.8)
                                .foregroundStyle(.white).padding(.vertical, 14).frame(maxWidth: .infinity)
                                .background(Cumbre.terraFill)
                        }
                        .buttonStyle(.plain).padding(16)
                        Divider().overlay(Cumbre.rule)
                        if vm.entries.isEmpty {
                            Text("Aún no has registrado bloques.")
                                .font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(32)
                        } else {
                            // Filtro por GRADO, con la paleta de los topos. El VM
                            // ya lo calculaba y lo aplicaba, pero los chips no se
                            // pintaban: la función estaba, sin manera de usarla
                            // (Android sí los tenía).
                            if vm.availableGrades.count > 1 {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 6) {
                                        ForEach(vm.availableGrades, id: \.self) { g in
                                            let estilo = GradeColor.chipStyle(g)
                                            let color = estilo.dark ? Cumbre.ink : estilo.stroke
                                            let activo = vm.gradeFilters.contains(g)
                                            Button {
                                                // VARIOS a la vez: tocar añade o quita ese grado del filtro.
                                                if activo { vm.gradeFilters.remove(g) } else { vm.gradeFilters.insert(g) }
                                                vm.regroup()
                                            } label: {
                                                Text(g)
                                                    .font(Cumbre.mono(11, .bold))
                                                    .foregroundStyle(activo ? .white : color)
                                                    .padding(.horizontal, 10).padding(.vertical, 5)
                                                    .background(activo ? color : Color.clear)
                                                    .overlay(RoundedRectangle(cornerRadius: 6)
                                                        .stroke(color, lineWidth: 1))
                                            }.buttonStyle(.plain)
                                        }
                                    }
                                    .padding(.horizontal, 16).padding(.vertical, 6)
                                }
                            }
                            // C3: agrupado por MES. PRECOMPUTADO en el VM — hacerlo
                            // en el body relanzaba el calculo en cada frame (watchdog
                            // 0x8BADF00D del 29-jul).
                            let byMonth = vm.entriesByMonth
                            ForEach(vm.monthKeys, id: \.self) { month in
                                Text(JournalView.monthHeader(month) + " · \(byMonth[month]!.count)")
                                    .font(Cumbre.mono(10, .bold)).tracking(1)
                                    .foregroundStyle(Cumbre.terra)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 16).padding(.vertical, 8)
                                ForEach(byMonth[month]!, id: \.id) { e in
                                    JournalRow(entry: e, schoolId: e.schoolId,
                                               onChangeDate: { newDate in
                                                   Task { await vm.changeDate(e.id, newDate) }
                                               }) { vm.delete(e.id) }
                                    Divider().overlay(Cumbre.rule)
                                }
                            }
                        }
                    }
                }
            }
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Mi diario")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAdd) {
            AddBlockSheet { block, grade, schoolId, school, sector, notes, discipline in
                Task { await vm.add(blockName: block, grade: grade, schoolId: schoolId, schoolName: school, sector: sector, notes: notes, discipline: discipline) }
            }
        }
        .task { await vm.load() }
    }
}

struct JournalStatsRow: View {
    let stats: JournalStats
    var body: some View {
        HStack(spacing: 0) {
            cell("\(stats.blockCount)", L("BLOQUES"))
            cell("\(stats.schoolCount)", L("ESCUELAS"))
            cell(stats.maxGrade ?? "—", L("GRADO MÁX"))
        }
        .padding(.vertical, 16)
        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
        .padding(16)
    }
    private func cell(_ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(v).font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
            Text(l).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }.frame(maxWidth: .infinity)
    }
}

struct JournalRow: View {
    let entry: JournalSession
    /// Si se indica, la fila es pulsable y abre esa escuela (ver la piedra).
    var schoolId: String? = nil
    /// nº de piedra + sector resueltos en vivo del catálogo (no se guardan).
    var info: ViaCatalogInfo? = nil
    /// C3: cambiar la fecha de la entrada (nil = no editable, diario ajeno).
    var onChangeDate: ((String) -> Void)? = nil
    /// Cambiar el estilo (a vista / al flash), excluyentes: encender uno apaga el otro.
    /// nil = no editable (diario ajeno).
    var onChangeStyle: ((_ aVista: Bool, _ alFlash: Bool) -> Void)? = nil
    /// nil → fila de solo lectura (diario de otro usuario, no se puede borrar).
    var onDelete: (() -> Void)? = nil
    @State private var showDatePicker = false
    @State private var pickedDate = Date()
    /// Borrar pide confirmación: un toque sin querer en el cubo no se lleva la entrada (Álvaro, 2026-10-19).
    @State private var confirmDelete = false
    /// Deslizar la fila a la izquierda descubre FECHA y BORRAR (Álvaro, 2026-10-19). Las listas del diario son
    /// ScrollView, no List, así que el `swipeActions` nativo no existe aquí: gesto propio, solo en horizontal.
    @State private var swipeOffset: CGFloat = 0
    @State private var swipeBase: CGFloat = 0
    /// Grados partidos por altura (se piden juntos para todas las filas visibles).
    @ObservedObject private var gradeSplits = GradeSplitsStore.shared

    /// "Escuela · Piedra N · Sector" — lo que se pueda resolver del catálogo.
    private var subtitle: String {
        var parts: [String] = []
        if let sn = entry.schoolName, !sn.isEmpty { parts.append(sn) }
        if let n = info?.boulderNumber, !n.isEmpty { parts.append(L("Piedra %@", n)) }
        if let s = info?.sector, !s.isEmpty { parts.append(s) }
        parts.append(shortDate)
        return parts.joined(separator: " · ")
    }

    /// «12 oct» (la fecha ya no es un botón escondido: se cambia desde «⋯» o deslizando).
    private var shortDate: String {
        let iso = String(entry.date.prefix(10))
        let p = DateFormatter(); p.dateFormat = "yyyy-MM-dd"; p.locale = Locale(identifier: "en_US_POSIX")
        guard let d = p.date(from: iso) else { return iso }
        let out = DateFormatter(); out.setLocalizedDateFormatFromTemplate("d MMM")
        return out.string(from: d)
    }

    private var canEdit: Bool { onChangeDate != nil || onDelete != nil }
    /// Ancho de los botones que descubre el deslizar.
    private var actionsWidth: CGFloat { (onChangeDate != nil ? 76 : 0) + (onDelete != nil ? 76 : 0) }

    private func splitHalf(_ g: String) -> some View {
        Text(g).font(Cumbre.mono(9, .bold))
            .foregroundStyle(GradeColor.chipStyle(g).dark ? Color.black : Color.white)
            .minimumScaleFactor(0.7).lineLimit(1)
            .frame(width: 22, height: 32).background(GradeColor.chip(g))
    }

    private var leading: some View {
        HStack(spacing: 12) {
            // Miniatura de la piedra con el trazo de la vía, como en el buscador (Álvaro, 2026-10-19).
            if let photo = info?.photoUrl, !photo.isEmpty {
                MiniTopoThumbnail(photoUrl: photo, points: dedupPoints(TopoParse.points(info?.linePath)),
                                  grade: info?.grade ?? entry.grade)
                    .frame(width: 52, height: 52)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Cumbre.rule, lineWidth: 1))
            }
            // Grado ACTUAL del catálogo (refleja correcciones) o el guardado.
            if let g = (info?.grade ?? entry.grade), !g.isEmpty {
                if let split = entry.lineId.flatMap({ gradeSplits.splits[$0] }) {
                    // La altura parte la opinión sobre esta vía: dos mitades, cada una con su grado y su color.
                    HStack(spacing: 0) {
                        splitHalf(split.tall)
                        splitHalf(split.short)
                    }
                    .frame(width: 44, height: 32)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                } else {
                    // Texto negro sobre grados claros (≤5c son blancos) para que se lea.
                    Text(g).font(Cumbre.mono(12, .bold))
                        .foregroundStyle(GradeColor.chipStyle(g).dark ? .black : .white)
                        .frame(width: 44, height: 32).background(GradeColor.chip(g))
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: GradeColor.chipStyle(g).dark ? 1 : 0))
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.blockName).font(Cumbre.serif(16, .semibold)).foregroundStyle(Cumbre.ink)
                // Estilo de ascensión: solo se ve si hay algo que enseñar (ajeno)
                // o si es editable (propio, para poder añadirlo después).
                if entry.aVista || entry.alFlash || onChangeStyle != nil {
                    HStack(spacing: 4) {
                        if entry.aVista || onChangeStyle != nil {
                            styleBadge(L("A VISTA"), active: entry.aVista) {
                                onChangeStyle?(!entry.aVista, false)
                            }
                        }
                        if entry.alFlash || onChangeStyle != nil {
                            styleBadge(L("AL FLASH"), active: entry.alFlash) {
                                onChangeStyle?(false, !entry.alFlash)
                            }
                        }
                    }
                }
                // Escuela + nº de piedra + sector (resueltos del catálogo en vivo).
                if !subtitle.isEmpty {
                    Text(subtitle).font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                }
                // Ocultamos la nota auto "Piedra: N" (obsoleta: el número se recicla).
                if let n = entry.notes, !n.isEmpty, !n.hasPrefix("Piedra: ") {
                    Text(n).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                }
            }
        }
    }

    /// Chip pequeño de estilo (A VISTA / AL FLASH) — pulsable si onClick hace
    /// algo (onChangeStyle != nil), para marcarlo/desmarcarlo sin diálogo.
    private func styleBadge(_ label: String, active: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            Text(label).font(Cumbre.mono(9, .bold))
                .foregroundStyle(active ? .white : Cumbre.ink3)
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(active ? Cumbre.terra : Cumbre.paper,
                            in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                    .stroke(active ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(onChangeStyle == nil)
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            if canEdit && swipeOffset < 0 { swipeActions }
            rowContent
                .background(Cumbre.bg)
                .offset(x: swipeOffset)
                // Con la fila abierta, tocarla la cierra (en vez de abrir la piedra).
                .overlay {
                    if swipeOffset < 0 {
                        Color.clear.contentShape(Rectangle())
                            .onTapGesture { closeSwipe() }
                    }
                }
        }
        .clipped()
        .simultaneousGesture(canEdit ? swipeGesture : nil)
        .sheet(isPresented: $showDatePicker) { datePickerSheet }
        .alert(L("¿Borrar «%@» del diario?", entry.blockName), isPresented: $confirmDelete) {
            Button(L("Cancelar"), role: .cancel) {}
            Button(L("Borrar"), role: .destructive) { onDelete?() }
        } message: {
            Text(L("Se quitará de tu diario y de tus estadísticas."))
        }
    }

    private var rowContent: some View {
        ZStack {
            if let sid = schoolId, !sid.isEmpty {
                // BUG del propio fix (Álvaro, 2026-09-11): EmptyView() sin frame
                // mide 0x0 — el link "de fondo" no cubría nada y dejó de
                // navegar TOTALMENTE. Necesita frame explícito a pantalla
                // completa + contentShape para que el toque se registre en
                // todo ese rectángulo, no solo donde hay contenido dibujado.
                NavigationLink(destination: SchoolLoaderView(schoolId: sid, openVia: entry.lineId ?? entry.blockName)) {
                    Color.clear
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            }
            HStack(spacing: 12) {
                leading.onAppear { gradeSplits.want(entry.lineId) }
                Spacer(minLength: 4)
                if canEdit {
                    moreMenu
                } else if schoolId != nil, !(schoolId ?? "").isEmpty {
                    Image(systemName: "chevron.right").font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                }
            }
        }
        .contentShape(Rectangle())
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    /// «⋯»: todo lo que se puede hacer con la entrada, con nombre y un botón grande.
    private var moreMenu: some View {
        Menu {
            if onChangeDate != nil {
                Button { openDatePicker() } label: { Label(L("Cambiar fecha"), systemImage: "calendar") }
            }
            if onDelete != nil {
                Button(role: .destructive) { confirmDelete = true } label: {
                    Label(L("Borrar del diario"), systemImage: "trash")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Cumbre.ink2)
                .frame(width: 36, height: 36)
                .background(Cumbre.paper, in: Circle())
                .contentShape(Circle())
        }
        .accessibilityLabel(L("Más opciones"))
    }

    /// Lo que descubre el deslizar: FECHA (gris) y BORRAR (rojo).
    private var swipeActions: some View {
        HStack(spacing: 0) {
            if onChangeDate != nil {
                swipeButton(L("Fecha"), icon: "calendar", color: Cumbre.ink3) { closeSwipe(); openDatePicker() }
            }
            if onDelete != nil {
                swipeButton(L("Borrar"), icon: "trash", color: Cumbre.bad) { closeSwipe(); confirmDelete = true }
            }
        }
        .frame(maxHeight: .infinity)
    }

    private func swipeButton(_ title: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                Text(title).font(.system(size: 12, weight: .semibold))
            }
            .foregroundStyle(.white)
            .frame(width: 76)
            .frame(maxHeight: .infinity)
            .background(color)
        }
        .buttonStyle(.plain)
    }

    /// Solo cuenta si el gesto es claramente horizontal: el scroll vertical de la lista no se toca.
    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 18)
            .onChanged { v in
                guard abs(v.translation.width) > abs(v.translation.height) * 1.4 else { return }
                swipeOffset = min(0, max(-actionsWidth - 24, swipeBase + v.translation.width))
            }
            .onEnded { v in
                guard abs(v.translation.width) > abs(v.translation.height) * 1.4 else {
                    withAnimation(.spring(response: 0.3)) { swipeOffset = swipeBase }
                    return
                }
                let open = swipeBase + v.translation.width < -actionsWidth / 2
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    swipeOffset = open ? -actionsWidth : 0
                }
                swipeBase = swipeOffset
            }
    }

    private func closeSwipe() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { swipeOffset = 0 }
        swipeBase = 0
    }

    private func openDatePicker() {
        let p = DateFormatter(); p.dateFormat = "yyyy-MM-dd"; p.locale = Locale(identifier: "en_US_POSIX")
        pickedDate = p.date(from: String(entry.date.prefix(10))) ?? Date()
        showDatePicker = true
    }

    private var datePickerSheet: some View {
        VStack(spacing: 12) {
            Text("CAMBIAR FECHA").font(Cumbre.mono(11, .bold)).tracking(1)
                .foregroundStyle(Cumbre.terra)
            DatePicker("", selection: $pickedDate, in: ...Date(), displayedComponents: .date)
                .datePickerStyle(.graphical)
            Button("GUARDAR") {
                let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"
                onChangeDate?(df.string(from: pickedDate))
                showDatePicker = false
            }
            .font(Cumbre.mono(12, .bold)).foregroundStyle(Cumbre.terra)
        }
        .padding(16)
        .presentationDetents([.medium])
    }
}

private extension String { var nilIfBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self } }

extension JournalView {
    static func monthHeader(_ yyyyMm: String) -> String {
        let names = CalendarLabels.monthsLong().map { $0.uppercased() }
        let parts = yyyyMm.split(separator: "-")
        guard parts.count == 2, let m = Int(parts[1]), m >= 1, m <= 12 else { return yyyyMm }
        return names[m - 1] + " " + parts[0]
    }
}
