import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// PANTALLA DE DETALLE de escuela (orquestador) — las piezas viven en:
// SchoolForecastViews / SchoolMapSection / BlockInfoSheet / ContributionSheets /
// SchoolNotesViews / SchoolDetailHelpers (espejo del reparto de Android).

/// Lo que se ofrece descargar para ver la escuela sin cobertura. Espejo de
/// `OfertaFotosOffline` en Android.
/// Salta el intento de red en `load()` cuando `NetworkMonitor` ya dice que no
/// hay conexión en absoluto — cae directo al `catch` genérico (Álvaro, 2026-09-30).
struct KnownOfflineError: Error {}

struct SchoolDetailView: View {
    let school: School
    /// Si se indica, al abrir se despliega el mapa y se abre la piedra que
    /// contiene esa vía (deep-link desde el diario).
    var openVia: String? = nil
    @StateObject private var vm = SchoolDetailViewModel()
    @State private var factorsExpanded = false
    @State private var selectedDay: DayForecast?
    @Environment(\.dismiss) private var dismiss
    // Deslizar hacia abajo pide otra vez quién hay en "Estoy aquí" Y las
    // piedras del mapa (nuevas o de otros) — mejor a demanda que sondear
    // sola cada X segundos (Álvaro, 2026-09-04).
    @State private var presenceRefreshTrigger = 0
    @State private var showProcessionary = false
    @State private var showHelp = false
    /// Pestaña activa (opción B del rediseño, 2026-10-13). El mapa va por defecto.
    @State private var tab: SchoolTab = .map
    /// Vía que debe abrir el mapa al mostrarse: la del deep-link, o la que se toca en la pestaña BLOQUES.
    @State private var mapOpenVia: String?
    /// "Ver en mapa" desde la guía: piedra a centrar (sin abrirla).
    @State private var mapFocusBlock: String?
    /// Se abrió una vía DESDE la lista de BLOQUES: al cerrar su ficha se vuelve a la lista, no se queda en el mapa.
    @State private var returnToBlocks = false
    /// Estado de la pestaña BLOQUES: aquí para que sobreviva al cambiar de pestaña.
    @StateObject private var blocksModel = SchoolBlocksExplorerModel()
    /// Restricciones en vigor ("!" en sectores y piedras): se cargan aquí y las leen los hijos por el entorno.
    @StateObject private var restrictionsModel = SchoolRestrictionsModel()
    @State private var showRestrictions = false
    /// Tarjeta HOY abierta a mano. En la pestaña TIEMPO se muestra abierta sola (es justo el tiempo).
    @State private var hoyManual = false
    /// "¿Qué piedra es esta?": foto de una piedra de ESTA escuela (botón de cámara junto a AYUDA).
    @StateObject private var photoFinder = SchoolPhotoFinder()
    private var hoyOpen: Binding<Bool> {
        Binding(get: { hoyManual || tab == .weather }, set: { hoyManual = $0 })
    }

