import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Block (clase Kotlin) Identifiable por su id — para .sheet(item:).

// SECCIÓN DEL MAPA de la escuela — espejo de SchoolMap.kt + SchoolMapView.kt
// (Android). OJO workarounds documentados inline: sheets colgados de mapArea,
// fullScreenCover colgado del buscador, task(id: expanded) del GPS. NO mover.

struct SchoolMapSection: View {
    let school: School
    var openVia: String? = nil

    // Filtro por ORIENTACIÓN (consenso comunitario): filtra los marcadores
    // de piedra del mapa. Piedras sin votos → solo en TODAS. Espejo de
    // OrientationFilter.kt de Android.
    @State private var blockOrientations: [String: String] = [:]
    @State private var orientationFilter: String? = nil
    // Filtro LOCAL por GRADO (ver BLOCK_SEARCH_DESIGN.md §7). No llama al
    // servidor: la escuela ya trae todas sus vías. Las piedras sin ninguna vía
    // en rango se ATENÚAN (no se ocultan): se conserva el contexto del sector.
    // No persiste al salir de la escuela, a diferencia del filtro de escuelas.
    @State private var selectedGrades: Set<String> = []
    /// Filtro Vía/Bloque del mapa: solo aparece si la escuela tiene AMBOS
    /// estilos ("Vía,Bloque" en school.style) — si es de uno solo, no aporta
    /// nada y no se muestra. Vacío = sin filtrar, se ve todo.
    @State private var styleFilter: Set<String> = []
    // Aproximaciones (caminos) — Fase 1 de APPROACH_DESIGN.md, solo lectura.
    // El loader vive aquí (no dentro de ApproachesSection) para que la ficha
    // mini de un sector/parking pueda ofrecer "SEGUIR" si hay un camino hasta
    // él, no solo la sección de la ficha completa.
    @StateObject private var approachesLoader = ApproachesLoader()
    @State private var followingApproach: Approach?
    @State private var didAutoOpen = false
    /// Bandera interna, ya no un boton: el mapa esta siempre visible (ver body).
    @State private var expanded = true
    @State private var selectedBlock: Block?
    // Flujo proponer/corregir agrupado (espejo de ProposalMapBridge en Android).
    @StateObject private var flow = MapProposalFlowStore()
    /// Foto de "Enviar piedra" que esperaba a esta escuela. Se lee UNA vez: si
    /// se quedara puesta, volver a entrar reabriría el flujo de proponer sin
    /// que nadie lo haya pedido.
    @State private var fotoSemilla: PhotoProposalSeedStore.Seed?
    /// Atajo "piedra desde una foto" lanzado DESDE esta escuela.
    @State private var eligiendoFoto = false
    /// Piedra a medias guardada en este movil (se lee al entrar).
    @State private var borradorPiedra: BoulderDraftStore.Draft?
    /// El formulario arranca precargado con el borrador.
    @State private var continuandoBorrador = false
    /// Punto donde se hizo la foto, a la espera de que el usuario lo confirme.
    @State private var confirmandoFoto: CLLocationCoordinate2D?
    /// "CORREGIR NOMBRE" del selector — sin tocar el mapa, solo un campo.
    @State private var correctingSchoolName = false
    /// "CORREGIR ESTILO" del selector — sin tocar el mapa, solo chips.
    @State private var correctingSchoolStyle = false
    // Datos del mapa (bloques/capas/buscador/admin) — ver SchoolMapViewModel.
    @StateObject private var vm = SchoolMapViewModel()
    @State private var mapStyle: MapStyleKind = .satellite  // paridad con Android
    @State private var userCoord: CLLocationCoordinate2D?   // mi ubicación en el sector
    // Mini-ficha flotante de PARKING/ZONA sobre el mapa (la ficha grande queda
    // solo para PIEDRAS). confirmDeleteMini = confirmación de borrado (admin).
    @State private var miniBlock: Block?
    @State private var confirmDeleteMini = false
    @State private var editLinesBlock: Block?
    @State private var assignSectorBlock: Block?
    @State private var mapZoom: Double = 14   // para mostrar el nombre del sector al acercar
    // Cámara conservada al recrear el MapView (entrar/salir de fullscreen).
    @State private var savedCenter: CLLocationCoordinate2D?
    @State private var savedZoom: Double?
    // Foco explícito al pulsar un parking en la lista (recentra el mapa ahí).
    @State private var focusCoord: CLLocationCoordinate2D?
    @State private var focusToken = 0
    // Encuadre de bounds (parking + su zona) — tiene prioridad sobre focusCoord.
    @State private var focusFit: [CLLocationCoordinate2D] = []
    // Capas ocultas por el usuario (leyenda pulsable): PARKING/BLOCK/ZONE.
    // Mapa a pantalla completa (estilo Radar).
    @State private var fullscreenMap = false
    // Buscador de vías/bloques de la escuela.
    // Brújula → cono de dirección en el punto azul.
    @StateObject private var headingProvider = HeadingProvider()

