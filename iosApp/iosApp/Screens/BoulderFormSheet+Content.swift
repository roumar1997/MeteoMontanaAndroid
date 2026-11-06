import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Formulario de piedra: el contenido del formulario.

extension BoulderFormSheet {
    var contenido: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Rellena los datos de la piedra. Podrás añadir fotos y dibujar las líneas de cada vía sobre ellas.")
                        .font(.system(size: 13)).foregroundStyle(Cumbre.ink2)

                    FirstTimeHint(
                        hintKey: "boulder_form_guide",
                        text: L("Pasos: 1) Añade una foto de la piedra, 2) marca sus vías con grado, 3) dibuja las líneas sobre la foto, 4) envía.")
                    )

                    // ── Modalidad: BLOQUE o VÍA ────────────────────────────────────
                    VStack(alignment: .leading, spacing: 6) {
                        Text("MODALIDAD").eyebrow()
                        Text("¿Es una piedra de boulder (sentadas, bloques cortos) o de vía (escalada deportiva, más larga)?")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        DisciplineSelector(selected: $discipline)
                    }

                    // F: orientación de la piedra según el autor (opcional).
                    // Al aprobarse se convierte en su PRIMER voto; la
                    // comunidad puede seguir votando después.
                    orientationChips(label: L("ORIENTACIÓN DE LA PIEDRA (opcional)"),
                                     selected: blockOrientation) { a in
                        blockOrientation = blockOrientation == a ? nil : a
                    }

