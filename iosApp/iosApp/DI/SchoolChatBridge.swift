import FirebaseFirestore
import FirebaseAuth
import Foundation
import Shared

/// Implementación Swift del bridge `IosSchoolChatBridge` (Kotlin iosMain) con
/// FirebaseFirestore. Colección `school_chats/{schoolId}/messages`, sin
/// comprobación de participantes (ver `firestore.rules`) — a diferencia de
/// `ChatBridge`, no hay documento de conversación que actualizar ni contador
/// de no leídos: es un tablón abierto, no una lista de chats.
final class SchoolChatBridge: NSObject, IosSchoolChatBridge {

    private lazy var db = Firestore.firestore()

    private func millis(_ any: Any?) -> Int64 {
        guard let ts = any as? Timestamp else { return -1 }
        return Int64(ts.dateValue().timeIntervalSince1970 * 1000)
    }

    func observeMessages(schoolId: String, limit: Int32, onChange: @escaping ([IosSchoolMsgDto]) -> Void) -> IosChatListener {
        let reg = db.collection("school_chats").document(schoolId).collection("messages")
            .order(by: "createdAt")
            .limit(toLast: Int(limit))
            .addSnapshotListener { snap, _ in
                let list: [IosSchoolMsgDto] = snap?.documents.map { doc in
                    let d = doc.data()
                    return IosSchoolMsgDto(
                        id: doc.documentID,
                        fromUid: d["fromUid"] as? String ?? "",
                        text: d["text"] as? String ?? "",
                        createdAtMillis: self.millis(d["createdAt"]))
                } ?? []
                onChange(list)
            }
        return ChatListenerHandle(reg)
    }

    func sendMessage(schoolId: String, text: String, completion: @escaping (String?) -> Void) {
        guard let me = Auth.auth().currentUser?.uid else { completion("No hay sesión"); return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 500 else { completion("Mensaje vacío o muy largo"); return }
        db.collection("school_chats").document(schoolId).collection("messages")
            .addDocument(data: ["fromUid": me, "text": trimmed, "createdAt": Timestamp(date: Date())]) { err in
                completion(err?.localizedDescription)
            }
    }
}
