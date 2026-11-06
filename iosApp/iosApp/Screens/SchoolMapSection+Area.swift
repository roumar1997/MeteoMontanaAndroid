import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Mapa de una escuela: el área del mapa con sus capas y botones.

extension SchoolMapSection {
    /// Mapa con todos sus overlays. Compartido entre el modo tarjeta (altura variable)
    /// y pantalla completa (estilo Radar).
    @ViewBuilder
    func mapArea(height: CGFloat) -> some View {
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
                            if movingBlock != nil { return }   // moviendo: los toques fijan el sitio
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
                                        selectZone(b)
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
                        onMapTap: (movingBlock != nil || flow.waitingTap || (flow.correctionMode && flow.corrActive)) ? { coord in
                            if movingBlock != nil {
                                movePending = coord
                            } else if flow.waitingTap {
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
                        focusZoom: focusZoom,
                        // Enfocar acerca, pero NUNCA aleja: si ya te habías
                        // acercado con los dedos, tocar una piedra no debe
                        // devolverte atrás (antes tocaba rehacer el zoom al
                        // cerrar cada ficha).
                        nuncaAlejar: true,
                        focusToken: focusToken,
                        focusFitCoordinates: focusFit,
                        focusMargenMinimo: focusMargenMinimo,
                        // La tarjeta de sector/parking flota ARRIBA del mapa: lo enfocado se coloca debajo de ella.
                        focusTopInset: miniBlock == nil ? 0 : (vm.isAdmin ? 176 : 124)
                    )
                    .frame(height: height)

                    if let mb = movingBlock {
                        VStack { moveBanner(mb); Spacer() }.padding(.top, fullscreenMap ? 54 : 0)
                            .frame(height: height)
                    } else if let punto = confirmandoFoto {
                        // La foto ya está colocada: aceptar o moverla. El GPS se
                        // equivoca entre 10 y 30 metros y las piedras están a
                        // metros, así que la última palabra es del usuario.
                        mapBanner(fotoSemilla?.aproximada == true
                                  ? L("NO SE PUDO UBICAR LA FOTO · TOCA DÓNDE ESTÁ LA PIEDRA")
                                  : L("LA FOTO SE HIZO AQUÍ · ¿ES DONDE ESTÁ LA PIEDRA?"),
                                  accept: {
                                      confirmandoFoto = nil
                                      flow.boulderCoord = punto
                                  },
                                  aceptarTexto: L("SÍ, SIGUE"),
                                  cancelarTexto: L("MOVERLA"),
                                  cancel: {
                                      confirmandoFoto = nil
                                      flow.waitingTap = true   // toca tú el sitio
                                  })
                    } else if flow.waitingTap {
                        // Banner "PULSA EN EL MAPA" (parking/sector/piedra).
                        mapBanner(L("PULSA EN EL MAPA PARA FIJAR LA POSICIÓN"),
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
                            HStack(spacing: 8) {
                                Spacer()
                                // Filtro de orientación: sobre el mapa, a la izquierda de PROPONER (así la fila de
                                // sectores de arriba tiene todo el ancho para ella).
                                orientationMenu
                                Button {
                                    // Ya no salimos de pantalla completa: el selector
                                    // se presenta desde mapArea (dentro del cover).
                                    flow.showTypePicker = true
                                } label: {
                                    Text(NSLocalizedString("detail_propose", comment: "")).font(Cumbre.mono(12, .bold)).tracking(0.6)
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 9)
                                        .background(Cumbre.terraFill)
                                        .clipShape(RoundedRectangle(cornerRadius: 11))
                                }
                                .buttonStyle(.plain)
                                .guideTarget("detail_map.propose")
                            }
                            Spacer()
                        }
                        .padding(10)
                        .frame(height: height)
                    }

                    // Ampliar / salir de pantalla completa — ARRIBA a la izquierda
                    // (debajo de los chips de estilo) + botonera lateral en fullscreen.
                    if movingBlock == nil && !flow.waitingTap && !flow.correctionMode {
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

                    // Tarjeta flotante de parking/sector, ARRIBA del mapa: pegada a
                    // los chips que la abren. Abajo quedaba fuera de pantalla
                    // desde que el mapa crece con el móvil (Álvaro, 2026-10-31).
                    if let mb = miniBlock, movingBlock == nil, !flow.waitingTap, !flow.correctionMode {
                        VStack { miniBlockCard(mb); Spacer() }.padding(.top, fullscreenMap ? 54 : 0)
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
                                // El trabajo de verdad lo hace el .onChange(of:
                                // fotoSeedStore.pendingSchoolId) de arriba: escribir
                                // aquí el @State directamente desde este closure NO
                                // llega de forma fiable a la vista viva (ver
                                // comentario en PhotoProposalSeedStore, Rodrigo,
                                // 2026-08-22). Solo se cierra el propio flujo.
                                eligiendoFoto = false
                            },
                            onDismiss: { eligiendoFoto = false })
                        // SIN allowsHitTesting(false): con él, sus propios
                        // avisos (p. ej. "Foto sin ubicación" tras hacer la
                        // foto con la cámara) quedaban SIN PODER TOCARSE — la
                        // vía de galería casi nunca necesita ese aviso (la
                        // foto ya trae EXIF) así que ahí no se notaba; la de
                        // cámara SIEMPRE puede necesitarlo (GPS en vivo, hasta
                        // 8s de espera) y por eso "no hacía nada" justo aquí
                        // (Rodrigo, 2026-08-22, solo reproducible DESDE
                        // dentro de una escuela, nunca desde Escuelas).
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
                .sheet(isPresented: $flow.showSuccess) { ContributionSuccessSheet(isAdmin: vm.isAdmin,
                                                                     trusted: !vm.isAdmin && ContributionOutcome.shared.publishedDirectly) }
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
                .onChange(of: selectedBlock?.id) { newId in
                    // Solo la TRANSICIÓN a "ninguna piedra abierta": recargar la misma piedra no cambia su id.
                    if newId == nil { onBlockSheetClosed?() }
                }
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

}
