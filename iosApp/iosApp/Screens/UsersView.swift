import SwiftUI
import Shared

// Búsqueda de usuarios + perfil público con seguir/dejar de seguir.
// Espejo de SearchUsersScreen.kt y PublicProfileScreen.kt de Android.

@MainActor
final class PublicProfileViewModel: ObservableObject {
    @Published var profile: PublicProfile?
    @Published var status: FollowStatus?
    @Published var stats: JournalStats?
    @Published var entries: [JournalSession] = []
    @Published var viaInfo: [String: ViaCatalogInfo] = [:]
    @Published var loading = true
    /// Interruptor del asistente de IA de ESTE usuario. nil = no se enseña (no eres admin, o es tu propio perfil).
    @Published var adminAssistant: Bool?
    @Published var adminAssistantSaving = false
    /// Solo admin: aportaciones de este usuario y si es de confianza.
    @Published var adminContrib: ContributorItem?
    /// Solo admin: denuncias, avisos y sanciones de este usuario.
    @Published var adminModeration: UserModerationDto?

    private let getMyProfile: GetMyProfileUseCase
    private let getUserAssistantAccess: GetUserAssistantAccessUseCase
    private let setUserAssistantAccess: SetUserAssistantAccessUseCase
    private let getPublicProfile: GetPublicProfileUseCase
    private let getFollowStatus: GetFollowStatusUseCase
    private let followUser: FollowUserUseCase
    private let unfollowUser: UnfollowUserUseCase
    private let getUserStats: GetUserStatsUseCase
    private let getUserJournal: GetUserJournalUseCase
    private let getJournalViaInfo: GetJournalViaInfoUseCase

    init(
        getPublicProfile: GetPublicProfileUseCase = AppDependencies.shared.container.getPublicProfile,
        getFollowStatus: GetFollowStatusUseCase = AppDependencies.shared.container.getFollowStatus,
        followUser: FollowUserUseCase = AppDependencies.shared.container.followUser,
        unfollowUser: UnfollowUserUseCase = AppDependencies.shared.container.unfollowUser,
        getUserStats: GetUserStatsUseCase = AppDependencies.shared.container.getUserStats,
        getUserJournal: GetUserJournalUseCase = AppDependencies.shared.container.getUserJournal,
        getJournalViaInfo: GetJournalViaInfoUseCase = AppDependencies.shared.container.getJournalViaInfo,
        getMyProfile: GetMyProfileUseCase = AppDependencies.shared.container.getMyProfile,
        getUserAssistantAccess: GetUserAssistantAccessUseCase = AppDependencies.shared.container.getUserAssistantAccess,
        setUserAssistantAccess: SetUserAssistantAccessUseCase = AppDependencies.shared.container.setUserAssistantAccess
    ) {
        self.getMyProfile = getMyProfile
        self.getUserAssistantAccess = getUserAssistantAccess
        self.setUserAssistantAccess = setUserAssistantAccess
        self.getPublicProfile = getPublicProfile
        self.getFollowStatus = getFollowStatus
        self.followUser = followUser
        self.unfollowUser = unfollowUser
        self.getUserStats = getUserStats
        self.getUserJournal = getUserJournal
        self.getJournalViaInfo = getJournalViaInfo
    }

    func load(uid: String) async {
        loading = true
        profile = try? await getPublicProfile.invoke(uid: uid)
        status = try? await getFollowStatus.invoke(uid: uid)
        loading = false
        // Diario y stats del usuario (solo si su perfil no está bloqueado).
        await loadJournal(uid: uid)
        await loadAdminAssistant()
    }

    /// Solo si quien mira es admin (y no es su propio perfil): lee el interruptor del asistente de IA de este usuario.
    private func loadAdminAssistant() async {
        adminAssistant = nil
        guard let profileUid = profile?.uid,
              let me = try? await getMyProfile.invoke(), me.isAdmin, me.uid != profileUid,
              let enabled = try? await getUserAssistantAccess.invoke(uid: profileUid) else { return }
        adminAssistant = enabled.boolValue
        // Aportaciones y confianza de este usuario (solo admin). Si no ha aportado nada, ceros.
        let list = (try? await AppDependencies.shared.container.getContributors.invoke(uid: profileUid)) ?? []
        adminContrib = list.first ?? ContributorItem(uid: profileUid, name: nil, approved: 0, rejected: 0, pending: 0,
                                                     lastAt: nil, trusted: false)
        // Denuncias, avisos y estado (baneo / suspensión) de esta persona.
        adminModeration = (try? await AppDependencies.shared.container.moderationApi.getUserModeration(uid: profileUid)) ?? nil
    }

