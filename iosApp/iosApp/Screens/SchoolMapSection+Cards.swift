import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Mapa de una escuela: mini-ficha de parking/sector, mover piedra, fila de sitios e ir a un sector.

extension SchoolMapSection {
    /// Tarjeta flotante de parking/sector, de CRISTAL como la barra de pestañas (Álvaro eligió la maqueta A,
    /// 2026-10-31). Arriba lo de todos: CÓMO LLEGAR y OCULTAR/VER PIEDRAS (o SEGUIR CAMINO en un parking con
    /// camino grabado). Debajo, solo admin y a la vista: MOVER, EDITAR y BORRAR.
    func miniBlockCard(_ mb: Block) -> some View {
        let isParking = mb.type.uppercased() == "PARKING"
        let isStone = !isParking && mb.type.uppercased() != "ZONE"
        let stoneCount = vm.blocks.filter { $0.sectorBlockId == mb.id }.count
        let collapsed = vm.collapsedSectors.contains(mb.id)
        let approach = approachesLoader.approach(to: mb.id)
        var kindText = isParking ? L("Parking") : L("Sector")
        if !isParking && stoneCount > 0 { kindText += L(" · %@ piedra%@", stoneCount, stoneCount == 1 ? "" : "s") }
        if isStone {
            kindText = mb.lines.count == 1 ? L("Piedra · 1 vía") : L("Piedra · %@ vías", mb.lines.count)
        }
        let distance: String? = userCoord.map { u in
            let km = Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: mb.lat, lon2: mb.lon)
            return km < 1 ? "\(Int(km * 1000)) m" : String(format: "%.1f km", km)
        }
        let accent = isStone ? Cumbre.terraFill : Color(isParking ? parkingColor : zoneColor)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Text(isParking ? "P" : isStone ? "\u{25C6}" : "S")
                    .font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(accent)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    Text(mb.name.isEmpty ? kindText : mb.name)
                        .font(Cumbre.serif(19, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                    HStack(spacing: 6) {
                        cardTag(kindText, tint: accent)
                        if let d = distance { cardTag(d, tint: Cumbre.ink2) }
                    }
                }
                Spacer(minLength: 4)
                Button { miniBlock = nil } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Cumbre.ink2)
                        .frame(width: 32, height: 32)
                        .background(Cumbre.ink.opacity(0.07), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(NSLocalizedString("common_close", comment: "")))
            }
            HStack(spacing: 8) {
                cardPill(L("Cómo llegar"), icon: "location.north.fill", primary: true) {
                    let g = URL(string: "comgooglemaps://?daddr=\(mb.lat),\(mb.lon)&directionsmode=driving")!
                    let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(mb.lat),\(mb.lon)")!
                    UIApplication.shared.open(UIApplication.shared.canOpenURL(g) ? g : web)
                }
                if isStone {
                    cardPill(L("Abrir piedra"), icon: "square.stack.3d.up") {
                        miniBlock = nil
                        openBlockSheet(mb)
                    }
                } else if !isParking && stoneCount > 0 {
                    cardPill(collapsed ? L("Ver piedras") : L("Ocultar piedras"),
                             icon: collapsed ? "eye" : "eye.slash") {
                        if collapsed { vm.collapsedSectors.remove(mb.id) } else { vm.collapsedSectors.insert(mb.id) }
                    }
                } else if let ap = approach {
                    // Hay un camino grabado hasta aquí: seguirlo sin ir a buscarlo a APROXIMACIONES.
                    cardPill(L("Seguir camino"), icon: "figure.walk") {
                        miniBlock = nil
                        followingApproach = ap
                    }
                }
            }
            // Desde un parking, los sectores ordenados por lo que queda por andar (como Android). Tocar uno hace lo mismo
            // que tocarlo en el mapa.
            if isParking {
                let near = SectoresDesdeParking.shared.calcular(parking: mb, todos: vm.blocks)
                if !near.isEmpty {
                    Text(L("Sectores desde aquí")).eyebrow()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(near, id: \.sector.id) { s in
                                Button { selectZone(s.sector) } label: {
                                    Text((s.sector.name.isEmpty ? L("Sector") : s.sector.name) + " · " + s.distanciaTexto
                                         + (s.piedras > 0 ? " · \(s.piedras)" : ""))
                                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                        .padding(.horizontal, 11).padding(.vertical, 7)
                                        .background(Color.white.opacity(0.85), in: Capsule())
                                        .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            if vm.isAdmin && !isStone {
                HStack(spacing: 6) {
                    Text("ADMIN").font(Cumbre.mono(9, .bold)).tracking(1.2).foregroundStyle(Cumbre.ink3)
                        .frame(width: 44, alignment: .leading)
                    adminChip(L("Mover"), icon: "arrow.up.and.down.and.arrow.left.and.right", tint: Cumbre.ink) {
                        movePending = nil
                        movingBlock = mb
                    }
                    adminChip(L("Editar"), icon: "pencil", tint: Cumbre.ink) {
                        miniBlock = nil
                        openBlockSheet(mb)
                    }
                    adminChip(L("Borrar"), icon: "trash", tint: Cumbre.bad) { confirmDeleteMini = true }
                }
                .padding(.top, 10)
                .overlay(alignment: .top) { Rectangle().fill(Cumbre.ink.opacity(0.08)).frame(height: 1) }
            }
        }
        .padding(14)
        .background(Cumbre.bg.opacity(0.55), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 1))
        .shadow(color: .black.opacity(0.22), radius: 16, y: 8)
        .padding(.horizontal, 10).padding(.top, 10)
    }

    func cardTag(_ text: String, tint: Color) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(tint).lineLimit(1)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(tint.opacity(0.13), in: Capsule())
    }

    func cardPill(_ title: String, icon: String, primary: Bool = false,
                          action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon).font(.system(size: 14, weight: .semibold))
                Text(title).font(.system(size: 15, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(primary ? Color.white : Cumbre.ink)
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(primary ? AnyShapeStyle(Cumbre.terraFill) : AnyShapeStyle(Cumbre.bg.opacity(0.85)), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    func adminChip(_ title: String, icon: String, tint: Color,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 12, weight: .semibold))
                Text(title).font(.system(size: 13, weight: .medium)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity).padding(.vertical, 8)
            .background(Cumbre.bg.opacity(0.7), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    /// Aviso del modo MOVER (admin): toca el sitio nuevo y GUARDAR AQUÍ lo deja puesto al momento.
    func moveBanner(_ b: Block) -> some View {
        let name = b.name.isEmpty ? typeLabel(b.type) : b.name
        return HStack(spacing: 12) {
            Image(systemName: "mappin.and.ellipse").font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Cumbre.terraFill, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(L("Mover «%@»", name)).font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .lineLimit(1)
                Text(movePending == nil ? L("Toca en el mapa el sitio nuevo") : L("Toca otra vez para cambiarlo"))
                    .font(.system(size: 13)).foregroundStyle(Cumbre.ink2).lineLimit(2)
            }
            Spacer(minLength: 4)
            Button(NSLocalizedString("common_cancel", comment: "")) {
                movingBlock = nil
                movePending = nil
            }
            .font(.system(size: 14, weight: .medium)).foregroundStyle(Cumbre.ink2)
            .buttonStyle(.plain)
            if movePending != nil {
                Button { Task { await saveMove() } } label: {
                    Text(savingMove ? L("Guardando…") : L("Guardar aquí"))
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Cumbre.terraFill, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(savingMove)
            }
        }
        .padding(12)
        .background(Cumbre.bg.opacity(0.55), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 1))
        .shadow(color: .black.opacity(0.22), radius: 16, y: 8)
        .padding(.horizontal, 10).padding(.top, 10)
    }

    /// Guarda el sitio nuevo directamente (admin): mismo pedido que Gestionar bloques, que conserva las vías.
    func saveMove() async {
        guard let b = movingBlock, let p = movePending else { return }
        savingMove = true
        defer { savingMove = false }
        do {
            _ = try await AppDependencies.shared.container.updateBlock.invoke(blockId: b.id, req: blockMoveRequest(b, to: p))
        } catch {
            ErrorPresenter.shared.show(L("No se pudo mover. Revisa la conexión e inténtalo otra vez."))
            return
        }
        movingBlock = nil
        movePending = nil
        if let fresh = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id) { selectedBlock = fresh }
        if let moved = vm.blocks.first(where: { $0.id == b.id }) { miniBlock = moved }
    }

    /// Sectores y parkings arriba, cada uno en SU fila y SIN rótulos: el símbolo ya lo dice (S = sector, P = parking).
    /// Tocar uno lleva el mapa allí. El filtro de orientación va sobre el mapa, junto a PROPONER.
    @ViewBuilder var placesRow: some View {
        let sectores = ordenadosPorCercania(vm.blocks.filter { $0.type.uppercased() == "ZONE" })
        let parkings = ordenadosPorCercania(vm.blocks.filter { $0.type.uppercased() == "PARKING" })
        if !(sectores.isEmpty && parkings.isEmpty) {
            VStack(alignment: .leading, spacing: 2) {
                // Línea 1: sectores (S), con todo el ancho.
                if !sectores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(sectores, id: \.id) { s in
                                placeChip(symbol: "S", color: zoneColor, corner: 10, label: etiquetaSector(s),
                                          restrictions: restrictionsUI?.allInSector(s.id) ?? []) {
                                    miniBlock = s
                                    irAlSector(s)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
                // Línea 2: parkings (P), aparte para no mezclarlos con los sectores.
                if !parkings.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(parkings, id: \.id) { p in
                                placeChip(symbol: "P", color: parkingColor, corner: 5, label: chipLabel(p)) {
                                    miniBlock = p
                                    flyToParkingZone(p)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 4)
            .guideTarget("detail_map.places")
        }
    }

    func placeChip(symbol: String, color: UIColor, corner: CGFloat, label: String,
                           restrictions: [Restriction] = [],
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(symbol).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 20, height: 20)
                    .background(Color(color))
                    .clipShape(RoundedRectangle(cornerRadius: corner))
                Text(label).font(.system(size: 13, weight: .medium)).foregroundStyle(Cumbre.ink).lineLimit(1)
                RestrictionMarkButton(items: restrictions)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Cumbre.paper, in: Capsule())
            .overlay(Capsule().stroke(Cumbre.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    /// Ordena parkings/sectores por CERCANÍA a tu ubicación — pero solo si de
    /// verdad estás en la escuela.
    ///
    /// Petición de Rodrigo (2026-08-17). Concilia con la decisión anterior, que
    /// los ponía alfabéticos a propósito "para que la lista tenga sentido
    /// también viéndola desde casa": mirándola de lejos, todos los parkings de
    /// una escuela están prácticamente a la MISMA distancia de ti, así que
    /// ordenarlos por distancia da un orden caprichoso que cambia con solo
    /// moverte por casa. Por eso: cerca → por distancia (lo útil en la roca);
    /// lejos o sin GPS → alfabético (estable y predecible).
    func ordenadosPorCercania(_ items: [Block]) -> [Block] {
        guard let u = userCoord else { return items.sorted { $0.name < $1.name } }
        let conDistancia = items.map {
            ($0, Geo.shared.haversineKm(lat1: u.latitude, lon1: u.longitude, lat2: $0.lat, lon2: $0.lon))
        }
        let estoyEnLaEscuela = conDistancia.contains { $0.1 <= Self.kmParaOrdenarPorCercania }
        guard estoyEnLaEscuela else { return items.sorted { $0.name < $1.name } }
        return conDistancia.sorted { $0.1 < $1.1 }.map { $0.0 }
    }

    /// A partir de aquí se considera que NO estás en la escuela (mismo criterio
    /// que usa el encuadre inicial del mapa para incluir tu posición).
    static let kmParaOrdenarPorCercania: Double = 20

    /// Nombre del sector + cuántas piedras tiene (lo que de verdad ayuda a
    /// elegir a cuál ir).
    /// Nombre + cuántas piedras tiene. SIN distancia a propósito: el GPS refresca
    /// en continuo y el número bailaba sin parar en pantalla, que distrae más de
    /// lo que ayuda (Rodrigo, 2026-08-17). La cercanía sigue notándose en el
    /// ORDEN de la fila, que es estable porque unos metros de deriva del GPS no
    /// cambian qué sector está más cerca.
    func etiquetaSector(_ s: Block) -> String {
        let piedras = vm.blocks.filter { $0.sectorBlockId == s.id }.count
        let nombre = s.name.isEmpty ? L("Sector") : s.name
        return piedras > 0 ? "\(nombre) · \(piedras)" : nombre
    }

    /// Lleva el mapa al sector y DESPLIEGA sus piedras, encuadrándolas para que
    /// se vean todas. Mismo comportamiento que tocar el sector en el mapa.
    func irAlSector(_ s: Block) {
        let piedras = vm.blocks.filter { $0.sectorBlockId == s.id }
        vm.collapsedSectors.remove(s.id)

        // Regla (Rodrigo, 2026-08-17): si el sector TIENE piedras, encuadrar
        // para que se vean todas; si no tiene, centrarse en él y listo.
        //
        // Se encuadran SOLO las piedras, sin el marcador del sector: ese
        // marcador puede estar apartado (al borde del camino, p. ej.) y meterlo
        // estiraba el encuadre dejando las piedras más pequeñas de lo necesario.
        //
        // Y el margen mínimo baja a ~55 m: el de por defecto (~450 m a cada
        // lado) está pensado para encuadrar escuelas enteras y convertía un
        // sector de 55 m —lo que miden de verdad los de Zarzalejo— en una caja
        // de casi 1 km, con lo que el mapa apenas se movía. Esa era la causa de
        // "los sectores CON piedras no hacen zoom y los que no tienen sí".
        focusMargenMinimo = 0.0005
        if piedras.count >= 2 {
            focusFit = piedras.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
        } else {
            // 0 piedras → el sector. 1 piedra → esa piedra (encuadrar un solo
            // punto no define ninguna caja).
            focusFit = []
            let destino = piedras.first ?? s
            focusCoord = CLLocationCoordinate2D(latitude: destino.lat, longitude: destino.lon)
            focusZoom = 17.0
        }
        focusToken += 1
    }

    /// Etiqueta del chip de parking: nombre + distancia si hay ubicación.
    func chipLabel(_ p: Block) -> String {
        // SIN distancia a propósito (Rodrigo, 2026-08-17): el GPS refresca en
        // continuo y el número bailaba sin parar, que distrae más de lo que
        // ayuda. La cercanía se nota en el ORDEN de la fila. La distancia exacta
        // sigue estando en la mini-ficha al tocar el parking, que es donde se
        // mira una vez y no cambia delante de tus ojos.
        p.name.isEmpty ? L("Parking") : p.name
    }

    func color(for type: String) -> UIColor {
        switch type.uppercased() {
        case "PARKING": return parkingColor
        case "ZONE": return zoneColor
        default: return blockColor
        }
    }
    func typeLabel(_ t: String) -> String {
        switch t.uppercased() {
        case "PARKING": return "PARKING"
        case "ZONE": return L("ZONA")
        default: return L("PIEDRA")
        }
    }
}

/// Coordenada Identifiable para presentar el formulario por sheet(item:).
struct CoordItem: Identifiable {
}