    init(school: School, openVia: String? = nil) {
        self.school = school
        self.openVia = openVia
        _mapOpenVia = State(initialValue: openVia)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Fijo, fuera del scroll (Álvaro, 2026-09-03: si no, nadie sabe que hay alguien ahí). Desde 2026-10-14 la
            // misma fila lleva la cabecera (nombre + iconos), el chat de la escuela y el botón "Aquí".
            SchoolPresenceRow(schoolId: school.id, schoolName: school.name,
                              myUid: AppDependencies.shared.authBridge.currentUid(),
                              refreshTrigger: presenceRefreshTrigger,
                              tileCount: 8,
                              leading: { w in
                                  SchoolDetailHeaderTitle(onBack: { dismiss() }, width: w)
                              },
                              trailing: { w in
                                  SchoolDetailHeaderActions(vm: vm, school: school, tile: w,
                                                            onProcessionary: { showProcessionary = true })
                              })
            // CABECERA FIJA, FUERA del ScrollView: nombre + «? AYUDA» + cámara, y las pestañas MAPA · GUÍA · TIEMPO ·
            // NOTAS. Es un VStack con el bloque arriba y el scroll debajo (no `pinnedViews` ni anclas aproximadas): así
            // no se mueve nunca, ni en el mapa, ni en la guía, ni en tiempo/notas.
            schoolTitle
            SchoolTabBar(selection: $tab, schoolStyle: school.style)
                .guideTarget("detail.tabs")
            Rectangle().fill(Cumbre.rule).frame(height: 1)
            scrollContent
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .guideHost("detail")
        .navigationBarHidden(true)
        .sheet(item: $selectedDay) { d in
            DayDetailView(day: d, allHours: vm.forecast?.hours ?? [])
        }
        .sheet(isPresented: $showHelp) { HelpSheet(topicKey: "detail") }
        .sheet(isPresented: $photoFinder.showSheet) {
            SchoolPhotoSheet(finder: photoFinder, schoolName: school.name) { lineId in
                // Se abre la piedra en el mapa de la escuela, como al tocarla en la lista de bloques.
                photoFinder.cancel()
                mapOpenVia = lineId
                returnToBlocks = false
                tab = .map
            }
        }
        // El mapa se recrea al volver a él: sin esto reabriría otra vez la piedra del deep-link o de BLOQUES.
        .onChange(of: tab) { newTab in
            if newTab != .map { mapOpenVia = nil; mapFocusBlock = nil }
            // Si el usuario cambia de pestaña a mano, ya no hay "vuelta a la lista" pendiente.
            if newTab != .map { returnToBlocks = false }
        }
        .sheet(isPresented: $showProcessionary) {
            ProcessionaryInfoSheet(
                vm: vm,
                onConfirm: { vm.confirmProcessionary(schoolId: school.id) },
                onRetract: { vm.retractProcessionary(schoolId: school.id) },
                onActiveNow: { vm.reportProcessionaryActiveNow(schoolId: school.id) },
                onClearActiveNow: { vm.clearProcessionaryActiveNow(schoolId: school.id) }
            )
        }
        .task { await vm.load(school: school) }
        .task { await restrictionsModel.load(schoolId: school.id) }
        .environment(\.schoolRestrictions, restrictionsModel.ui)
        .sheet(isPresented: $showRestrictions) {
            if let ui = restrictionsModel.ui {
                RestrictionsSheet(items: ui.restrictions.active, blocks: ui.blocks)
            }
        }
        .alert("¿Guardar también las fotos?",
               isPresented: Binding(get: { vm.ofertaFotos != nil },
                                    set: { if !$0 { vm.ofertaFotos = nil } })) {
            Button("Descargar") {
                // Se captura AQUÍ, antes de que el Binding del alert la borre.
                if let oferta = vm.ofertaFotos {
                    Task { await vm.descargarFotosOffline(oferta) }
                }
            }
            Button("Ahora no", role: .cancel) { vm.ofertaFotos = nil }
        } message: {
            if let o = vm.ofertaFotos {
                Text(L("La escuela ya está guardada. Bajar sus %@ fotos (%@) te deja ver los topos en la roca aunque no haya cobertura.\n\nSi dices que no, tendrás los nombres, los grados y las líneas, pero no las fotos sobre las que van dibujadas.", o.urls.count, o.pesoTexto))
            }
        }
        .overlay {
            if vm.descargandoFotos {
                ProgressView("Guardando las fotos…")
                    .padding(20)
                    .background(Cumbre.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
            }
        }
    }

    @ViewBuilder private func tabbedContent(_ f: Forecast?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            switch tab {
            case .map:
                // Boletín de montaña AEMET QUITADO (Álvaro, 2026-11-05): unas veces llega y otras no.
                // Para volver: MountainBulletinSection(lat: school.lat, lon: school.lon, country: school.country)
                SchoolMapSection(school: school, openVia: mapOpenVia, focusBlockId: mapFocusBlock, showsSearch: false,
                                 onBlockSheetClosed: {
                                     // Una sola vuelta: la lista conserva sus filtros (viven en blocksModel).
                                     guard returnToBlocks else { return }
                                     returnToBlocks = false
                                     tab = .blocks
                                     // Vuelve a la piedra que se abrió (antes quedaba arriba del todo y había que
                                     // bajar otra vez). Espera a que la guía esté pintada para poder desplazarse.
                                     DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                                         blocksModel.scrollToBlock = blocksModel.openBookmarkedSector()
                                     }
                                 },
                                 refreshTrigger: presenceRefreshTrigger)
                    .id(mapOpenVia ?? mapFocusBlock ?? "")
            case .blocks:
                SchoolBlocksExplorerView(school: school, model: blocksModel, onShowOnMap: { blockId in
                    mapFocusBlock = blockId
                    returnToBlocks = false
                    tab = .map
                }) { lineId in
                    mapOpenVia = lineId
                    returnToBlocks = true
                    tab = .map
                }
            case .weather:
                if let f {
                    ForecastBodyView.weatherTab(f, onSelectDay: { selectedDay = $0 })
                }
                // Mejores meses del año (stats mensuales del backend, cacheadas).
                if !vm.monthlyScores.isEmpty {
                    MonthlyStatsSection(scores: vm.monthlyScores, bestRange: vm.monthlyBestRange)
                }
            case .notes:
                NotesSectionView(
                    notes: vm.notes,
                    publishing: vm.publishing,
                    onPublish: { text, image in Task { await vm.publishNote(schoolId: school.id, text: text, image: image) } },
                    onVote: { note, v in Task { await vm.voteNote(note, value: v) } },
                    alwaysOpen: true
                )
            }
        }
    }

    /// El nombre de la escuela (fijo, fuera del scroll) con el botón "? AYUDA" y la cámara a la derecha: un solo botón
    /// de ayuda que reconoce pantalla y pestaña. La 1ª vez cuenta la pantalla y encadena la de la pestaña; luego solo
    /// la de la pestaña. Sin guía, la ayuda de texto de siempre.
    private var schoolTitle: some View {
        HStack(alignment: .center, spacing: 10) {
            Text(school.name)
                .font(Cumbre.serif(26, .bold))
                .foregroundStyle(Cumbre.ink)
                .lineLimit(2).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, alignment: .leading)
            // "! 2 CERRADOS" junto al nombre: solo DENTRO de la escuela (nunca en la lista).
            if let ui = restrictionsModel.ui {
                RestrictionsPill(ui: ui) { showRestrictions = true }
            }
            GuidePill(topicKey: "detail") {
                if !GuideStore.shared.startScreenAndTab(screen: "detail", tab: tab.guideKey) { showHelp = true }
            }
            SchoolPhotoButton(finder: photoFinder, school: school)
        }
        .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 2)
    }

    /// Ancla de los saltos a sector o piedra: el borde de arriba del scroll. La cabecera (baldosas, nombre y pestañas)
    /// está FUERA del ScrollView, así que no hay que descontar su altura.
    private static let belowTabs = UnitPoint.top

    private var scrollContent: some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(spacing: 0) {
            if vm.loading {
                SkeletonSchoolDetail()
                    .frame(maxWidth: .infinity, minHeight: 320)
            } else if let err = vm.errorText {
                ContentUnavailableView(L("Sin previsión"), systemImage: "cloud.slash", description: Text(err))
                    .padding(.top, 60)
            } else if let f = vm.forecast {
                FirstTimeHint(
                    hintKey: "detail_offline",
                    text: L("Toca ↓ (arriba) para guardar esta escuela y verla sin conexión, incluyendo el mapa y las piedras.")
                )
                FirstTimeHint(
                    hintKey: "detail_propose",
                    text: L("Despliega el mapa de abajo y usa + PROPONER para añadir piedras, parkings o sectores que falten. Un admin lo revisa.")
                )
                FirstTimeHint(
                    hintKey: "detail_tick",
                    text: L("Toca una piedra en el mapa para ver sus vías. El círculo ○ marca una vía como hecha y la guarda en tu diario.")
                )
                if vm.offlineForecast {
                    HStack(spacing: 6) {
                        Image(systemName: "wifi.slash").font(.system(size: 11))
                        Text(vm.offlineSince.map { L("SIN CONEXIÓN · ACTUALIZADO %@", relativeUpdated($0).uppercased()) }
                             ?? L("SIN CONEXIÓN · PREVISIÓN GUARDADA")).eyebrow()
                    }
                    .foregroundStyle(Cumbre.terra)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 8)
                    .background(Cumbre.terraBg)
                }
                ForecastBodyView(
                    forecast: f,
                    directions: (lat: school.lat, lon: school.lon, label: school.name),
                    factorsExpanded: $factorsExpanded,
                    onSelectDay: { selectedDay = $0 },
                    tabsSlot: AnyView(tabbedContent(f)),
                    hoyOpen: hoyOpen
                )
            } else {
                // Sin previsión: las pestañas van igualmente (el tiempo no tiene nada que enseñar).
                tabbedContent(nil)
            }
            }
        }
        // Deslizar hacia abajo recarga el tiempo Y pide otra vez quién hay en
        // "Estoy aquí" (Álvaro, 2026-09-04: mejor a demanda que sondear sola).
        .refreshable {
            await vm.load(school: school)
            presenceRefreshTrigger += 1
            // La guía también: si has cambiado el sector de una piedra, al recargar tiene que verse.
            await blocksModel.loadIfNeeded(schoolId: school.id, schoolName: school.name, force: true)
            await blocksModel.loadOverview(schoolId: school.id)
        }
        // Sector tocado en el mapa general de la guía.
        .onChange(of: blocksModel.scrollToSector) { key in
            guard let key else { return }
            blocksModel.scrollToSector = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(GuideAnchor.sector(key), anchor: Self.belowTabs) }
            }
        }
        // Vía tocada en el listado del inicio de sector: baja a la tarjeta de su piedra.
        .onChange(of: blocksModel.scrollToBlock) { id in
            guard let id else { return }
            blocksModel.scrollToBlock = nil
            withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(GuideAnchor.id(id), anchor: Self.belowTabs) }
        }
        }
    }
}