                    // ── Opciones avanzadas: muro, sector y numeración (plegado) ──
                    // El 90% de las propuestas son una piedra normal: esto vive
                    // plegado para no estorbar (espejo del formulario Android).
                    Button { withAnimation { advancedOpen.toggle() } } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Muro, sector y numeración").font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                Text(isWall ? L("Muro de %@ puntos", wallPath.count) + (sectorId != nil ? L(" · con sector") : "")
                                     : (sectorId != nil ? L("Con sector asignado") : L("Solo si es una pared larga o va en un sector")))
                                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            }
                            Spacer()
                            Image(systemName: advancedOpen ? "chevron.up" : "chevron.down")
                                .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                        }
                        .padding(12)
                        .background(Cumbre.paper)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(advancedOpen ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain)
                    if advancedOpen {
                        // ── Geometría: PUNTO o MURO ────────────────────────────────────
                        VStack(alignment: .leading, spacing: 6) {
                            Text(NSLocalizedString("propose_geometry", comment: "")).eyebrow()
                            Text("Punto = una piedra suelta. Muro = una pared larga que se traza en el mapa.")
                                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                            WallSeg(options: [("POINT", L("PUNTO")), ("LINE", L("MURO"))], selected: $geometry)
                        }
                        if isWall {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("SENTIDO DE NUMERACIÓN").eyebrow()
                                WallSeg(options: [("LTR", L("IZQ → DER")), ("RTL", L("DER → IZQ"))], selected: $direction)
                            }
                            Button { showTrace = true } label: {
                                Text(wallPath.isEmpty ? L("✎ TRAZAR EL MURO EN EL MAPA") : L("✓ MURO TRAZADO (%@ PUNTOS) · RE-TRAZAR", wallPath.count))
                                    .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.terra)
                                    .lineLimit(1).minimumScaleFactor(0.8)
                                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                                    .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                        .stroke(Cumbre.terra, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                        if !sectors.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("SECTOR (opcional)").eyebrow()
                                Menu {
                                    Button("Sin sector") { sectorId = nil }
                                    ForEach(sectors, id: \.id) { s in
                                        Button(s.name.isEmpty ? L("Zona") : s.name) { sectorId = s.id }
                                    }
                                } label: {
                                    HStack {
                                        Text(sectorName).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                                        Spacer(); Image(systemName: "chevron.down").font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                                    }
                                    .padding(10).overlay(Rectangle().stroke(Cumbre.rule, lineWidth: 1))
                                }
                            }
                        }
                    }

                    // ── Caras (fotos) ──────────────────────────────────────────────
                    Text("FOTOS DE LA PIEDRA").eyebrow()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            // Pestañas CON MINIATURA, igual que al EDITAR: crear
                            // una piedra se veía "a la antigua" con solo el
                            // número (Álvaro, 2026-08-24).
                            ForEach(Array(faces.enumerated()), id: \.element.id) { idx, _ in
                                let on = idx == faceIdx
                                Button { selectedFace = idx } label: {
                                    VStack(spacing: 4) {
                                        Group {
                                            if let img = faces[idx].photo {
                                                Image(uiImage: img).resizable().scaledToFill()
                                            } else {
                                                Cumbre.paper.overlay(
                                                    Image(systemName: "photo").font(.system(size: 16))
                                                        .foregroundStyle(Cumbre.ink3))
                                            }
                                        }
                                        .frame(width: 52, height: 52)
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                        .overlay(RoundedRectangle(cornerRadius: 12)
                                            .stroke(on ? Cumbre.terra : Cumbre.rule, lineWidth: on ? 2 : 1))
                                        Text(L("FOTO %@", idx + 1)).font(Cumbre.mono(10, .bold))
                                            .foregroundStyle(on ? Cumbre.terra : Cumbre.ink2)
                                    }
                                }.buttonStyle(.plain)
                            }
                            Button { faces.append(BoulderFaceForm()); selectedFace = faces.count - 1 } label: {
                                VStack(spacing: 4) {
                                    Image(systemName: "plus").font(.system(size: 18)).foregroundStyle(Cumbre.terra)
                                        .frame(width: 52, height: 52)
                                        .overlay(RoundedRectangle(cornerRadius: 12)
                                            .stroke(Cumbre.rule, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                                    Text(NSLocalizedString("propose_add_photo", comment: ""))
                                        .font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra).lineLimit(1)
                                }
                            }.buttonStyle(.plain)
                        }
                    }
                    if faces.count > 1 {
                        Button {
                            faces.remove(at: faceIdx); selectedFace = max(0, faceIdx - 1)
                        } label: {
                            Text("✕ QUITAR ESTA FOTO").font(Cumbre.mono(10, .bold))
                                .foregroundStyle(Cumbre.bad)
                        }.buttonStyle(.plain)
                    }

                    // ── Foto de la cara seleccionada ───────────────────────────────
                    if let photo = faces[faceIdx].photo {
                        Image(uiImage: photo).resizable().scaledToFit()
                            .frame(maxHeight: 200).clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                    // Mismo selector que "aportar desde una foto": el de
                    // SwiftUI entrega una copia SIN ubicación, y entonces la
                    // orientación no se podría sugerir nunca.
                    Button {
                        eligiendoOrigenFotoCara = true
                    } label: {
                        Text(faces[faceIdx].photo == nil ? L("SELECCIONAR FOTO") : L("CAMBIAR FOTO"))
                            .font(Cumbre.mono(12, .bold)).tracking(0.6).foregroundStyle(Cumbre.terra)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius).stroke(Cumbre.rule, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .confirmationDialog("¿Cómo quieres la foto?", isPresented: $eligiendoOrigenFotoCara) {
                        Button("HACER FOTO AHORA") {
                            let idx = faceIdx
                            CameraAccess.request { granted in
                                guard granted else { return }
                                presentSystemCamera(context: "piedra-nueva-\(idx)") { image in
                                    faces[idx].photo = image
                                    sendError = nil
                                }
                            }
                        }
                        Button("ELEGIR DE GALERÍA") { elegirFotoDeGaleria() }
                        Button("CANCELAR", role: .cancel) {}
                    }
                    // F: orientación de ESTA cara (opcional). Solo tiene
                    // sentido con varias fotos; con una sola vale la general.
                    if faces.count > 1 {
                        orientationChips(label: L("ORIENTACIÓN DE ESTA CARA (opcional)"),
                                         selected: faces[faceIdx].orientation) { a in
                            faces[faceIdx].orientation =
                                faces[faceIdx].orientation == a ? nil : a
                        }
                    }

                    // ── Vías de esta foto ──────────────────────────────────────────
                    // Una sola ficha abierta a la vez, igual que al EDITAR: con
                    // los formularios apilados, meter la quinta vía obligaba a
                    // scrollear las cuatro anteriores (Álvaro, 2026-08-24).
                    Text(L("VÍAS EN ESTA FOTO (%@)", faces[faceIdx].blocks.count)).eyebrow().padding(.top, 4)
                    ForEach(Array(faces[faceIdx].blocks.enumerated()), id: \.element.id) { idx, via in
                        if via.id == expandedVia {
                            VStack(alignment: .leading, spacing: 8) {
                                BoulderBlockRow(block: $faces[faceIdx].blocks[idx], index: idx,
                                                onDelete: faces[faceIdx].blocks.count > 1 ? {
                                                    faces[faceIdx].blocks.remove(at: idx); expandedVia = nil
                                                } : nil)
                                HStack(spacing: 8) {
                                    Button { expandedVia = nil } label: {
                                        Text("LISTO").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                            .foregroundStyle(Cumbre.ink2)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                                .stroke(Cumbre.rule, lineWidth: 1))
                                    }.buttonStyle(.plain)
                                    Button { nuevaVia() } label: {
                                        Text("AÑADIR OTRA +").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                            .foregroundStyle(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(Cumbre.terraFill,
                                                        in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                                    }.buttonStyle(.plain)
                                }
                            }
                        } else {
                            viaCompacta(idx: idx, via: faces[faceIdx].blocks[idx])
                        }
                    }
                    if !expandedHere {
                        Button { nuevaVia() } label: {
                            Text("+ AÑADIR VÍA").font(Cumbre.mono(12, .bold)).tracking(0.6)
                                .foregroundStyle(Cumbre.terra).frame(maxWidth: .infinity).padding(.vertical, 10)
                                .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                    .stroke(Cumbre.terra, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }

                    // ── Dibujar líneas de esta foto ────────────────────────────────
                    let hasPhoto = faces[faceIdx].photo != nil
                    Button { showEditor = true } label: {
                        Text(faces[faceIdx].blocks.contains { !$0.line.isEmpty } ? L("✎ EDITAR LÍNEAS") : L("✎ DIBUJAR LÍNEAS"))
                            .font(Cumbre.mono(12, .bold)).tracking(0.6)
                            .foregroundStyle(hasPhoto ? .white : Cumbre.ink3)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(hasPhoto ? Cumbre.terraFill : Color.clear,
                                        in: RoundedRectangle(cornerRadius: Cumbre.pillRadius))
                            .overlay(RoundedRectangle(cornerRadius: Cumbre.pillRadius)
                                .stroke(hasPhoto ? Cumbre.terra : Cumbre.rule, lineWidth: 1))
                    }.buttonStyle(.plain).disabled(!hasPhoto)
                    if !hasPhoto {
                        Text("Añade una foto para poder dibujar las líneas.")
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("COORDENADAS").eyebrow()
                        Text(String(format: "%.5f, %.5f", coord.latitude, coord.longitude))
                            .font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink2)
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            .navigationTitle("Nueva piedra")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(NSLocalizedString("common_cancel", comment: "")) {
                        // Una piedra son varios campos, una foto por cara y una
                        // linea por via: cerrar no puede tirarlo sin preguntar.
                        if BoulderDraftStore.tieneContenido(borradorActual()) {
                            preguntandoGuardar = true
                        } else {
                            dismiss(); onDone(false)
                        }
                    }.foregroundStyle(Cumbre.ink3)
                }
                // ENVIAR arriba, fijo, igual que Editar vías (Álvaro,
                // 2026-08-24: "fíjate que enviar propuesta está abajo y no
                // arriba a la derecha como hablamos"). .borderedProminent para
                // que el relleno ocupe la píldora entera en iOS 26.
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await send() }
                    } label: {
                        if sending { ProgressView().tint(.white) }
                        else { Text(NSLocalizedString("propose_submit", comment: ""))
                            .font(Cumbre.mono(11, .bold)).tracking(0.6) }
                    }
                    .buttonStyle(.borderedProminent).tint(Cumbre.terraFill)
                    .disabled(sending)
                }
            }
            // Deslizar hacia abajo NO puede tirar el trabajo en silencio: con
            // algo escrito, el gesto se desactiva y hay que usar "Cancelar",
            // que es quien pregunta (lo cazo Rodrigo: cancelar preguntaba, el
            // gesto no).
            .interactiveDismissDisabled(BoulderDraftStore.tieneContenido(borradorActual()))
            .alert("¿Guardar para terminar luego?", isPresented: $preguntandoGuardar) {
                Button("GUARDAR") {
                    BoulderDraftStore.save(borradorActual())
                    dismiss(); onDone(false)
                }
                Button("DESCARTAR", role: .destructive) {
                    BoulderDraftStore.clear(schoolId: schoolId)
                    dismiss(); onDone(false)
                }
            } message: {
                Text("Se queda guardada en este móvil. No se envía a nadie hasta que la termines.")
            }
            .alert("Guardada en tu móvil", isPresented: $queued) {
                Button("CERRAR") { dismiss(); onDone(false) }
            } message: {
                Text("Se enviará automáticamente en cuanto haya cobertura. No tienes que hacer nada.")
            }
            // No se pudo enviar (sin cobertura, lo normal): diálogo emergente en
            // vez de un texto rojo colado en el formulario, mismo estilo que el
            // de "¿Guardar también las fotos?" al descargar una escuela. Repetible
            // — reintentar puede volver a fallar y el diálogo vuelve a salir.
            .alert("No se pudo enviar",
                   isPresented: Binding(get: { sendError != nil }, set: { if !$0 { sendError = nil } })) {
                Button("Guardar y enviar con cobertura") { Task { await saveOffline() } }
                Button("Reintentar") { Task { await send() } }
            } message: {
                Text(sendError ?? "")
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            let idx = faceIdx
            // @MainActor OBLIGATORIO: `faces` es @State y de él se pinta la
            // miniatura y depende el botón de dibujar. Sin esto se escribía
            // fuera del hilo de la interfaz y SwiftUI no se enteraba (mismo
            // fallo que en EditLinesSheet, build 142).
            Task { @MainActor in
                // loadTransferable a veces devuelve nil (foto en iCloud sin bajar
                // aún): antes fallaba en silencio y la foto no aparecía.
                if let data = try? await item.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    faces[idx].photo = img
                    sendError = nil
                } else {
                    sendError = L("No se pudo cargar la foto elegida (¿está en iCloud?). Elígela otra vez.")
                }
                pickerItem = nil
            }
        }
        .sheet(isPresented: $showEditor) {
            if faces[faceIdx].photo != nil {
                TopoEditorView(photo: faces[faceIdx].photo, blocks: $faces[faceIdx].blocks)
            }
        }
        .sheet(isPresented: $showTrace) {
            WallTraceSheet(center: coord, initial: wallPath,
                           contextMarkers: contextMarkers) { wallPath = $0 }
        }
    }
}
