import FirebaseFirestore
import FirebaseAuth
import Foundation
import Shared

/// Handle de un snapshot listener de Firestore para el lado Kotlin (lo quita en
/// `awaitClose` cuando el Flow se cancela).
final class ChatListenerHandle: NSObject, IosChatListener {
    private let registration: ListenerRegistration?
    init(_ r: ListenerRegistration?) { self.registration = r }
    func remove() { registration?.remove() }
}

/// Implementación Swift del bridge `IosChatBridge` (Kotlin iosMain) con
/// FirebaseFirestore. MISMA estructura Firestore que `FirebaseChatService` de
/// Android: colección `conversations` + subcolección `messages`.
final class ChatBridge: NSObject, IosChatBridge {

    // `lazy`: Firestore NO se inicializa al arrancar la app (solo al usar el chat),
    // para no añadir Firestore al arranque (evita cierres en modo avión, etc.).
    private lazy var db = Firestore.firestore()
    private var convs: CollectionReference { db.collection("conversations") }

    private func convIdFor(_ a: String, _ b: String) -> String {
        [a, b].sorted().joined(separator: "_")
    }

    private func millis(_ any: Any?) -> Int64 {
        guard let ts = any as? Timestamp else { return -1 }
        return Int64(ts.dateValue().timeIntervalSince1970 * 1000)
    }

    func observeConversations(onChange: @escaping ([IosConvDto]) -> Void) -> IosChatListener {
        guard let me = Auth.auth().currentUser?.uid else { onChange([]); return ChatListenerHandle(nil) }
        // Solo filtramos por `arrayContains` y ordenamos en cliente: combinar
        // `arrayContains` + `order(by:)` exige un índice compuesto en Firestore;
        // sin él el listener falla. Ordenar aquí evita esa dependencia.
        let reg = convs
            .whereField("participants", arrayContains: me)
            .addSnapshotListener { snap, _ in
                let list: [IosConvDto] = (snap?.documents.compactMap { doc -> IosConvDto? in
                    let d = doc.data()
                    let participants = (d["participants"] as? [String]) ?? []
                    if participants.isEmpty { return nil }
                    let unread = (d["unread_\(me)"] as? NSNumber)?.int64Value ?? 0
                    return IosConvDto(
                        id: doc.documentID,
                        participants: participants,
                        lastMessage: d["lastMessage"] as? String,
                        lastFromUid: d["lastFromUid"] as? String,
                        lastAtMillis: self.millis(d["lastAt"]),
                        unreadCount: unread,
                        clearedAtMillis: self.millis(d["cleared_\(me)"]),
                        isGroup: (d["isGroup"] as? Bool) ?? false,
                        name: d["name"] as? String)
                } ?? [])
                .sorted { $0.lastAtMillis > $1.lastAtMillis }
                onChange(list)
            }
        return ChatListenerHandle(reg)
    }

    func observeMessages(convId: String, limit: Int32, onChange: @escaping ([IosMsgDto]) -> Void) -> IosChatListener {
        let reg = convs.document(convId).collection("messages")
            .order(by: "createdAt")
            .limit(toLast: Int(limit))
            .addSnapshotListener { snap, _ in
                let list: [IosMsgDto] = snap?.documents.map { doc in
                    let d = doc.data()
                    return IosMsgDto(
                        id: doc.documentID,
                        fromUid: d["fromUid"] as? String ?? "",
                        text: d["text"] as? String ?? "",
                        createdAtMillis: self.millis(d["createdAt"]),
                        replyToId: d["replyToId"] as? String,
                        replyText: d["replyText"] as? String,
                        replyFromUid: d["replyFromUid"] as? String)
                } ?? []
                onChange(list)
            }
        return ChatListenerHandle(reg)
    }

    /// Construye el doc del mensaje; añade los campos reply* solo si es respuesta.
    private func messageData(_ me: String, _ text: String, _ now: Timestamp,
                             _ replyToId: String?, _ replyText: String?, _ replyFromUid: String?) -> [String: Any] {
        var data: [String: Any] = ["fromUid": me, "text": text, "createdAt": now]
        if let rid = replyToId {
            data["replyToId"] = rid
            if let rt = replyText { data["replyText"] = rt }
            if let rf = replyFromUid { data["replyFromUid"] = rf }
        }
        return data
    }

    func sendMessage(otherUid: String, text: String,
                     replyToId: String?, replyText: String?, replyFromUid: String?,
                     completion: @escaping (String?) -> Void) {
        guard let me = Auth.auth().currentUser?.uid else { completion("No hay sesión"); return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 1000 else { completion("Mensaje vacío o muy largo"); return }
        let ref = convs.document(convIdFor(me, otherUid))
        let now = Timestamp(date: Date())
        ref.collection("messages").addDocument(data: messageData(me, trimmed, now, replyToId, replyText, replyFromUid)) { err in
            if let err = err { completion(err.localizedDescription); return }
            ref.setData([
                "participants": [me, otherUid].sorted(),
                "lastMessage": trimmed,
                "lastFromUid": me,
                "lastAt": now,
                "unread_\(otherUid)": FieldValue.increment(Int64(1))
            ], merge: true) { err2 in completion(err2?.localizedDescription) }
        }
    }

    func sendGroupMessage(convId: String, text: String,
                          replyToId: String?, replyText: String?, replyFromUid: String?,
                          completion: @escaping (String?) -> Void) {
        guard let me = Auth.auth().currentUser?.uid else { completion("No hay sesión"); return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 1000 else { completion("Mensaje vacío o muy largo"); return }
        let ref = convs.document(convId)
        // Necesito los participantes para subir el contador a todos menos a mí.
        ref.getDocument { snap, _ in
            let participants = (snap?.data()?["participants"] as? [String]) ?? []
            let now = Timestamp(date: Date())
            ref.collection("messages").addDocument(data: self.messageData(me, trimmed, now, replyToId, replyText, replyFromUid)) { err in
                if let err = err { completion(err.localizedDescription); return }
                var update: [String: Any] = ["lastMessage": trimmed, "lastFromUid": me, "lastAt": now]
                for uid in participants where uid != me {
                    update["unread_\(uid)"] = FieldValue.increment(Int64(1))
                }
                ref.setData(update, merge: true) { err2 in completion(err2?.localizedDescription) }
            }
        }
    }

    func markRead(convId: String, completion: @escaping (String?) -> Void) {
        guard let me = Auth.auth().currentUser?.uid else { completion(nil); return }
        convs.document(convId).setData(["unread_\(me)": 0], merge: true) { err in
            completion(err?.localizedDescription)
        }
    }

    func markUnread(convId: String, completion: @escaping (String?) -> Void) {
        guard let me = Auth.auth().currentUser?.uid else { completion(nil); return }
        convs.document(convId).setData(["unread_\(me)": 1], merge: true) { err in
            completion(err?.localizedDescription)
        }
    }

    func deleteConversation(convId: String, completion: @escaping (String?) -> Void) {
        // "Borrar para mí": marca cleared_<me> = ahora. No toca los datos del otro.
        guard let me = Auth.auth().currentUser?.uid else { completion(nil); return }
        convs.document(convId).setData(["cleared_\(me)": Timestamp(date: Date())], merge: true) { err in
            completion(err?.localizedDescription)
        }
    }
}