    // Colores de marcador por tipo (espejo de Android: parking azul, piedra
    // terra, zona verde; la escuela en tinta oscura).
    private let parkingColor = UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    private let blockColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    private let zoneColor = UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    private let schoolColor = UIColor(red: 0.11, green: 0.11, blue: 0.10, alpha: 1)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // SIN barra de abrir/cerrar: el mapa se ve SIEMPRE.
            //
            // Decision de Rodrigo (2026-08-11). En Android ese boton dejo de
            // responder dentro de una escuela y no se llego a aislar por que;
            // ademas el mapa es lo mas util de esta pantalla, asi que quitarlo
            // ahorra un toque a todo el mundo. Se quita en las DOS apps para no
            // romper la paridad.
            //
            // `expanded` sigue existiendo como bandera interna: la usan el GPS
            // (solo se sigue la ubicacion con el mapa a la vista) y el
            // deep-link. Arranca en true y ya no la cambia ningun boton.
            Group {
                searchBar
                GradeFilterBar(
                    selectedGrades: $selectedGrades,
                    availableGrades: availableGrades(vm.blocks),
                    result: gradeFilter,
                    onSelectLine: { match in
                        if let b = vm.blocks.first(where: { $0.id == match.blockId }) {
                            selectedBlock = b
                        }
                    })
                if schoolHasBothStyles { styleFilterRow }
                if !fullscreenMap {
                    mapArea(height: 280)
                }
                // Grabar/chincheta SOLO ADMIN por ahora (APPROACH_DESIGN.md §2.6/§10).
                ApproachesSection(loader: approachesLoader, school: school, blocks: vm.blocks,
                                   isAdmin: vm.isAdmin, following: $followingApproach)
                // "CÓMO LLEGAR" de la escuela quitado: las indicaciones salen al
                // tocar cada parking/piedra en el mapa (BlockInfoSheet).
                parkingsList
            }
        }
        .task(id: expanded) {
            guard expanded else { return }
            if vm.blocks.isEmpty {
                _ = await vm.reloadBlocks(school: school, selectedId: nil)
            }
            maybeAutoOpen()
            approachesLoader.load(schoolId: school.id)
            headingProvider.start()
            // Mi ubicación en CONTINUO mientras el mapa está abierto: el GPS se
            // mantiene caliente y afina en segundos (los fixes sueltos cada 5 s
            // salían "perdidísimos" en montaña).
            if AppDependencies.shared.locationBridge.hasPermission() {
                AppDependencies.shared.locationBridge.startStream { loc in
                    DispatchQueue.main.async {
                        userCoord = CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
                    }
                }
            }
            // Mantener el task vivo hasta plegar el mapa; al salir, se apaga.
            while expanded, !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
            }
            AppDependencies.shared.locationBridge.stopStream()
        }
        // Deep-link desde el diario: despliega el mapa al entrar.
        .onAppear {
            if let v = openVia, !v.isEmpty, !didAutoOpen, !expanded { expanded = true }
            // "Enviar piedra": la foto ya dijo qué se propone y dónde se hizo.
            // Se abre el mapa y se espera un toque para el sitio exacto: el GPS
            // se equivoca entre 10 y 30 metros y las piedras están a metros.
            borradorPiedra = BoulderDraftStore.load(schoolId: school.id)
            consumirSemillaDeFoto()
        }
        // Si los bloques llegan DESPUÉS del task (caché/red lenta), reintenta
        // el auto-abrir del deep-link (antes se quedaba en la escuela a secas).
        // OJO: la firma incluye las VÍAS, no solo el número de bloques. La
        // caché puede traer los mismos N bloques SIN sus vías: el auto-abrir
        // fallaba (abría la escuela a secas) y como el count no cambiaba al
        // llegar los datos reales, no se reintentaba (a la 2ª vez ya iba).
        .onChange(of: vm.blocks.reduce(0) { $0 + 1 + $1.lines.count }) { _ in maybeAutoOpen() }
        // ¿Admin? → puede eliminar bloques desde su ficha.
        .task {
            await vm.loadAdminFlag()
            // Consenso de orientación de TODAS las piedras (una llamada).
            if let m = try? await AppDependencies.shared.container
                .getSchoolOrientations.invoke(schoolId: school.id) {
                blockOrientations = (m as NSDictionary as? [String: String]) ?? [:]
            }
        }
        .alert("¿Eliminar «\(miniBlock?.name ?? "")»?", isPresented: $confirmDeleteMini) {
            Button("ELIMINAR", role: .destructive) {
                guard let mb = miniBlock else { return }
                miniBlock = nil
                Task {
                    try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: mb.id)
                    if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
                }
            }
            Button(NSLocalizedString("common_cancel", comment: ""), role: .cancel) {}
        } message: {
            Text("Esta acción no se puede deshacer.")
        }
    }

    /// Mapa con todos sus overlays. Compartido entre el modo tarjeta (280 pt)
    /// y pantalla completa (estilo Radar).
    @ViewBuilder
    private func mapArea(height: CGFloat) -> some View {
        ZStack(alignment: .bottomTrailing) {
                    MapLibreView(
                        center: savedCenter ?? CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
                        // M1: 12.5 (antes 14). El autoFit hace return en el primer apply
                        // (registra la base), así que la escuela abría al zoom INICIAL fijo
                        // y salía muy cerca. Con 12.5 abre más abierta (se ve más contexto).
                        zoom: savedZoom ?? 12.5,
                        markers: markers,
                        style: mapStyle,
                        // Encuadre inicial con TODOS los elementos (parkings/sectores/
                        // piedras), por muy separados que estén — antes el mapa abría
                        // fijo en zoom 14 sobre la escuela, dejando fuera sectores a
                        // km. Mi ubicación solo entra en el encuadre si ya está cerca
                        // (ver userMarkerIfNearby en MapLibreView.swift).
                        autoFitToMarkers: savedZoom == nil,
                        onZoomChange: { mapZoom = $0 },
                        onCameraChange: { c, z in savedCenter = c; savedZoom = z },
                        onTapMarker: { id in
                            if flow.correctionMode && !flow.corrActive {
                                selectCorrectionTarget(id)
                            } else if !flow.waitingTap && !flow.correctionMode {
                                // Parking/zona → mini-ficha flotante (el mapa se
                                // sigue viendo); piedras → su ficha completa.
                                if let b = vm.blocks.first(where: { $0.id == id }) {
                                    switch b.type.uppercased() {
                                    case "PARKING":
                                        miniBlock = b
                                        flyToParkingZone(b)
                                    case "ZONE":
                                        // Encuadra el sector con sus piedras
                                        // (expandiéndolas), como el parking.
                                        miniBlock = b
                                        // Zoom FIJO centrado entre sector y piedras
                                        // (los encuadres calculados se iban a extremos).
                                        let stones = vm.blocks.filter { $0.sectorBlockId == b.id }
                                        vm.collapsedSectors.remove(b.id)
                                        let pts = [CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)]
                                            + stones.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
                                        let cLat = pts.map { $0.latitude }.reduce(0, +) / Double(pts.count)
                                        let cLon = pts.map { $0.longitude }.reduce(0, +) / Double(pts.count)
                                        focusFit = []
                                        focusCoord = CLLocationCoordinate2D(latitude: cLat, longitude: cLon)
                                        focusToken += 1
                                    default:
                                        // Piedra: centra suave y abre su ficha.
                                        focusFit = []
                                        focusCoord = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
                                        focusToken += 1
                                        openBlockSheet(b)
                                    }
                                }
                            }
                        },
                        onMapTap: (flow.waitingTap || (flow.correctionMode && flow.corrActive)) ? { coord in
                            if flow.waitingTap {
                                flow.waitingTap = false
                                if flow.proposeType == "BOULDER" { flow.boulderCoord = coord } else { flow.formCoord = coord }
                            } else {
                                flow.corrNew = coord
                            }
                        } : nil,
                        polylines: wallPolylines,
                        // Foco explícito al pulsar un parking en la lista (recentra
                        // el mapa ahí). Debe ir DESPUÉS de polylines: el init
                        // memberwise de Swift exige el mismo orden que la
                        // declaración del struct aunque los args vayan con nombre.
                        focusCoordinate: focusCoord,
                        // Enfocar acerca, pero NUNCA aleja: si ya te habías
                        // acercado con los dedos, tocar una piedra no debe
                        // devolverte atrás (antes tocaba rehacer el zoom al
                        // cerrar cada ficha).
                        nuncaAlejar: true,
                        focusToken: focusToken,
                        focusFitCoordinates: focusFit
                    )
                    .frame(height: height)


                    if let punto = confirmandoFoto {
                        // La foto ya está colocada: aceptar o moverla. El GPS se
                        // equivoca entre 10 y 30 metros y las piedras están a
                        // metros, así que la última palabra es del usuario.
                        mapBanner("LA FOTO SE HIZO AQUÍ · ¿ES DONDE ESTÁ LA PIEDRA?",
                                  accept: {
                                      confirmandoFoto = nil
                                      flow.boulderCoord = punto
                                  },
                                  aceptarTexto: "SÍ, SIGUE",
                                  cancelarTexto: "MOVERLA",
                                  cancel: {
                                      confirmandoFoto = nil
                                      flow.waitingTap = true   // toca tú el sitio
                                  })
                    } else if flow.waitingTap {
                        // Banner "PULSA EN EL MAPA" (parking/sector/piedra).
                        mapBanner("PULSA EN EL MAPA PARA FIJAR LA POSICIÓN",
                                  cancel: { flow.waitingTap = false })
                    } else if flow.correctionMode {
                        mapBanner(correctionBannerText,
                                  accept: (flow.corrActive && flow.corrNew != nil) ? { Task { await submitCorrection() } } : nil,
                                  cancel: cancelCorrection)
                    } else {
                        // Botón "+ PROPONER": pill flotante ARRIBA a la derecha —
                        // abajo lo tapaba la mini-ficha de parking/sector.
                        VStack {
                            if fullscreenMap { Spacer().frame(height: 54) }
                            HStack {
                                Spacer()
                                Button {
                                    // Ya no salimos de pantalla completa: el selector
                                    // se presenta desde mapArea (dentro del cover).
                                    flow.showTypePicker = true
                                } label: {
                                    Text(NSLocalizedString("detail_propose", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.6)
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 9)
                                        .background(Cumbre.terra)
                                        .clipShape(RoundedRectangle(cornerRadius: 11))
                                }
                                .buttonStyle(.plain)
                            }
                            Spacer()
                        }
                        .padding(10)
                        .frame(height: height)
                    }

                    // Ampliar / salir de pantalla completa — ARRIBA a la izquierda
                    // (debajo de los chips de estilo) + botonera lateral en fullscreen.
                    if !flow.waitingTap && !flow.correctionMode {
                        VStack {
                            HStack(alignment: .top) {
                                // A la IZQUIERDA: ampliar arriba y, debajo, la
                                // rosa de los vientos. En la botonera de la
                                // derecha desplazaba lo que ya estaba colocado.
                                VStack(spacing: 8) {
                                Button { fullscreenMap.toggle() } label: {
                                    Image(systemName: fullscreenMap
                                          ? "arrow.down.right.and.arrow.up.left"
                                          : "arrow.up.left.and.arrow.down.right")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(Cumbre.ink)
                                        .frame(width: 34, height: 34)
                                        .background(Cumbre.bg)
                                        .clipShape(Circle())
                                        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                }
                                Spacer()
                            }
                            .padding(.top, fullscreenMap ? 54 : 0)
                            Spacer()
                        }
                        .padding(10)
                        .frame(height: height)
                    }
                    if !flow.waitingTap && !flow.correctionMode {
                        // Botonera lateral (maqueta B): topo↔satélite de un toque
                        // + capas con la FORMA real del marcador.
                        VStack {
                            HStack {
                                Spacer()
                                VStack(spacing: 8) {
                                sideButton(active: true) {
                                    recenterOnSchool()
                                } content: {
                                    Image(systemName: "scope")
                                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                                }
                                sideButton(active: true) {
                                    mapStyle = (mapStyle == .satellite) ? .topo : .satellite
                                } content: {
                                    Image(systemName: "square.3.layers.3d")
                                        .font(.system(size: 16)).foregroundStyle(Cumbre.ink)
                                }
                                sideButton(active: !vm.hiddenTypes.contains("PARKING")) {
                                    vm.toggleLayer("PARKING")
                                } content: { parkingShape }
                                sideButton(active: !vm.hiddenTypes.contains("BLOCK")) {
                                    vm.toggleLayer("BLOCK")
                                } content: { stoneShape }
                                sideButton(active: !vm.hiddenTypes.contains("ZONE")) {
                                    vm.toggleLayer("ZONE")
                                } content: { zoneShape }
                                if let u = userCoord {
                                    sideButton(active: true) {
                                        focusFit = []
                                        focusCoord = u
                                        focusToken += 1
                                    } content: {
                                        Image(systemName: "location.fill")
                                            .font(.system(size: 14))
                                            .foregroundStyle(Color(parkingColor))
                                    }
                                }
                                }
                            }
                            Spacer()
                        }
                        .padding(.top, fullscreenMap ? 104 : 50)
                        .padding(.trailing, 10)
                        .frame(height: height)
                    }

                    // Mini-ficha flotante de parking/sector: informa y da CÓMO
                    // LLEGAR sin tapar el mapa.
                    if let mb = miniBlock, !flow.waitingTap, !flow.correctionMode {
                        VStack { Spacer(); miniBlockCard(mb) }
                            .frame(height: height)
                            .frame(maxWidth: .infinity)
                    }
                }
                // Sheets del flujo de PROPONER anclados al mapa: como mapArea se
                // usa igual en tarjeta y en pantalla completa (solo hay una
                // instancia activa a la vez), el selector/formularios se
                // presentan en el contexto correcto SIN salir de pantalla completa.
                .sheet(isPresented: $flow.showTypePicker) {
                    ContributionTypePicker(hayBorrador: borradorPiedra != nil) { type in
                        flow.showTypePicker = false
                        switch type {
                        case "PARKING", "SECTOR", "BOULDER": flow.proposeType = type; flow.waitingTap = true
                        // La escuela ya la sabemos: solo hace falta la foto.
                        case "BOULDER_PHOTO": eligiendoFoto = true
                        // Continuar donde lo dejaste: al formulario directo, sin
                        // pedir otra vez el sitio (ya esta en el borrador).
                        case "BOULDER_DRAFT":
                            if let b = borradorPiedra {
                                continuandoBorrador = true
                                flow.boulderCoord = CLLocationCoordinate2D(
                                    latitude: b.lat, longitude: b.lon)
                            }
                        case "CORRECTION": flow.startCorrection()
                        case "SCHOOL_NAME_CORRECTION": correctingSchoolName = true
                        case "SCHOOL_STYLE_CORRECTION": correctingSchoolStyle = true
                        default: break
                        }
                    }
                }
                .overlay {
                    if eligiendoFoto {
                        SubmitBlockPhotoFlow(
                            schools: [school],
                            escuelaFijada: school,
                            onOpenSchool: { _ in
                                eligiendoFoto = false
                                borradorPiedra = BoulderDraftStore.load(schoolId: school.id)
            consumirSemillaDeFoto()
                            },
                            onDismiss: { eligiendoFoto = false })
                        .allowsHitTesting(false)
                    }
                }
                .sheet(item: coordItem) { item in
                    ContributionFormSheet(type: flow.proposeType, schoolId: school.id, coord: item.coord) { ok in
                        flow.formCoord = nil
                        if ok { afterSubmit() }
                    }
                }
                .sheet(item: boulderCoordItem) { item in
                    BoulderFormSheet(schoolId: school.id, coord: item.coord,
                                     sectors: vm.blocks.filter { $0.type.uppercased() == "ZONE" },
                                     contextMarkers: traceContextMarkers,
                                     seedPhoto: fotoSemilla?.image,
                                     seedAspect: fotoSemilla?.aspect,
                                     borrador: continuandoBorrador ? borradorPiedra : nil) { ok in
                        flow.boulderCoord = nil
                        fotoSemilla = nil
                        continuandoBorrador = false
                        // Enviada o descartada: el borrador ya no aplica.
                        if ok { BoulderDraftStore.clear(schoolId: school.id) }
                        borradorPiedra = BoulderDraftStore.load(schoolId: school.id)
                        if ok { afterSubmit() }
                    }
                }
                .sheet(isPresented: $flow.showSuccess) { ContributionSuccessSheet(isAdmin: vm.isAdmin) }
                .sheet(isPresented: $correctingSchoolName) {
                    SchoolNameCorrectionSheet(schoolId: school.id, currentName: school.name,
                                              coord: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)) { ok in
                        if ok { afterSubmit() }
                    }
                }
                .sheet(isPresented: $correctingSchoolStyle) {
                    SchoolStyleCorrectionSheet(schoolId: school.id, currentStyle: school.style,
                                               coord: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)) { ok in
                        if ok { afterSubmit() }
                    }
                }
                .fullScreenCover(item: $followingApproach) { a in
                    ApproachFollowView(approach: a, schoolName: school.name, isAdmin: vm.isAdmin) {
                        approachesLoader.reload(schoolId: school.id)
                    }
                }
                // Ficha de piedra y sus acciones — también ancladas al mapa para
                // que se presenten sobre la pantalla completa sin tener que salir.
                .sheet(item: $selectedBlock) { b in
                    BlockInfoSheet(
                        block: b,
                        sectors: vm.blocks.filter { $0.type.uppercased() == "ZONE" },
                        schoolName: school.name,
                        highlightVia: vm.searchHighlight ?? openVia,
                        onEditLines: { editLinesBlock = b },
                        onAssignSector: { assignSectorBlock = b },
                        onDelete: vm.isAdmin ? {
                            let id = b.id
                            selectedBlock = nil
                            Task {
                                try? await AppDependencies.shared.container.deleteBlock.invoke(blockId: id)
                                if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
                            }
                        } : nil,
                        onRateLine: { lineId, stars in
                            Task {
                                let rateLine = AppDependencies.shared.container.rateLine
                                if stars > 0 {
                                    _ = try? await rateLine.rate(blockId: b.id, lineId: lineId, stars: Int32(stars))
                                } else {
                                    _ = try? await rateLine.unrate(blockId: b.id, lineId: lineId)
                                }
                                if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
                            }
                        },
                        // Filtro de grado activo → sus vías fuera de rango se
                        // atenúan dentro de la ficha (nil = sin filtro).
                        gradeMatchingLineIds: gradeFilterActive ? gradeFilter.matchingLineIds : nil)
                }
                .sheet(item: $editLinesBlock) { b in
                    EditLinesSheet(block: b, schoolId: school.id, focusVia: openVia,
                                   contextMarkers: traceContextMarkers) { ok in
                        editLinesBlock = nil
                        if ok { afterSubmit() }
                    }
                }
                .sheet(item: $assignSectorBlock) { b in
                    AssignSectorSheet(block: b, schoolId: school.id,
                                      sectors: vm.blocks.filter { $0.type.uppercased() == "ZONE" }) { ok in
                        assignSectorBlock = nil
                        if ok { afterSubmit() }
                    }
                }
    }

    /// Buscador de vías/bloques (como el buscador de escuelas de la lista).
    @ViewBuilder
    private var searchBar: some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField("Buscar vías/bloques…", text: $vm.searchQuery)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(Cumbre.paper)
                .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
            orientationChips
            let q = vm.searchQuery.trimmingCharacters(in: .whitespaces)
            if q.count >= 2 {
                let hits = vm.searchHits(q)
                if hits.isEmpty {
                    Text("Sin resultados en esta escuela")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                } else {
                    VStack(spacing: 0) {
                        ForEach(hits, id: \.id) { h in
                            Button {
                                vm.searchQuery = ""
                                vm.searchHighlight = h.viaName
                                selectedBlock = h.block
                            } label: {
                                HStack {
                                    Text(h.label).font(.system(size: 14))
                                        .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    Spacer()
                                    Text(h.sub).font(.system(size: 12))
                                        .foregroundStyle(Cumbre.ink3).lineLimit(1)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 9)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 4)
        .fullScreenCover(isPresented: $fullscreenMap) {
            ZStack(alignment: .bottomTrailing) {
                Color.black.ignoresSafeArea()
                mapArea(height: UIScreen.main.bounds.height)
                    .ignoresSafeArea()
            }
        }
    }

    private static let aspectOrder = Aspect.shared.ALL

    /// Chips TODAS / N / NE… con el nº de piedras de cada consenso.
    @ViewBuilder
    private var orientationChips: some View {
        if !blockOrientations.isEmpty {
            let counts = Dictionary(grouping: blockOrientations.values, by: { $0 })
                .mapValues { $0.count }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    orientationChip("TODAS", active: orientationFilter == nil) {
                        orientationFilter = nil
                    }
                    ForEach(Self.aspectOrder, id: \.self) { aspect in
                        if let n = counts[aspect], n > 0 {
                            orientationChip("\(aspect) · \(n)", active: orientationFilter == aspect) {
                                orientationFilter = orientationFilter == aspect ? nil : aspect
                            }
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func orientationChip(_ label: String, active: Bool,
                                 action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Cumbre.mono(10, .bold))
                .foregroundStyle(active ? .white : Cumbre.terra)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 6)
                    .fill(active ? Cumbre.terra : Cumbre.paper))
                .overlay(RoundedRectangle(cornerRadius: 6)
                    .stroke(Cumbre.terra.opacity(active ? 1 : 0.55),
                            style: StrokeStyle(lineWidth: 1, dash: active ? [] : [4, 3])))
        }
        .buttonStyle(.plain)
    }

    /// Vuelve al encuadre inicial de la escuela (todos los marcadores).
    private func recenterOnSchool() {
        var coords = [CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)]
        coords += vm.blocks.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        if coords.count >= 2 { focusFit = coords } else {
            focusFit = []
            focusCoord = coords[0]
        }
        focusToken += 1
    }

    /// Botón circular de la botonera lateral; apagado = capa oculta.
    private func sideButton<C: View>(active: Bool, action: @escaping () -> Void,
                                     @ViewBuilder content: () -> C) -> some View {
        Button(action: action) {
            content()
                .frame(width: 34, height: 34)
                .background(Cumbre.bg)
                .clipShape(Circle())
                .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
                .opacity(active ? 1 : 0.4)
        }
        .buttonStyle(.plain)
    }

    /// P cuadrada azul — la forma real del marcador de parking.
    private var parkingShape: some View {
        Text("P").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(parkingColor))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    /// Polígono irregular terra — la forma real del marcador de piedra.
    private var stoneShape: some View {
        StonePolygon().fill(Color(blockColor))
            .overlay(StonePolygon().stroke(.white, lineWidth: 1.2))
            .frame(width: 24, height: 22)
    }

    /// Círculo verde con Z — la forma real del marcador de zona.
    private var zoneShape: some View {
        Text("Z").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(zoneColor))
            .clipShape(Circle())
    }

    /// Abre la ficha de una piedra. Ya NO sale de pantalla completa: el sheet
    /// cuelga de mapArea, así que se presenta sobre el cover.
    private func openBlockSheet(_ b: Block) {
        selectedBlock = b
    }

    private func afterSubmit() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            flow.showSuccess = true
            if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
        }
    }

    // Wrapper Identifiable para presentar el formulario de piedra.
    private var boulderCoordItem: Binding<CoordItem?> {
        Binding(get: { flow.boulderCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { flow.boulderCoord = nil } })
    }

    // MARK: - Corrección de posición

    private func selectCorrectionTarget(_ id: String) {
        if let b = vm.blocks.first(where: { $0.id == id }) {
            flow.corrTargetId = b.id
            flow.corrTargetName = b.name.isEmpty ? typeLabel(b.type) : b.name
            flow.corrOld = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
        } else if id == school.id {
            flow.corrTargetId = nil
            flow.corrTargetName = "la escuela"
            flow.corrOld = CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)
        } else { return }
        flow.corrActive = true
        flow.corrNew = nil
    }

    private func cancelCorrection() {
        flow.resetCorrection()
    }

    private func submitCorrection() async {
        guard let old = flow.corrOld, let nw = flow.corrNew else { return }
        let req = ContributionRequest(
            type: "POSITION_CORRECTION", name: flow.corrTargetName,
            lat: old.latitude, lon: old.longitude,
            notes: nil, description: nil,
            proposedLat: KotlinDouble(double: nw.latitude),
            proposedLon: KotlinDouble(double: nw.longitude), correctionReason: nil,
            targetBlockId: flow.corrTargetId, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: nil, topoLinesJson: nil, discipline: nil,
            geometry: nil, path: nil, direction: nil, orientationsJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: school.id, req: req)) != nil
        cancelCorrection()
        if ok { afterSubmit() }
    }

    private var correctionBannerText: String {
        if !flow.corrActive { return "PULSA EL MARCADOR QUE QUIERES MOVER" }
        if flow.corrNew == nil { return "MOVIENDO «\(flow.corrTargetName)» · PULSA LA NUEVA POSICIÓN" }
        return "POSICIÓN FIJADA · PULSA OTRA VEZ PARA RECORREGIR O ACEPTA"
    }

    /// Recoge la foto que dejó el atajo "piedra desde una foto" y prepara el
    /// mapa para confirmar el sitio.
    ///
    /// Se llama desde DOS sitios: al entrar en la escuela (atajo lanzado desde
    /// la lista de escuelas) y justo después de elegir la foto dentro de la
    /// propia escuela. Es el mismo trabajo, así que vive en un solo sitio.
    private func consumirSemillaDeFoto() {
        // OJO: NO se exige que `fotoSemilla` este vacia. Se exigia, y si quedaba
        // una foto a medias —flujo abandonado sin cerrar el formulario— la
        // SIGUIENTE no entraba y el boton parecia muerto (cazado por Rodrigo).
        // Manda la foto nueva: es lo ultimo que ha pedido el usuario.
        guard let semilla = PhotoProposalSeedStore.shared.take(schoolId: school.id) else { return }
        fotoSemilla = semilla
        expanded = true
        flow.proposeType = "BOULDER"
        // Se coloca DONDE SE HIZO la foto y solo queda confirmarlo. El mapa se
        // centra ahí para poder juzgar si el sitio es bueno: el GPS se equivoca
        // entre 10 y 30 metros y las piedras están a metros.
        confirmandoFoto = CLLocationCoordinate2D(latitude: semilla.lat,
                                                 longitude: semilla.lon)
        // A pantalla completa: la pregunta "¿es el sitio?" solo se puede
        // responder viendo el mapa con holgura.
        fullscreenMap = true
        focusCoord = confirmandoFoto
        // Dos veces a proposito: al pasar a pantalla completa el mapa se RECREA
        // y la primera orden puede perderse con el mapa viejo.
        focusToken += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            if confirmandoFoto != nil { focusToken += 1 }
        }
    }

    /// Banner superior + botón cancelar (y aceptar opcional) sobre el mapa.
    private func mapBanner(_ text: String, accept: (() -> Void)? = nil,
                           aceptarTexto: String = "ACEPTAR",
                           cancelarTexto: String = "CANCELAR",
                           cancel: @escaping () -> Void) -> some View {
        // Los botones van PEGADOS al texto, dentro de la misma tira terracota:
        // sueltos abajo y en blanco y negro parecían de otra cosa. Espejo del
        // banner de Android.
        VStack {
            // ABAJO, como en Android: arriba tapaba el mapa justo donde hay que
            // mirar, y ademas cada app lo ponia en un sitio.
            Spacer()
            VStack(spacing: 8) {
                Text(text)
                    .font(Cumbre.mono(11, .bold)).tracking(0.6).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    if let accept {
                        Button(aceptarTexto, action: accept)
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(.white)
                    }
                    Button(cancelarTexto, action: cancel)
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .overlay(Rectangle().stroke(.white, lineWidth: 1))
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity).background(Cumbre.terra)
        }
    }

    // Wrapper Identifiable para presentar el formulario con la coordenada fijada.
    private var coordItem: Binding<CoordItem?> {
        Binding(get: { flow.formCoord.map { CoordItem(coord: $0) } },
                set: { if $0 == nil { flow.formCoord = nil } })
    }

    /// Abre la piedra que contiene la vía indicada (deep-link del diario): busca
    /// el bloque cuya vía coincide por nombre (o el bloque con ese nombre).
    private func maybeAutoOpen() {
        guard !didAutoOpen, let via = openVia, !via.isEmpty, !vm.blocks.isEmpty else { return }
        // Por id ESTABLE primero (enlaces compartidos /s/v/{...}/{lineId});
        // si no, por nombre (deep-link clásico del diario).
        let target = vm.blocks.first { b in
            b.lines.contains { $0.id == via }
        } ?? vm.blocks.first { b in
            b.lines.contains { $0.name.caseInsensitiveCompare(via) == .orderedSame }
        } ?? vm.blocks.first { $0.name.caseInsensitiveCompare(via) == .orderedSame }
            // Post "piedra nueva" del feed: openVia trae el ID de la piedra.
            ?? vm.blocks.first { $0.id == via }
        if let target {
            didAutoOpen = true
            selectedBlock = target
        }
    }


    // Muros (geometry=LINE) dibujados como polilínea terra en el mapa. Se ocultan
    // si la piedra pertenece a un sector colapsado (igual que su marcador).
    private var wallPolylines: [CumbrePolyline] {
        vm.blocks.compactMap { b in
            // Solo PIEDRAS: un parking/zona con path corrupto no debe estirarse
            // en una línea (se veía azul, color del tipo).
            guard b.type.uppercased() == "BLOCK", b.geometry.uppercased() == "LINE" else { return nil }
            if vm.hiddenTypes.contains("BLOCK") { return nil }
            if let sid = b.sectorBlockId, vm.collapsedSectors.contains(sid) { return nil }
            // Filtro de orientación: los muros se ocultan igual que sus pines.
            if let f = orientationFilter, blockOrientations[b.id] != f { return nil }
            let pts = parseWallPath(b.path)
            guard pts.count >= 2 else { return nil }
            return CumbrePolyline(id: "wall-\(b.id)", coordinates: pts, color: blockColor, width: 5)
        }
    }

    /// Coordenada del marcador de una piedra: si es MURO, el centro de la
    /// polilínea (no la esquina donde se fijó al crearla → salía en un lateral).
    private func blockMarkerCoord(_ b: Block) -> CLLocationCoordinate2D {
        if b.geometry.uppercased() == "LINE" {
            let pts = parseWallPath(b.path)
            if pts.count >= 2 { return pts[pts.count / 2] }
        }
        return CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
    }

    /// Resultado del filtro de grado sobre las piedras cargadas. Espejo de
    /// `filterBlocksByGrades` en shared (GradeFilter.kt).
    private var gradeFilter: GradeFilterResult {
        filterBlocksByGrades(vm.blocks, selectedGrades: selectedGrades)
    }

    /// ¿Hay filtro de grado puesto? Si no, nada se atenúa.
    private var gradeFilterActive: Bool { !selectedGrades.isEmpty }

    /// Solo tiene sentido el filtro Vía/Bloque si la escuela es de AMBOS
    /// estilos a la vez (feature 2: school.style = "Vía,Bloque").
    private var schoolHasBothStyles: Bool {
        Set(school.style?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } ?? []).count > 1
    }

    private var styleFilterRow: some View {
        HStack(spacing: 8) {
            ForEach(["Vía", "Bloque"], id: \.self) { opt in
                let active = styleFilter.contains(opt)
                Button {
                    if active { styleFilter.remove(opt) } else { styleFilter.insert(opt) }
                } label: {
                    Text(opt.uppercased()).font(Cumbre.mono(11, .bold)).tracking(0.5)
                        .foregroundStyle(active ? .white : Cumbre.ink2)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(active ? Cumbre.terra : Cumbre.paper)
                        .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(.horizontal, 4)
    }

    /// "ROUTE" en el backend = estilo "Vía"; "BOULDER" = "Bloque".
    private func styleLabel(_ discipline: String) -> String {
        discipline.uppercased() == "ROUTE" ? "Vía" : "Bloque"
    }

    /// ¿Esta piedra/sector pasa el filtro Vía/Bloque? Vacío = sin filtrar.
    /// Un sector sin piedras dentro (sectorDisciplines vacío) siempre se ve —
    /// no hay disciplina que comparar todavía (evita que desaparezca).
    private func matchesStyleFilter(_ b: Block) -> Bool {
        if styleFilter.isEmpty { return true }
        switch b.type.uppercased() {
        case "BLOCK": return styleFilter.contains(styleLabel(b.discipline))
        case "ZONE":
            let disciplines = b.sectorDisciplines ?? []
            if disciplines.isEmpty { return true }
            return disciplines.contains { styleFilter.contains(styleLabel($0)) }
        default: return true
        }
    }

    /// Color del pin de una piedra, atenuado si el filtro de grado está puesto
    /// y esa piedra no tiene ninguna vía en rango. Se ATENÚA, no se oculta:
    /// así se conserva el contexto del sector (decisión de diseño §7.3).
    private func blockColorFiltered(_ b: Block) -> UIColor {
        let base = color(for: b.type)
        guard gradeFilterActive, b.type.uppercased() == "BLOCK",
              !gradeFilter.matchingBlockIds.contains(b.id) else { return base }
        return base.withAlphaComponent(0.35)
    }

    private var markers: [CumbreMarker] {
        var ms = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in vm.blocks {
            // Capa apagada (botonera lateral) → no se pinta.
            if vm.hiddenTypes.contains(b.type.uppercased()) { continue }
            // Piedra oculta si pertenece a un sector colapsado.
            if b.type.uppercased() == "BLOCK", let sid = b.sectorBlockId, vm.collapsedSectors.contains(sid) {
                continue
            }
            // Filtro de orientación activo: solo piedras con ESE consenso.
            if b.type.uppercased() == "BLOCK", let f = orientationFilter,
               blockOrientations[b.id] != f {
                continue
            }
            // Filtro Vía/Bloque (solo en escuelas con ambos estilos).
            if !matchesStyleFilter(b) { continue }
            // Zona colapsada: muestra cuántas piedras tiene ocultas.
            let collapsed = b.type.uppercased() == "ZONE" && vm.collapsedSectors.contains(b.id)
            let hidden = collapsed ? vm.blocks.filter { $0.sectorBlockId == b.id }.count : 0
            ms.append(CumbreMarker(
                id: b.id,
                coordinate: blockMarkerCoord(b),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type),
                color: blockColorFiltered(b),
                name: collapsed && hidden > 0 ? "\(b.name) (+\(hidden))" : b.name,
                // Nombre del sector visible al acercar (sin tener que pulsarlo).
                showName: b.type.uppercased() == "ZONE" && !b.name.isEmpty && mapZoom >= 13.5))
        }
        // Orden de pintado: piedras primero → sectores/parkings/escuela ENCIMA
        // (no quedan tapados por los pines de piedra).
        ms.sort { a, b in
            func rank(_ k: MarkerKind) -> Int {
                switch k { case .block: return 0; case .parking: return 1
                case .zone: return 2; case .school: return 3; default: return 4 }
            }
            return rank(a.kind) < rank(b.kind)
        }
        // Mi ubicación (punto azul) para orientarme en el sector.
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user,
                                   score: headingProvider.heading))
        }
        // Punto donde se hizo la foto, pendiente de confirmar. Sin marcador, la
        // pregunta "¿es el sitio?" no se puede responder: no se ve dónde cae.
        if let f = confirmandoFoto {
            ms.append(CumbreMarker(
                id: "__FOTO__", coordinate: f, title: "Aquí se hizo la foto",
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        // Fantasma de la nueva posición al corregir.
        if let nw = flow.corrNew {
            ms.append(CumbreMarker(
                id: "__GHOST__", coordinate: nw, title: "Nueva posición",
                kind: .block, color: UIColor(hex: 0xF59E0B), name: "★"))
        }
        return ms
    }

    private func markerKind(for type: String) -> MarkerKind {
        switch type.uppercased() {
        case "PARKING": return .parking
        case "ZONE": return .zone
        default: return .block
        }
    }

    /// Contexto de solo lectura para el mapa de trazar muro: escuela + todas las
    /// piedras/sectores/parkings + mi ubicación. Sin overlays del trazo ni
    /// fantasmas de corrección (eso lo pinta WallTraceSheet aparte).
    private var traceContextMarkers: [CumbreMarker] {
        var ms: [CumbreMarker] = [CumbreMarker(
            id: school.id,
            coordinate: CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon),
            title: school.name, kind: .school, color: schoolColor)]
        for b in vm.blocks {
            ms.append(CumbreMarker(
                id: b.id, coordinate: blockMarkerCoord(b),
                title: b.name.isEmpty ? b.type : b.name,
                subtitle: typeLabel(b.type),
                kind: markerKind(for: b.type), color: color(for: b.type), name: b.name))
        }
        if let u = userCoord {
            ms.append(CumbreMarker(id: "__USER__", coordinate: u, title: "", kind: .user,
                                   score: headingProvider.heading))
        }
        return ms
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem("Parking", Color(parkingColor))
            legendItem("Piedra", Color(blockColor))
            legendItem("Zona", Color(zoneColor))
        }
        .padding(.horizontal, 16).padding(.top, 8)
    }
    private func legendItem(_ t: String, _ c: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(c).frame(width: 9, height: 9).overlay(Circle().stroke(.white, lineWidth: 1))
            Text(t).font(Cumbre.mono(10)).foregroundStyle(Cumbre.ink3)
        }
    }

    // Lo importante para llegar: desde qué parking se va andando. Orden
    // alfabético (NO por cercanía — debe tener sentido también viéndolo desde
    // casa, lejos de la escuela); la distancia es solo dato informativo. Tocar
    // uno centra el mapa en esa zona (para ver qué sectores/piedras hay
    // alrededor) y abre su ficha, que ya tiene "CÓMO LLEGAR".
    /// Vuela a la "zona" de un parking: encuadra parking + sectores/piedras a
    /// ≤800 m (expandiendo colapsados) — vista de ~1,5-2 km. Sin nada cerca,
    /// zoom equivalente. Espejo de flyToParkingZone de Android.
    private func flyToParkingZone(_ p: Block) {
        let near = vm.blocks.filter { b in
            b.id != p.id &&
            Geo.shared.haversineKm(lat1: p.lat, lon1: p.lon, lat2: b.lat, lon2: b.lon) <= 0.8
        }
        for b in near {
            if b.type.uppercased() == "ZONE" { vm.collapsedSectors.remove(b.id) }
            if let sid = b.sectorBlockId { vm.collapsedSectors.remove(sid) }
        }
        // Centrar EN el parking pulsado (no en el centroide del parking + cercanos,
        // que dejaba el parking descentrado). Paridad con Android.
        focusFit = []
        focusCoord = CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon)
        focusToken += 1
    }

    /// Mini-ficha flotante de parking/sector: una línea de alto, el mapa se
    /// sigue viendo. Admin: ✎ (abre la ficha completa) y 🗑 a la izquierda de
    /// CÓMO LLEGAR. Sectores con piedras: toggle VER/OCULTAR explícito.
    private func miniBlockCard(_ mb: Block) -> some View {
        let isParking = mb.type.uppercased() == "PARKING"
        let stoneCount = vm.blocks.filter { $0.sectorBlockId == mb.id }.count
        let collapsed = vm.collapsedSectors.contains(mb.id)
        var subtitle = isParking ? "Parking" : "Sector"
        if !isParking && stoneCount > 0 { subtitle += " · \(stoneCount) piedra\(stoneCount == 1 ? "" : "s")" }
        if let u = userCoord {
            let km = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: mb.lat, lon2: mb.lon)
            subtitle += km < 1 ? " · \(Int(km * 1000)) m" : String(format: " · %.1f km", km)
        }
        return VStack(spacing: 6) {
            HStack(spacing: 8) {
                Text(isParking ? "P" : "Z")
                    .font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 24, height: 24)
                    .background(isParking ? Color(parkingColor) : Color(zoneColor))
                    .clipShape(RoundedRectangle(cornerRadius: isParking ? 6 : 12))
                VStack(alignment: .leading, spacing: 0) {
                    Text(mb.name.isEmpty ? (isParking ? "Parking" : "Sector") : mb.name)
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    Text(subtitle).font(.system(size: 11)).foregroundStyle(Cumbre.ink3).lineLimit(1)
                }
                Spacer(minLength: 4)
                if vm.isAdmin {
                    Button { miniBlock = nil; openBlockSheet(mb) } label: {
                        Image(systemName: "pencil").font(.system(size: 15)).foregroundStyle(Cumbre.ink2).padding(4)
                    }.buttonStyle(.plain)
                    Button { confirmDeleteMini = true } label: {
                        Image(systemName: "trash").font(.system(size: 14)).foregroundStyle(Cumbre.bad).padding(4)
                    }.buttonStyle(.plain)
                }
                // Si hay un camino grabado que llega hasta este sector/parking,
                // ofrecer seguirlo directo desde aquí — no hace falta ir a la
                // sección APROXIMACIONES a buscarlo.
                if let approach = approachesLoader.approach(to: mb.id) {
                    Button {
                        miniBlock = nil
                        followingApproach = approach
                    } label: {
                        Text("SEGUIR").font(Cumbre.mono(10, .bold)).tracking(0.8)
                            .foregroundStyle(Cumbre.terra).padding(.horizontal, 10).padding(.vertical, 8)
                            .overlay(RoundedRectangle(cornerRadius: 9).stroke(Cumbre.terra, lineWidth: 1))
                    }.buttonStyle(.plain)
                }
                Button {
                    let g = URL(string: "comgooglemaps://?daddr=\(mb.lat),\(mb.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(mb.lat),\(mb.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                } label: {
                    Text("CÓMO LLEGAR").font(Cumbre.mono(10, .bold)).tracking(0.8)
                        .foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 8)
                        .background(Cumbre.terra).clipShape(RoundedRectangle(cornerRadius: 9))
                }.buttonStyle(.plain)
                Button { miniBlock = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 13)).foregroundStyle(Cumbre.ink3).padding(4)
                }.buttonStyle(.plain)
            }
            if !isParking && stoneCount > 0 {
                Button {
                    if collapsed { vm.collapsedSectors.remove(mb.id) } else { vm.collapsedSectors.insert(mb.id) }
                } label: {
                    Text(collapsed ? "VER PIEDRAS" : "OCULTAR PIEDRAS")
                        .font(Cumbre.mono(10, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 7)
                        .overlay(RoundedRectangle(cornerRadius: 9).stroke(Cumbre.rule, lineWidth: 1))
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .background(Cumbre.bg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 8).padding(.bottom, 8)
    }

    private var parkingsList: some View {
        let parkings = vm.blocks.filter { $0.type.uppercased() == "PARKING" }.sorted { $0.name < $1.name }
        return Group {
            if !parkings.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("PARKINGS").eyebrow()
                        .padding(.horizontal, 16).padding(.top, 10)
                    // Chips horizontales (una línea): cada uno lleva el mapa a SU
                    // zona (mini-ficha + encuadre parking + cercanos).
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(parkings, id: \.id) { p in
                                Button {
                                    miniBlock = p
                                    flyToParkingZone(p)
                                } label: {
                                    HStack(spacing: 6) {
                                        Text("P")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundStyle(.white)
                                            .frame(width: 20, height: 20)
                                            .background(Color(parkingColor))
                                            .clipShape(RoundedRectangle(cornerRadius: 5))
                                        Text(chipLabel(p))
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    }
                                    .padding(.horizontal, 10).padding(.vertical, 7)
                                    .background(Cumbre.paper)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 6)
                    }
                }
            }
        }
    }

    /// Etiqueta del chip de parking: nombre + distancia si hay ubicación.
    private func chipLabel(_ p: Block) -> String {
        var label = p.name.isEmpty ? "Parking" : p.name
        if let u = userCoord {
            let km = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: p.lat, lon2: p.lon)
            label += km < 1 ? " · \(Int(km * 1000)) m" : String(format: " · %.1f km", km)
        }
        return label
    }

    private func color(for type: String) -> UIColor {
        switch type.uppercased() {
        case "PARKING": return parkingColor
        case "ZONE": return zoneColor
        default: return blockColor
        }
    }
    private func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return "ZONA"
        default: return "PIEDRA"
        }
    }
}

/// Coordenada Identifiable para presentar el formulario por sheet(item:).
private struct CoordItem: Identifiable {
    let coord: CLLocationCoordinate2D
    var id: String { "\(coord.latitude),\(coord.longitude)" }
}

/// Selector de tipo de propuesta — espejo de TypePickerDialog.kt. PARKING está
/// activo; el resto (piedra/sector/corregir) llegará en próximas iteraciones.

struct StonePolygon: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width, h = rect.height
        p.move(to: CGPoint(x: 0.16*w, y: 0.78*h))
        p.addLine(to: CGPoint(x: 0.07*w, y: 0.42*h))
        p.addLine(to: CGPoint(x: 0.30*w, y: 0.13*h))
        p.addLine(to: CGPoint(x: 0.72*w, y: 0.08*h))
        p.addLine(to: CGPoint(x: 0.94*w, y: 0.38*h))
        p.addLine(to: CGPoint(x: 0.86*w, y: 0.74*h))
        p.addLine(to: CGPoint(x: 0.54*w, y: 0.92*h))
        p.closeSubpath()
        return p
    }
}
