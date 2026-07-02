import Foundation
import Shared

/// Caché LOCAL del perfil privado + stats + diario, para poder ver el perfil
/// SIN conexión (espejo del ProfileCache de Android). Se guarda cada vez que el
/// perfil carga online; offline se reconstruye desde aquí. Persiste en
/// UserDefaults como JSON (los modelos Kotlin no son Codable → snapshots propios).
final class ProfileCache {
    static let shared = ProfileCache()
    private let key = "profile_cache_snapshot"
    private init() {}

    func save(profile: PrivateProfile, stats: JournalStats?, entries: [JournalSession], followers: Int64, following: Int64) {
        let snap = Snapshot(
            profile: ProfileSnap(profile),
            stats: stats.map { StatsSnap($0) },
            entries: entries.map { EntrySnap($0) },
            followers: followers,
            following: following
        )
        if let data = try? JSONEncoder().encode(snap) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    /// Última copia cacheada reconstruida como modelos Kotlin, o nil si nunca cargó online.
    func load() -> Cached? {
        guard let data = UserDefaults.standard.data(forKey: key),
              let snap = try? JSONDecoder().decode(Snapshot.self, from: data) else { return nil }
        return Cached(
            profile: snap.profile.toModel(),
            stats: snap.stats?.toModel(),
            entries: snap.entries.map { $0.toModel() },
            followers: snap.followers,
            following: snap.following
        )
    }

    struct Cached {
        let profile: PrivateProfile
        let stats: JournalStats?
        let entries: [JournalSession]
        let followers: Int64
        let following: Int64
    }

    // MARK: - Snapshots Codable

    private struct Snapshot: Codable {
        let profile: ProfileSnap
        let stats: StatsSnap?
        let entries: [EntrySnap]
        let followers: Int64
        let following: Int64
    }

    private struct ProfileSnap: Codable {
        let uid: String
        let email: String?, username: String?, displayName: String?
        let photoUrl: String?, bio: String?, topGrade: String?
        let isPublic: Bool, isAdmin: Bool, isPremium: Bool
        init(_ p: PrivateProfile) {
            uid = p.uid; email = p.email; username = p.username; displayName = p.displayName
            photoUrl = p.photoUrl; bio = p.bio; topGrade = p.topGrade
            isPublic = p.isPublic; isAdmin = p.isAdmin; isPremium = p.isPremium
            gender = p.gender
            gearJson = p.gearJson
        }
        var gender: String?
        var gearJson: String?
        func toModel() -> PrivateProfile {
            PrivateProfile(uid: uid, email: email, username: username, displayName: displayName,
                           photoUrl: photoUrl, bio: bio, topGrade: topGrade,
                           isPublic: isPublic, isAdmin: isAdmin, isPremium: isPremium,
                           gender: gender, gearJson: gearJson)
        }
    }

    private struct StatsSnap: Codable {
        let blockCount: Int32, schoolCount: Int32
        let maxGrade: String?
        let bySchool: [SchoolStatSnap]
        let boulderCount: Int32, routeCount: Int32
        let maxBoulderGrade: String?, maxRouteGrade: String?
        let projectCount: Int32, projectBoulderCount: Int32, projectRouteCount: Int32
        init(_ s: JournalStats) {
            blockCount = s.blockCount; schoolCount = s.schoolCount
            maxGrade = s.maxGrade; bySchool = s.bySchool.map { SchoolStatSnap($0) }
            boulderCount = s.boulderCount; routeCount = s.routeCount
            maxBoulderGrade = s.maxBoulderGrade; maxRouteGrade = s.maxRouteGrade
            projectCount = s.projectCount; projectBoulderCount = s.projectBoulderCount
            projectRouteCount = s.projectRouteCount
        }
        func toModel() -> JournalStats {
            JournalStats(blockCount: blockCount, boulderCount: boulderCount, routeCount: routeCount,
                         schoolCount: schoolCount, maxGrade: maxGrade,
                         maxBoulderGrade: maxBoulderGrade, maxRouteGrade: maxRouteGrade,
                         bySchool: bySchool.map { $0.toModel() },
                         projectCount: projectCount, projectBoulderCount: projectBoulderCount,
                         projectRouteCount: projectRouteCount)
        }
    }

    private struct SchoolStatSnap: Codable {
        let schoolName: String, blockCount: Int32, maxGrade: String?
        init(_ s: SchoolStats) { schoolName = s.schoolName; blockCount = s.blockCount; maxGrade = s.maxGrade }
        func toModel() -> SchoolStats {
            SchoolStats(schoolName: schoolName, blockCount: blockCount, maxGrade: maxGrade)
        }
    }

    private struct EntrySnap: Codable {
        let id: String, schoolId: String?, schoolName: String?, sector: String?
        let blockName: String, grade: String?, notes: String?, date: String, createdAt: String
        let discipline: String
        let status: String
        init(_ e: JournalSession) {
            id = e.id; schoolId = e.schoolId; schoolName = e.schoolName; sector = e.sector
            blockName = e.blockName; grade = e.grade; notes = e.notes; date = e.date; createdAt = e.createdAt
            discipline = e.discipline
            status = e.status
        }
        func toModel() -> JournalSession {
            JournalSession(id: id, schoolId: schoolId, schoolName: schoolName, sector: sector,
                           blockName: blockName, grade: grade, notes: notes, date: date, createdAt: createdAt,
                           discipline: discipline, lineId: nil, status: status)
        }
    }
}
