import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Mapa de una escuela: buscador, orientación, botones laterales, correcciones, banners y semilla de foto.

extension SchoolMapSection {
    @ViewBuilder
    var searchBar: some View {
        VStack(alignment: .leading, spacing: 4) {
            if showsSearch {
                TextField("Buscar vías/bloques…", text: $vm.searchQuery)
                    .textFieldStyle(.plain)
                    .font(.system(size: 14))
                    .padding(.horizontal, 12).padding(.vertical, 9)
                    .background(Cumbre.paper)
                    .overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                    .dismissKeyboardOnSubmit()
            }
            let q = vm.searchQuery.trimmingCharacters(in: .whitespaces)
            if showsSearch && q.count >= 2 {
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
                                    if let g = h.grade, let split = h.lineId.flatMap({ gradeSplits.splits[$0] }) {
                                        // La altura parte la opinión: el grado va en dos colores.
                                        HStack(spacing: 6) {
                                            Text(h.name).font(.system(size: 14))
                                                .foregroundStyle(Cumbre.ink).lineLimit(1)
                                            GradePill(grade: g, small: true, split: split)
                                        }
                                    } else {
                                        Text(h.label).font(.system(size: 14))
                                            .foregroundStyle(Cumbre.ink).lineLimit(1)
                                    }
                                    Spacer()
                                    Text(h.sub).font(.system(size: 12))
                                        .foregroundStyle(Cumbre.ink3).lineLimit(1)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 9)
                                .contentShape(Rectangle())
                                .onAppear { gradeSplits.want(h.lineId) }
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

    static let aspectOrder = Aspect.shared.ALL

    /// Filtro de orientación en UN botón con brújula (antes, una fila de chips "N · 4", "NE · 5"…). Muestra la elegida.
    @ViewBuilder var orientationMenu: some View {
        if !blockOrientations.isEmpty {
            let counts = Dictionary(grouping: blockOrientations.values, by: { $0 }).mapValues { $0.count }
            Menu {
                Button { orientationFilter = nil } label: {
                    if orientationFilter == nil { Label(L("TODAS"), systemImage: "checkmark") } else { Text(L("TODAS")) }
                }
                ForEach(Self.aspectOrder, id: \.self) { aspect in
                    if let n = counts[aspect], n > 0 {
                        Button { orientationFilter = (orientationFilter == aspect ? nil : aspect) } label: {
                            if orientationFilter == aspect {
                                Label("\(aspectLabel(aspect)) · \(n)", systemImage: "checkmark")
                            } else {
                                Text("\(aspectLabel(aspect)) · \(n)")
                            }
                        }
                    }
                }
            } label: {
                // Terracota y con flecha: se lee como un botón (la brújula sola parecía un adorno).
                HStack(spacing: 4) {
                    Image(systemName: "safari").font(.system(size: 14, weight: .semibold))
                    if let sel = orientationFilter {
                        Text(aspectLabel(sel)).font(Cumbre.mono(10.5, .bold))
                    } else {
                        Text(L("ORIENT.")).font(Cumbre.mono(10, .bold)).tracking(0.6)
                    }
                    Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold))
                }
                .foregroundStyle(orientationFilter == nil ? Cumbre.terra : Color.white)
                .padding(.horizontal, 10).padding(.vertical, 7)
                .background(orientationFilter == nil ? Cumbre.terraBg : Cumbre.terraFill, in: Capsule())
                .overlay(Capsule().stroke(Cumbre.terra, lineWidth: orientationFilter == nil ? 1.5 : 1))
            }
            .guideTarget("detail_map.orientation")
        }
    }