    /// Vuelve a leer las denuncias/sanciones (tras actuar en la consola).
    func refreshModeration() async {
        guard let profileUid = profile?.uid, adminModeration != nil else { return }
        adminModeration = (try? await AppDependencies.shared.container.moderationApi.getUserModeration(uid: profileUid)) ?? adminModeration
    }

    /// Da o quita la confianza (publicar sin revisión). Si falla, el interruptor vuelve a su sitio.
    func setTrust(_ trusted: Bool) {
        guard let c = adminContrib else { return }
        adminContrib = c.doCopy(uid: c.uid, name: c.name, approved: c.approved, rejected: c.rejected, pending: c.pending,
                                lastAt: c.lastAt, trusted: trusted)
        Task {
            if (try? await AppDependencies.shared.container.setUserTrust.invoke(uid: c.uid, trusted: trusted)) == nil {
                adminContrib = c
            }
        }
    }

    /// Activa o desactiva el asistente de IA de este usuario. Si falla, el interruptor vuelve a su sitio.
    func setAssistantAccess(_ enabled: Bool) {
        guard let profileUid = profile?.uid, let before = adminAssistant else { return }
        adminAssistant = enabled
        adminAssistantSaving = true
        Task {
            if let saved = try? await setUserAssistantAccess.invoke(uid: profileUid, enabled: enabled) {
                adminAssistant = saved.boolValue
            } else {
                adminAssistant = before
            }
            adminAssistantSaving = false
        }
    }

    /// Carga el diario/stats del usuario. Si su perfil es privado y no le sigues,
    /// el backend devuelve 403 → quedan vacíos (y la UI no muestra la sección).
    func loadJournal(uid: String) async {
        if profile?.locked == true { stats = nil; entries = []; viaInfo = [:]; return }
        stats = try? await getUserStats.invoke(uid: uid)
        // Solo HECHO: los PROYECTOS tienen su propia pantalla (ProjectsView).
        entries = ((try? await getUserJournal.invoke(uid: uid)) ?? []).filter { $0.status != "PROJECT" }
        // Nº de piedra + sector de cada vía, resueltos en vivo del catálogo.
        viaInfo = (try? await getJournalViaInfo.invoke(entries: entries)) ?? [:]
    }

    func toggleFollow(uid: String) {
        guard let s = status else { return }
        let wasFollowing = s.iFollowThem
        Task {
            do {
                if wasFollowing { try await unfollowUser.invoke(uid: uid) }
                else { try await followUser.invoke(uid: uid) }
                status = try? await getFollowStatus.invoke(uid: uid)
                profile = try? await getPublicProfile.invoke(uid: uid)
                await loadJournal(uid: uid)   // tras aceptar/seguir puede desbloquearse
            } catch {}
        }
    }
}