/// "Mejores meses del año" — barras de score medio por mes (3 años de histórico,
/// calculado en el backend). Espejo de la sección mensual de Android.
struct MonthlyStatsSection: View {
    let scores: [Int]
    let bestRange: String?
    private let months = ["E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(L("MEJORES MESES"))
            if let r = bestRange, !r.isEmpty {
                Text(L("Mejor época: %@", ForecastText.monthsIn(r)))
                    .font(Cumbre.mono(12)).foregroundStyle(Cumbre.ink2)
                    .padding(.horizontal, 16)
            }
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(scores.prefix(12).enumerated()), id: \.offset) { i, s in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Cumbre.score(s))
                            .frame(height: max(4, CGFloat(s) * 0.7))
                        Text(months[i % 12]).font(Cumbre.mono(9)).foregroundStyle(Cumbre.ink3)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 90, alignment: .bottom)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
    }
}

/// Sección de mapa de la escuela (plegable). Muestra un MapLibre con tiles
/// topográficos, un marcador en la escuela y el botón "CÓMO LLEGAR". Los bloques
/// (parking/piedras/zonas) se añadirán como marcadores en una iteración siguiente.

struct SchoolDetailLoaderView: View {
    let schoolId: String
    @State private var school: School?
    @State private var failed = false

    var body: some View {
        Group {
            if let s = school {
                SchoolDetailView(school: s)
            } else if failed {
                ContentUnavailableView(L("Escuela no encontrada"), systemImage: "mappin.slash")
            } else {
                SkeletonSchoolDetail()
            }
        }
        .task {
            if let s = try? await AppDependencies.shared.container.getSchoolById.invoke(id: schoolId) {
                school = s
            } else { failed = true }
        }
    }
}

/// Cuerpo del forecast (réplica de ForecastBody.kt). Reutilizado por el detalle
/// de escuela y por el tab Tiempo (en tu ubicación). `directions` es opcional:
/// el tab Tiempo no muestra "CÓMO LLEGAR" (no hay escuela destino).

#Preview {
    NavigationStack {
        SchoolDetailView(school: School(id: "x", name: "Demo", location: "Demo", region: "Aragón",
                                        style: "Boulder", rockType: "Caliza", lat: 0, lon: 0, source: nil,
                                        country: "ES", hasKnownProcessionary: false, processionaryAlertActive: false, processionaryActiveNowSet: false))
    }
}

/// Polígono irregular con la silueta del marcador de piedra (botonera).