    /// Vuelve al encuadre inicial de la escuela (todos los marcadores).
    func recenterOnSchool() {
        var coords = [CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)]
        coords += vm.blocks.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        if coords.count >= 2 { focusFit = coords } else {
            focusFit = []
            focusCoord = coords[0]
        }
        focusToken += 1
    }

    /// Botón circular de la botonera lateral; apagado = capa oculta.
    func sideButton<C: View>(active: Bool, action: @escaping () -> Void,
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
    var parkingShape: some View {
        Text("P").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(parkingColor))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    /// Polígono irregular terra — la forma real del marcador de piedra.
    var stoneShape: some View {
        StonePolygon().fill(Color(blockColor))
            .overlay(StonePolygon().stroke(.white, lineWidth: 1.2))
            .frame(width: 24, height: 22)
    }

    /// Círculo verde con Z — la forma real del marcador de zona.
    var zoneShape: some View {
        Text("S").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
            .frame(width: 22, height: 22)
            .background(Color(zoneColor))
            .clipShape(Circle())
    }

    /// Abre la ficha de una piedra. Ya NO sale de pantalla completa: el sheet
    /// cuelga de mapArea, así que se presenta sobre el cover.
    func openBlockSheet(_ b: Block) {
        selectedBlock = b
    }

    func afterSubmit() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            flow.showSuccess = true
            if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
        }
    }

    // Wrapper Identifiable para presentar el formulario de piedra.
    var boulderCoordItem: Binding<MapCoordItem?> {
        Binding(get: { flow.boulderCoord.map { MapCoordItem(coord: $0) } },
                set: { if $0 == nil { flow.boulderCoord = nil } })
    }

    // MARK: - Corrección de posición

    func selectCorrectionTarget(_ id: String) {
        if let b = vm.blocks.first(where: { $0.id == id }) {
            flow.corrTargetId = b.id
            flow.corrTargetName = b.name.isEmpty ? typeLabel(b.type) : b.name
            flow.corrOld = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)
        } else if id == school.id {
            flow.corrTargetId = nil
            flow.corrTargetName = L("la escuela")
            flow.corrOld = CLLocationCoordinate2D(latitude: school.lat, longitude: school.lon)
        } else { return }
        flow.corrActive = true
        flow.corrNew = nil
    }

    func cancelCorrection() {
        flow.resetCorrection()
    }

    func submitCorrection() async {
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

    var correctionBannerText: String {
        if !flow.corrActive { return L("PULSA EL MARCADOR QUE QUIERES MOVER") }
        if flow.corrNew == nil { return L("MOVIENDO «%@» · PULSA LA NUEVA POSICIÓN", flow.corrTargetName) }
        return L("POSICIÓN FIJADA · PULSA OTRA VEZ PARA RECORREGIR O ACEPTA")
    }

    /// Recoge la foto que dejó el atajo "piedra desde una foto" y prepara el
    /// mapa para confirmar el sitio.
    ///
    /// Se llama desde DOS sitios: al entrar en la escuela (atajo lanzado desde
    /// la lista de escuelas) y justo después de elegir la foto dentro de la
    /// propia escuela. Es el mismo trabajo, así que vive en un solo sitio.
    func consumirSemillaDeFoto() {
        // OJO: NO se exige que `fotoSemilla` este vacia. Se exigia, y si quedaba
        // una foto a medias —flujo abandonado sin cerrar el formulario— la
        // SIGUIENTE no entraba y el boton parecia muerto (cazado por Rodrigo).
        // Manda la foto nueva: es lo ultimo que ha pedido el usuario.
        guard let semilla = PhotoProposalSeedStore.shared.take(schoolId: school.id) else { return }
        // withAnimation FORZADO: el HUD probó que estas mismas escrituras a
        // @State, hechas dentro de una cadena de closures que arranca en el
        // completion handler de UIKit (dismiss de la cámara), a veces NO
        // disparaban un repintado -- SwiftUI parecía "no darse cuenta" del
        // cambio (Rodrigo, 2026-08-22, HUD confirmó "colocando en mapa" pero
        // la pantalla se quedó igual). withAnimation fuerza una Transaction
        // real en vez de dejarlo a la transacción ambiente heredada.
        withAnimation(.default) {
            fotoSemilla = semilla
            expanded = true
            flow.proposeType = "BOULDER"
            // Se coloca DONDE SE HIZO la foto y solo queda confirmarlo. El mapa
            // se centra ahí para poder juzgar si el sitio es bueno: el GPS se
            // equivoca entre 10 y 30 metros y las piedras están a metros.
            confirmandoFoto = CLLocationCoordinate2D(latitude: semilla.lat,
                                                     longitude: semilla.lon)
            // A pantalla completa: la pregunta "¿es el sitio?" solo se puede
            // responder viendo el mapa con holgura.
            fullscreenMap = true
        }
        focusCoord = confirmandoFoto
        // Dos veces a proposito: al pasar a pantalla completa el mapa se RECREA
        // y la primera orden puede perderse con el mapa viejo.
        focusToken += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            if confirmandoFoto != nil { focusToken += 1 }
        }
    }

    /// Banner superior + botón cancelar (y aceptar opcional) sobre el mapa.
    func mapBanner(_ text: String, accept: (() -> Void)? = nil,
                           aceptarTexto: String = L("ACEPTAR"),
                           cancelarTexto: String = L("CANCELAR"),
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
            .frame(maxWidth: .infinity).background(Cumbre.terraFill)
        }
    }

    // Wrapper Identifiable para presentar el formulario con la coordenada fijada.
    var coordItem: Binding<MapCoordItem?> {
        Binding(get: { flow.formCoord.map { MapCoordItem(coord: $0) } },
                set: { if $0 == nil { flow.formCoord = nil } })
    }

    /// Tocar un sector (en el mapa o en «Sectores desde aquí» del parking): su tarjeta y encuadrarlo con sus piedras,
    /// desplegándolas. Zoom FIJO centrado entre sector y piedras (los encuadres calculados se iban a extremos).
    func selectZone(_ b: Block) {
        miniBlock = b
        let stones = vm.blocks.filter { $0.sectorBlockId == b.id }
        vm.collapsedSectors.remove(b.id)
        let pts = [CLLocationCoordinate2D(latitude: b.lat, longitude: b.lon)]
            + stones.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        let cLat = pts.map { $0.latitude }.reduce(0, +) / Double(pts.count)
        let cLon = pts.map { $0.longitude }.reduce(0, +) / Double(pts.count)
        focusFit = []
        focusCoord = CLLocationCoordinate2D(latitude: cLat, longitude: cLon)
        focusToken += 1
    }

    /// "Ver en mapa" (guía de BLOQUES): centra y acerca el mapa a la piedra, desplegando su sector, sin abrir la ficha.
    func maybeFocusBlock() {
        guard !didAutoOpen, let id = focusBlockId, !id.isEmpty,
              let b = vm.blocks.first(where: { $0.id == id }) else { return }
        didAutoOpen = true
        if let sid = b.sectorBlockId { vm.collapsedSectors.remove(sid) }
        focusFit = []
        focusCoord = blockMarkerCoord(b)
        focusZoom = 18.5
        focusToken += 1
        // La misma tarjeta que un parking o un sector: ABRIR la piedra o CÓMO LLEGAR.
        miniBlock = b
    }

    /// Abre la piedra que contiene la vía indicada (deep-link del diario): busca
    /// el bloque cuya vía coincide por nombre (o el bloque con ese nombre).
    func maybeAutoOpen() {
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

}