struct PublicProfileView: View {
    /// Lo que llega por la navegación: puede ser el uid O el username (las
    /// menciones @usuario del feed solo conocen el username).
    let uid: String
    /// El uid REAL. Todo lo que identifica a la persona —chat, listas de
    /// seguidores, bloquear, denunciar— tiene que usar ESTE, no el de la ruta:
    /// el chat compone el id de la conversación en Firestore ordenando los dos
    /// uids, así que con un username apuntaba a una conversación inexistente
    /// (salía vacía y el mensaje no se enviaba). Solo la carga del perfil usa
    /// el identificador tal cual, porque el servidor sí resuelve las dos formas.
    private var realUid: String { vm.profile?.uid ?? uid }
    @StateObject private var vm = PublicProfileViewModel()
    // Moderación: denunciar / bloquear a este usuario (menú superior).
    @ObservedObject private var moderation = ModerationStore.shared
    @State private var showReport = false
    @State private var showAdminSheet = false   // solo admin: hoja con asistente, aportaciones, confianza y denuncias
    @State private var showModeration = false   // solo admin: consola de denuncias/sanciones de este usuario
    @State private var zoomPhoto = false   // foto de perfil a pantalla completa

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                // Solo admins: botón arriba a la izquierda que abre todo lo de gestión de esta persona.
                if vm.adminAssistant != nil {
                    HStack { adminButton; Spacer() }.padding(.bottom, -4)
                }
                // Avatar ampliable con zoom al tocarlo (si tiene foto).
                AvatarCircle(url: vm.profile?.photoUrl, size: 76, uid: vm.profile?.uid)
                    .onTapGesture { if vm.profile?.photoUrl != nil { zoomPhoto = true } }
                HStack(spacing: 6) {
                    Text(vm.profile?.displayName ?? vm.profile?.username ?? L("Usuario"))
                        .font(Cumbre.serif(22, .bold)).foregroundStyle(Cumbre.ink)
                    VerifiedCheck(uid: vm.profile?.uid, size: 18)
                }
                if let u = vm.profile?.username, !u.isEmpty {
                    Text("@\(u)").font(Cumbre.mono(13)).foregroundStyle(Cumbre.ink3)
                }
                // Título, insignias y viero/bloquero de esta persona.
                if let profileUid = vm.profile?.uid { RewardsBlock(uid: profileUid) }
                if vm.profile?.locked == true {
                    // Perfil privado: bloqueado para quien no le sigue. Solo
                    // nombre/avatar + botón para solicitar seguir.
                    VStack(spacing: 8) {
                        Image(systemName: "lock.fill").font(.system(size: 22)).foregroundStyle(Cumbre.ink3)
                        Text("Este perfil es privado")
                            .font(Cumbre.mono(11, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink2)
                        Text("Sigue a este usuario para ver su diario y estadísticas.")
                            .font(.system(size: 13)).foregroundStyle(Cumbre.ink3)
                            .multilineTextAlignment(.center).padding(.horizontal, 32)
                    }.padding(.vertical, 8)
                    if let s = vm.status { followButton(s) }
                } else {
                    if let bio = vm.profile?.bio, !bio.isEmpty {
                        Text(bio).font(.system(size: 15)).foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.center).padding(.horizontal, 24)
                    }
                    // El grado tope ya NO es manual: se ve automático (MÁX BLOQUE /
                    // MÁX VÍA del diario) en la sección DIARIO de abajo.
                    if let s = vm.status {
                        HStack(spacing: 20) {
                            NavigationLink(destination: FollowListView(uid: realUid, mode: .followers)) {
                                stat("\(s.followers)", L("SEGUIDORES"))
                            }.buttonStyle(.plain)
                            NavigationLink(destination: FollowListView(uid: realUid, mode: .following)) {
                                stat("\(s.following)", L("SIGUIENDO"))
                            }.buttonStyle(.plain)
                            // «Te sigue»: una etiqueta clara, no un texto suelto.
                            if s.theyFollowMe {
                                Label(L("TE SIGUE"), systemImage: "person.fill.checkmark")
                                    .font(Cumbre.mono(9.5, .bold)).foregroundStyle(Cumbre.ok)
                                    .padding(.horizontal, 9).padding(.vertical, 5)
                                    .background(Cumbre.ok.opacity(0.10), in: Capsule())
                                    .overlay(Capsule().stroke(Cumbre.ok.opacity(0.5), lineWidth: 1))
                            }
                        }
                        // Seguir y mensaje, uno al lado del otro (antes, dos botones grandes apilados).
                        HStack(spacing: 10) {
                            followButton(s)
                            NavigationLink(destination: ChatView(
                                otherUid: realUid,
                                otherName: vm.profile?.displayName ?? vm.profile?.username ?? L("Usuario"))) {
                                Label(NSLocalizedString("chat_message", comment: ""), systemImage: "bubble.left.fill")
                                    .font(Cumbre.mono(11.5, .bold)).foregroundStyle(Cumbre.terra)
                                    .frame(maxWidth: .infinity).padding(.vertical, 11)
                                    .background(Cumbre.terra.opacity(0.08), in: Capsule())
                                    .overlay(Capsule().stroke(Cumbre.terra, lineWidth: 1))
                            }.buttonStyle(.plain)
                        }
                    }
                    // Diario del usuario: BLOQUES / ESCUELAS / MÁXIMO navegables
                    // (igual que tu cuenta). Solo si su perfil es visible.
                    if let st = vm.stats {
                        // H: incluye las celdas ESTADÍSTICAS y PUBLICACIONES
                        // (pulsables, como el resto del perfil).
                        JournalStatsNav(stats: st, entries: vm.entries, viaInfo: vm.viaInfo,
                                        projectsUid: realUid, showStatsAndPosts: true)
                    }
                    // H: las publicaciones ya NO van inline — se abren desde
                    // la celda PUBLICACIONES (igual que en el perfil propio).
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 8).padding(.horizontal, 20)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Perfil")
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(isPresented: $zoomPhoto) {
            if let url = vm.profile?.photoUrl {
                FullScreenPhotoView(photoUrl: url) { zoomPhoto = false }
            }
        }
        .toolbar {
            // Compartir el perfil: imagen 1080×1920 (formato historia) →
            // Instagram Stories, WhatsApp... + enlace /s/u/ (paridad Android).
            ToolbarItem(placement: .topBarTrailing) {
                let handle = vm.profile?.username ?? uid
                let label = vm.profile?.username.map { "@" + $0 }
                    ?? vm.profile?.displayName ?? L("este escalador")
                Button {
                    Task {
                        let st = vm.stats
                        await ShareProfileImage.share(
                            handle: handle, displayLabel: label,
                            username: vm.profile?.username,
                            photoUrl: vm.profile?.photoUrl,
                            topGrade: vm.profile?.topGrade,
                            bio: vm.profile?.bio,
                            boulders: st.map { Int($0.boulderCount) },
                            routes: st.map { Int($0.routeCount) },
                            schools: st.map { Int($0.schoolCount) })
                    }
                } label: {
                    Image(systemName: "square.and.arrow.up").foregroundStyle(Cumbre.ink2)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { showReport = true } label: {
                        Label("Denunciar usuario", systemImage: "flag")
                    }
                    if moderation.blocked.contains(realUid) {
                        Button { moderation.unblock(realUid) } label: {
                            Label("Desbloquear", systemImage: "hand.raised.slash")
                        }
                    } else {
                        Button(role: .destructive) { moderation.block(realUid) } label: {
                            Label("Bloquear (no verás su contenido)", systemImage: "hand.raised")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis").foregroundStyle(Cumbre.ink2)
                }
            }
        }
        .sheet(isPresented: $showAdminSheet) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(L("ADMIN · %@", vm.profile?.username.map { "@" + $0 } ?? vm.profile?.displayName ?? "")).eyebrow(Cumbre.terra)
                    adminAssistantCard
                    if let u = vm.profile?.uid {
                        SpecialTitleAdminCard(uid: u)
                            .padding(12)
                            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                    }
                }
                .padding(16)
            }
            .background(Cumbre.bg.ignoresSafeArea())
            // Entera desde el principio: a media altura no se veía el TÍTULO ESPECIAL (Álvaro, 2026-10-19).
            .presentationDetents([.large])
        }
        .sheet(isPresented: $showModeration, onDismiss: { Task { await vm.refreshModeration() } }) {
            if let u = vm.profile?.uid { UserModerationSheet(uid: u) }
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(title: L("DENUNCIAR USUARIO"),
                        authorLabel: vm.profile?.username.map { "@" + $0 } ?? "usuario") { reason, alsoBlock, details in
                moderation.report(targetType: "USER", targetId: realUid, reason: reason, details: details,
                                  alsoBlockUid: alsoBlock ? realUid : nil)
            }
        }
        .task { await vm.load(uid: uid); await moderation.loadBlocked() }
    }

    /// Resumen para el botón ADMIN: qué tiene esa persona («2 DENUNCIAS · 1 AVISO · BANEADA»), vacío si nada.
    private var adminReason: String {
        guard let m = vm.adminModeration else { return "" }
        var parts: [String] = []
        if m.reportCount > 0 { parts.append("\(m.reportCount) " + (m.reportCount == 1 ? L("DENUNCIA") : L("DENUNCIAS"))) }
        if m.warnings > 0 { parts.append("\(m.warnings) " + (m.warnings == 1 ? L("AVISO") : L("AVISOS"))) }
        if m.banned { parts.append(L("BANEADA")) }
        return parts.joined(separator: " · ")
    }

    /// El botón ADMIN (arriba a la izquierda): rojo, con el motivo, si esa persona tiene denuncias, avisos o está baneada.
    private var adminButton: some View {
        let m = vm.adminModeration
        let bad = (m?.reportCount ?? 0) > 0 || (m?.warnings ?? 0) > 0 || m?.banned == true
        return Button { showAdminSheet = true } label: {
            HStack(spacing: 6) {
                Image(systemName: "shield.lefthalf.filled").font(.system(size: 12))
                Text(L("ADMIN")).font(Cumbre.mono(11, .bold)).tracking(1)
                if bad { Text("· " + adminReason).font(Cumbre.mono(10, .bold)).lineLimit(1) }
            }
            .foregroundStyle(bad ? Color.white : Cumbre.terra)
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(bad ? Cumbre.bad : Color.clear, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(bad ? Cumbre.bad : Cumbre.terra, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    /// Solo admins: activar o desactivar el asistente de IA a este usuario. Nadie más ve este bloque.
    @ViewBuilder private var adminAssistantCard: some View {
        if let on = vm.adminAssistant {
            VStack(alignment: .leading, spacing: 6) {
                Text(L("SOLO ADMIN")).font(Cumbre.mono(10, .bold)).tracking(1.4).foregroundStyle(Cumbre.terra)
                Toggle(isOn: Binding(get: { on }, set: { vm.setAssistantAccess($0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("Asistente de IA")).font(.system(size: 15, weight: .medium)).foregroundStyle(Cumbre.ink)
                        Text(on ? L("Activado para este usuario") : L("Desactivado para este usuario"))
                            .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                    }
                }
                .tint(Cumbre.terra)
                .disabled(vm.adminAssistantSaving)
                Text(L("Nadie más ve este bloque. Al activarlo, le aparece la burbuja del asistente en cuanto abra la app."))
                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                if let c = vm.adminContrib {
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    Text(L("APORTACIONES: %@ aprobadas · %@ rechazadas · %@ pendientes", c.approved, c.rejected, c.pending))
                        .font(Cumbre.mono(10.5, .bold)).foregroundStyle(Cumbre.ink2)
                    Toggle(isOn: Binding(get: { c.trusted }, set: { vm.setTrust($0) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L("De confianza")).font(.system(size: 15, weight: .medium)).foregroundStyle(Cumbre.ink)
                            Text(L("Lo que proponga se publica sin revisión (llega sola con 20 aprobadas)."))
                                .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                        }
                    }
                    .tint(Cumbre.terra)
                }
                if let m = vm.adminModeration {
                    Divider().overlay(Cumbre.rule).padding(.vertical, 4)
                    Button { showModeration = true } label: {
                        HStack {
                            let bad = m.reportCount > 0 || m.warnings > 0 || m.banned
                            Text(L("DENUNCIAS: %@ · AVISOS: %@", m.reportCount, m.warnings)
                                 + (m.banned ? " · " + L("BANEADO") : ""))
                                .font(Cumbre.mono(10.5, .bold)).foregroundStyle(bad ? Cumbre.bad : Cumbre.ok)
                            Spacer()
                            Text(L("VER / ACTUAR")).font(Cumbre.mono(10, .bold)).foregroundStyle(Cumbre.terra)
                        }.contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Cumbre.terra.opacity(0.6), lineWidth: 1))
            .padding(.top, 8)
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
            Text(label).font(Cumbre.mono(9, .bold)).tracking(0.8).foregroundStyle(Cumbre.ink3)
        }
    }

    @ViewBuilder private func followButton(_ s: FollowStatus) -> some View {
        let following = s.iFollowThem
        let pending = s.requestPending
        Button { vm.toggleFollow(uid: realUid) } label: {
            Label(pending ? NSLocalizedString("profile_requested", comment: "") : (following ? L("SIGUIENDO") : NSLocalizedString("profile_follow", comment: "")),
                  systemImage: pending ? "clock" : (following ? "checkmark" : "person.badge.plus"))
                .font(Cumbre.mono(11.5, .bold))
                .foregroundStyle(following || pending ? Cumbre.ink2 : .white)
                .frame(maxWidth: .infinity).padding(.vertical, 11)
                .background(following || pending ? Cumbre.paper : Cumbre.terraFill, in: Capsule())
                .overlay(Capsule().stroke(following || pending ? Cumbre.rule : Color.clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(pending)
    }
}

// MARK: - Avatar reutilizable

struct AvatarCircle: View {
    let url: String?
    let size: CGFloat
    /// De quién es la foto: con él se pinta la aureola de su título (Álvaro, 2026-10-19). nil = sin aureola.
    var uid: String? = nil
    @State private var image: UIImage?
    @ObservedObject private var badges = UserBadgeStore.shared
    /// Lleva hadas o dragones animados (foto grande con efecto y título visible).
    private var effectRoom: Bool {
        guard size >= 60, let b = uid.flatMap({ badges.badges[$0] }), !b.titleHidden else { return false }
        return TitleEffectKind(b.titleEffect) != nil
    }
    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().foregroundStyle(Cumbre.ink3)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Cumbre.rule, lineWidth: 1))
        .overlay(AvatarRing(badge: uid.flatMap { badges.badges[$0] }, size: size))
        // Con hadas o dragones animados (perfil): hueco encima y debajo para que no se corten ni tapen el nombre.
        .padding(.top, effectRoom ? size * 0.5 : 0)
        .padding(.bottom, effectRoom ? size * 0.42 : 0)
        .onAppear { badges.want(uid) }
        // Caché en disco (igual que las fotos de piedras): el avatar se ve SIN
        // conexión una vez descargado. AsyncImage no persistía en disco → offline
        // quedaba en el placeholder aunque tuviéramos la URL cacheada.
        .task(id: url) {
            image = nil
            guard let url, !url.isEmpty else { return }
            if let img = await ImageCache.image(url) { image = img }
        }
    }
}

// MARK: - Lista de seguidores / seguidos

