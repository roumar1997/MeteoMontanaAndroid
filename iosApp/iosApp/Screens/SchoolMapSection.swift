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
    /// "Ver en mapa" desde la guía: centra el mapa en ESTA piedra (id) SIN abrir su ficha.
    var focusBlockId: String? = nil
    /// El buscador de vías/bloques por nombre. La ficha de escuela con pestañas lo quita del mapa (vive en BLOQUES/VÍAS);
    /// los chips de orientación se quedan.
    var showsSearch: Bool = true
    /// Se avisa cuando la ficha de una piedra se CIERRA del todo (no cuando solo se recarga). La ficha de escuela
    /// lo usa para devolverte a la lista de BLOQUES si venías de ella.
    var onBlockSheetClosed: (() -> Void)? = nil
    /// Sube al deslizar hacia abajo en la ficha (pull-to-refresh) para volver
    /// a pedir las piedras — normalmente solo se cargan una vez al abrir el
    /// mapa (Álvaro, 2026-09-04: piedras/usuarios nuevos no aparecían sin
    /// salir y volver a entrar).
    var refreshTrigger: Int = 0

    // Filtro por ORIENTACIÓN (consenso comunitario): filtra los marcadores
    // de piedra del mapa. Piedras sin votos → solo en TODAS. Espejo de
    // OrientationFilter.kt de Android.
    @State var blockOrientations: [String: String] = [:]
    /// Grados partidos por altura (se piden juntos para los resultados del buscador).
    @ObservedObject var gradeSplits = GradeSplitsStore.shared
    @State var orientationFilter: String? = nil
    // Filtro LOCAL por GRADO (ver docs/BLOCK_SEARCH_DESIGN.md §7). No llama al
    // servidor: la escuela ya trae todas sus vías. Las piedras sin ninguna vía
    // en rango se ATENÚAN (no se ocultan): se conserva el contexto del sector.
    // No persiste al salir de la escuela, a diferencia del filtro de escuelas.
    @State var selectedGrades: Set<String> = []
    /// Filtro Vía/Bloque del mapa: solo aparece si la escuela tiene AMBOS
    /// estilos ("Vía,Bloque" en school.style) — si es de uno solo, no aporta
    /// nada y no se muestra. Vacío = sin filtrar, se ve todo.
    @State var styleFilter: Set<String> = []
    // Aproximaciones (caminos) — Fase 1 de docs/APPROACH_DESIGN.md, solo lectura.
    // El loader vive aquí (no dentro de ApproachesSection) para que la ficha
    // mini de un sector/parking pueda ofrecer "SEGUIR" si hay un camino hasta
    // él, no solo la sección de la ficha completa.
    @StateObject var approachesLoader = ApproachesLoader()
    /// Observado A PROPÓSITO en vez de recibir la semilla solo por closure:
    /// ver el comentario en PhotoProposalSeedStore (Rodrigo, 2026-08-22).
    @ObservedObject var fotoSeedStore = PhotoProposalSeedStore.shared
    @State var followingApproach: Approach?
    @State var didAutoOpen = false
    /// Bandera interna, ya no un boton: el mapa esta siempre visible (ver body).
    @State var expanded = true
    @State var selectedBlock: Block?
    // Flujo proponer/corregir agrupado (espejo de ProposalMapBridge en Android).
    @StateObject var flow = MapProposalFlowStore()
    /// Foto de "Enviar piedra" que esperaba a esta escuela. Se lee UNA vez: si
    /// se quedara puesta, volver a entrar reabriría el flujo de proponer sin
    /// que nadie lo haya pedido.
    @State var fotoSemilla: PhotoProposalSeedStore.Seed?
    /// Atajo "piedra desde una foto" lanzado DESDE esta escuela.
    @State var eligiendoFoto = false
    /// Piedra a medias guardada en este movil (se lee al entrar).
    @State var borradorPiedra: BoulderDraftStore.Draft?
    /// El formulario arranca precargado con el borrador.
    @State var continuandoBorrador = false
    /// Punto donde se hizo la foto, a la espera de que el usuario lo confirme.
    @State var confirmandoFoto: CLLocationCoordinate2D?
    /// "CORREGIR NOMBRE" del selector — sin tocar el mapa, solo un campo.
    @State var correctingSchoolName = false
    /// "CORREGIR ESTILO" del selector — sin tocar el mapa, solo chips.
    @State var correctingSchoolStyle = false
    // Datos del mapa (bloques/capas/buscador/admin) — ver SchoolMapViewModel.
    @StateObject var vm = SchoolMapViewModel()
    @State var mapStyle: MapStyleKind = .satellite  // paridad con Android
    @State var userCoord: CLLocationCoordinate2D?   // mi ubicación en el sector
    // Mini-ficha flotante de PARKING/ZONA sobre el mapa (la ficha grande queda
    // solo para PIEDRAS). confirmDeleteMini = confirmación de borrado (admin).
    @State var miniBlock: Block?
    /// Restricciones en vigor de la escuela (las da la ficha): "!" en el chip del sector.
    @Environment(\.schoolRestrictions) var restrictionsUI
    @State var confirmDeleteMini = false
    /// «Mover» de la tarjeta de sector/parking (solo admin): el bloque que se mueve y el sitio tocado, aún sin
    /// guardar. Va directo al servidor (sin propuesta), igual que Gestionar bloques.
    @State var movingBlock: Block?
    @State var movePending: CLLocationCoordinate2D?
    @State var savingMove = false
    @State var editLinesBlock: Block?
    @State var assignSectorBlock: Block?
    @State var mapZoom: Double = 14   // para mostrar el nombre del sector al acercar
    // Cámara conservada al recrear el MapView (entrar/salir de fullscreen).
    @State var savedCenter: CLLocationCoordinate2D?
    @State var savedZoom: Double?
    // Foco explícito al pulsar un parking en la lista (recentra el mapa ahí).
    @State var focusCoord: CLLocationCoordinate2D?
    /// Zoom del próximo enfoque (mínimo: nuncaAlejar lo respeta).
    @State var focusZoom: Double = 15.2
    /// Margen mínimo del encuadre (grados de lat). Los sectores lo bajan.
    @State var focusMargenMinimo: Double = 0.004
    @State var focusToken = 0
    // Encuadre de bounds (parking + su zona) — tiene prioridad sobre focusCoord.
    @State var focusFit: [CLLocationCoordinate2D] = []
    // Capas ocultas por el usuario (leyenda pulsable): PARKING/BLOCK/ZONE.
    // Mapa a pantalla completa (estilo Radar).
    @State var fullscreenMap = false
    // Buscador de vías/bloques de la escuela.
    // Brújula → cono de dirección en el punto azul.
    @StateObject var headingProvider = HeadingProvider()

    // Colores de marcador por tipo (espejo de Android: parking azul, piedra
    // terra, zona verde; la escuela en tinta oscura).
    let parkingColor = UIColor(red: 0.20, green: 0.45, blue: 0.85, alpha: 1)
    let blockColor = UIColor(red: 0.78, green: 0.40, blue: 0.13, alpha: 1)
    let zoneColor = UIColor(red: 0.29, green: 0.49, blue: 0.35, alpha: 1)
    let schoolColor = UIColor(red: 0.11, green: 0.11, blue: 0.10, alpha: 1)

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
                placesRow
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
                    mapArea(height: mapHeight)
                }
                // Grabar/chincheta SOLO ADMIN por ahora (docs/APPROACH_DESIGN.md §2.6/§10).
                ApproachesSection(loader: approachesLoader, school: school,
                                   isAdmin: vm.isAdmin, following: $followingApproach)
                // "CÓMO LLEGAR" de la escuela quitado: las indicaciones salen al
                // tocar cada parking/piedra en el mapa (BlockInfoSheet).
            }
        }
        .task(id: expanded) {
            guard expanded else { return }
            if vm.blocks.isEmpty {
                _ = await vm.reloadBlocks(school: school, selectedId: nil)
            }
            maybeAutoOpen()
            maybeFocusBlock()
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
            if let b = focusBlockId, !b.isEmpty, !didAutoOpen, !expanded { expanded = true }
            // "Enviar piedra": la foto ya dijo qué se propone y dónde se hizo.
            // Se abre el mapa y se espera un toque para el sitio exacto: el GPS
            // se equivoca entre 10 y 30 metros y las piedras están a metros.
            borradorPiedra = BoulderDraftStore.load(schoolId: school.id)
            consumirSemillaDeFoto()
        }
        // Camino FIABLE para la foto tomada DESDE ESTA MISMA pantalla (a
        // diferencia del .onAppear de arriba, que solo cubre la entrada
        // fresca vía navegación): reacciona al @Published de
        // PhotoProposalSeedStore en vez de a un closure capturado que puede
        // apuntar a una copia ya descartada de esta vista (ver comentario en
        // PhotoProposalSeedStore, Rodrigo, 2026-08-22).
        .onChange(of: fotoSeedStore.pendingSchoolId) { nuevo in
            guard nuevo == school.id else { return }
            eligiendoFoto = false
            borradorPiedra = BoulderDraftStore.load(schoolId: school.id)
            consumirSemillaDeFoto()
        }
        // Si los bloques llegan DESPUÉS del task (caché/red lenta), reintenta
        // el auto-abrir del deep-link (antes se quedaba en la escuela a secas).
        // OJO: la firma incluye las VÍAS, no solo el número de bloques. La
        // caché puede traer los mismos N bloques SIN sus vías: el auto-abrir
        // fallaba (abría la escuela a secas) y como el count no cambiaba al
        // llegar los datos reales, no se reintentaba (a la 2ª vez ya iba).
        .onChange(of: vm.blocks.reduce(0) { $0 + 1 + $1.lines.count }) { _ in maybeAutoOpen(); maybeFocusBlock() }
        // Pull-to-refresh de la ficha: vuelve a pedir las piedras (el `.task(id:
        // expanded)` de arriba solo carga si `vm.blocks` está vacío).
        .task(id: refreshTrigger) {
            guard refreshTrigger > 0 else { return }
            _ = await vm.reloadBlocks(school: school, selectedId: selectedBlock?.id)
        }
        // ¿Admin? → puede eliminar bloques desde su ficha.
        .task {
            await vm.loadAdminFlag()
            // Consenso de orientación de TODAS las piedras (una llamada).
            if let m = try? await AppDependencies.shared.container
                .getSchoolOrientations.invoke(schoolId: school.id) {
                blockOrientations = (m as NSDictionary as? [String: String]) ?? [:]
            }
        }
        .alert(L("¿Eliminar «%@»?", miniBlock?.name ?? ""), isPresented: $confirmDeleteMini) {
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

    /// Altura del mapa en la ficha: ocupa lo que sobra bajo cabecera, pestañas y filtros (antes eran 280 fijos y
    /// quedaba medio móvil vacío debajo). Limitada para que ni se quede diminuto ni se coma la pantalla entera.
    var mapHeight: CGFloat {
        let free: CGFloat = UIScreen.main.bounds.height - 400
        return min(max(free, 300), 560)
    }

    /// Buscador de vías/bloques (como el buscador de escuelas de la lista).
    // Muros (geometry=LINE) dibujados como polilínea terra en el mapa. Se ocultan
    // si la piedra pertenece a un sector colapsado (igual que su marcador).
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
