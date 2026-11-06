import SwiftUI
import PhotosUI
import CoreLocation
import Shared

// Editar vías: enviar la propuesta y guardarla sin cobertura.

extension EditLinesSheet {
    func send() async {
        sending = true
        // 1) Sube las fotos nuevas (caras que el usuario cambió) → URL por cara, y
        //    marca esas caras como "foto cambiada".
        var newFacePhoto: [Int: String] = [:]
        for (i, img) in facePicked {
            // No seguir si una foto cambiada no sube (evita mezclar caras).
            guard let url = try? await StorageUploader.uploadBoulderPhoto(img, schoolId: schoolId, index: i) else {
                sending = false
                // Ofrecer guardarlo TAMBIÉN aquí. Sin cobertura este es el
                // primer sitio donde se falla —subir la foto— y hacer `return`
                // sin más se saltaba el ofrecimiento: el usuario solo veía
                // "reintentar" y no podía dejarlo guardado (Rodrigo, build 144).
                ofrecerGuardarOffline = true
                return
            }
            newFacePhoto[i] = url
        }
        // 2) Construye el payload por cara. Si la cara cambió de foto, se envían
        //    TODAS sus vías (existentes como corrección + nuevas) con la foto nueva
        //    → la cara entera se mueve a la imagen nueva. Si no cambió, solo las
        //    vías modificadas + las nuevas.
        // Estado COMPLETO: todas las vías en su orden, cada una con la foto de su
        // cara (la nueva si la cara cambió). El backend (reconcileWall) reconcilia
        // por lineId preservando el diario, reaplica el orden y borra las omitidas.
        var payload: [BoulderBlockForm] = []
        for (i, faceVias) in faceBlocks.enumerated() {
            let movedPhoto = newFacePhoto[i]
            for b in faceVias {
                var v = b
                if let p = movedPhoto { v.facePhoto = p }   // mover a la foto nueva
                // Vía con ALGÚN dato (nombre, grado o trazo) → se conserva/corrige.
                // Completamente vacía → se OMITE: si era existente el backend la
                // borra (reconcilia omitidas), evitando vías fantasma imborrables.
                let hasData = v.grade != nil || !v.name.isEmpty || !v.line.isEmpty
                if hasData { payload.append(v) }
            }
        }
        guard !payload.isEmpty else { sending = false; dismiss(); onDone(false); return }

        let req = ContributionRequest(
            type: "BOULDER", name: nil, lat: block.lat, lon: block.lon,
            notes: nil, description: nil, proposedLat: nil, proposedLon: nil, correctionReason: nil,
            targetBlockId: block.id, targetLineId: nil, sectorBlockId: nil,
            photoUrl: nil, bloquesJson: buildBloquesJson(payload), topoLinesJson: nil, discipline: discipline,
            geometry: geometry,
            path: isWall ? (tracedPath.isEmpty ? block.path : buildPathJson(tracedPath)) : nil,
            direction: direction, orientationsJson: nil)
        let ok = (try? await AppDependencies.shared.container.submitContribution.invoke(schoolId: schoolId, req: req)) != nil
        sending = false
        if ok {
            EditBlockDraftStore.clear(blockId: block.id)   // enviado: el borrador ya no aplica
            dismiss(); onDone(true)
        }
        else {
            // No se pudo enviar: en vez de dejarlo en "reinténtalo" (y que el
            // usuario tenga que repetirlo todo en casa), se ofrece GUARDARLO
            // para mandarlo al recuperar cobertura. Se PREGUNTA en vez de
            // encolar solo: si el servidor rechazó por otra cosa, encolar a
            // ciegas lo reintentaría eternamente.
            ofrecerGuardarOffline = true
        }
    }

    /// Guarda la edición para enviarla al recuperar cobertura. Las fotos nuevas
    /// se copian a disco (las de las caras sin tocar conservan su URL), igual
    /// que al proponer una piedra nueva sin red.
    func guardarSinCobertura() async {
        let fm = FileManager.default
        let dir = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("outbox-photos", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)

        var facesArr: [[String: Any]] = []
        for (i, faceVias) in faceBlocks.enumerated() {
            var localPath: Any = NSNull()
            if let img = facePicked[i], let data = img.jpegData(compressionQuality: 0.85) {
                let f = dir.appendingPathComponent(UUID().uuidString + ".jpg")
                if (try? data.write(to: f)) != nil { localPath = f.path }
            }
            let vias: [[String: Any]] = faceVias.compactMap { b in
                guard b.grade != nil || !b.name.isEmpty || !b.line.isEmpty else { return nil }
                return [
                    "name": b.name,
                    "grade": b.grade ?? NSNull(),
                    "startType": b.startType ?? NSNull(),
                    "points": b.line.map { [Double($0.x), Double($0.y)] },
                    "targetLineId": b.existingLineId ?? NSNull()
                ]
            }
            facesArr.append([
                "localPhotoPath": localPath,
                "existingPhotoPath": b_existingPhoto(i) ?? NSNull(),
                "vias": vias
            ])
        }
        let payload: [String: Any] = [
            "schoolId": schoolId,
            "targetBlockId": block.id,
            "lat": block.lat, "lon": block.lon,
            "discipline": discipline,
            "geometry": geometry,
            "pathJson": isWall ? (tracedPath.isEmpty ? (block.path ?? NSNull()) : buildPathJson(tracedPath)) : NSNull(),
            "direction": direction,
            "faces": facesArr
        ]
        guard let d = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: d, encoding: .utf8) else { return }
        try? await AppDependencies.shared.container
            .enqueueBlockEditContribution(schoolId: schoolId, payloadJson: json)
        EditBlockDraftStore.clear(blockId: block.id)   // encolado: el borrador ya no aplica
        dismiss(); onDone(false)
    }
}
