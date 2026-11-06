import SwiftUI
import Shared

// Estado de la ficha de una quedada (separado de la vista).

@MainActor
final class MeetupDetailViewModel: ObservableObject {
    @Published var meetup: Meetup?
    @Published var loading = false
    @Published var error: String?
    @Published var joining = false
    @Published var leaving = false
    @Published var dayScores: [String: Int] = [:]
    /** Enlace de invitación (solo si somos miembros; el backend lo exige). */
    @Published var inviteLink: String? = nil

    private let getMeetup    = AppDependencies.shared.container.getMeetup
    private let joinMeetup   = AppDependencies.shared.container.joinMeetup
    private let leaveMeetup  = AppDependencies.shared.container.leaveMeetup
    private let updateMeetup = AppDependencies.shared.container.updateMeetup
    private let kickMember   = AppDependencies.shared.container.kickMeetupMember
    private let reportMeetup = AppDependencies.shared.container.reportMeetup
    private let getRangeScores = AppDependencies.shared.container.getRangeScores

    @Published var savingDescription = false
    private let updateMyGearUC = AppDependencies.shared.container.updateMyGear
    private let getMyProfileUC = AppDependencies.shared.container.getMyProfile

    func updateMyGear(id: String, gearJson: String) async {
        do {
            meetup = try await updateMyGearUC.execute(meetupId: id, gearJson: gearJson)
        } catch { self.error = error.localizedDescription }
    }

    func updateDescription(id: String, text: String?) async {
        savingDescription = true
        do {
            if let uc = updateMeetup { meetup = try await uc.execute(meetupId: id, description: text) }
        } catch { self.error = error.localizedDescription }
        savingDescription = false
    }

    func load(id: String) async {
        loading = true; error = nil
        do {
            if let uc = getMeetup { meetup = try await uc.execute(id: id) }
        } catch { self.error = error.localizedDescription }
        loading = false
        // Enlace de invitación (si somos miembros): para el botón de compartir.
        if let m = meetup, m.joined {
            inviteLink = try? await AppDependencies.shared.container.meetupApi
                .getInviteLink(id: m.id)
        }
        // Load day scores
        if let m = meetup, !m.schoolId.isEmpty, !m.days.isEmpty {
            await loadDayScores(schoolId: m.schoolId, days: Array(m.days))
        }
    }

    func join(id: String) async {
        joining = true
        do {
            if let uc = joinMeetup {
                meetup = try await uc.execute(id: id)
                // Autorrelleno del material desde el perfil: si tienes algo
                // configurado, se copia solo al unirte (editable después desde
                // "MI MATERIAL" en la quedada). Si falla, no bloquea el join.
                if let myGear = try? await getMyProfileUC.invoke().gearJson,
                   !myGear.isEmpty, !parseGear(myGear).isEmpty {
                    meetup = try? await updateMyGearUC.execute(meetupId: id, gearJson: myGear)
                }
            }
        } catch { self.error = error.localizedDescription }
        joining = false
    }

    func leave(id: String) async {
        leaving = true
        do {
            if let uc = leaveMeetup {
                try await uc.execute(id: id)
                await load(id: id)
            }
        } catch { self.error = error.localizedDescription }
        leaving = false
    }

    func kick(meetupId: String, targetUid: String) async {
        do {
            try await kickMember.execute(meetupId: meetupId, targetUid: targetUid)
            await load(id: meetupId)
        } catch { self.error = error.localizedDescription }
    }

    func report(meetupId: String, reportedUid: String?, reason: String) async {
        do {
            try await reportMeetup.execute(
                meetupId: meetupId,
                reportedUid: reportedUid,
                reason: reason,
                context: nil
            )
            reportDone = true
        } catch { self.error = error.localizedDescription }
    }

    func deleteMeetup(id: String) async -> Bool {
        // Direct HTTP DELETE since DeleteMeetupUseCase isn't exposed in iOS container
        guard let url = URL(string: "\(AppConfig.apiBaseUrl)meetups/\(id)") else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        // Get auth token
        if let token = await withCheckedContinuation({ cont in
            AppDependencies.shared.authBridge.currentIdToken(forceRefresh: false) { t in cont.resume(returning: t) }
        }) {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode < 300 {
                return true
            }
            self.error = L("Error al eliminar")
            return false
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    @Published var reportDone = false

    private func loadDayScores(schoolId: String, days: [String]) async {
        do {
            let results = try await getRangeScores.invoke(ids: [schoolId], dates: days)
            if let school = results.first {
                var map: [String: Int] = [:]
                for day in school.days { map[day.date] = Int(day.score) }
                dayScores = map
            }
        } catch {}
    }
}

// ── Detail view ─────────────────────────────────────────────────────────────
