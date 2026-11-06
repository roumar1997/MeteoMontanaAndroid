import SwiftUI
import Shared
import CoreLocation

// Pestañas de admin: avisos push y notas.

/// Tab PUSH — envío manual de notificación (espejo de PushTab). Rehecha el 2026-10-18: buscar y elegir VARIAS
/// personas (chips), o a todos; con vista previa del aviso.
struct AdminPushTab: View {
    @ObservedObject var vm: AdminViewModel
    @State private var recipients: [PublicProfile] = []
    @State private var query = ""
    @State private var results: [PublicProfile] = []
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var title = ""
    @State private var body_ = ""
    @State private var confirmAll = false

    private var canSend: Bool { !vm.pushBusy && !title.trimmingCharacters(in: .whitespaces).isEmpty && !body_.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    AdminSectionLabel(text: "PARA QUIÉN")
                    if recipients.isEmpty {
                        AdminChip(text: "TODOS LOS USUARIOS", color: Cumbre.terra, filled: true)
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 6)], alignment: .leading, spacing: 6) {
                            ForEach(recipients, id: \.uid) { u in
                                Button { recipients.removeAll { $0.uid == u.uid } } label: {
                                    HStack(spacing: 4) {
                                        Text(name(u)).font(Cumbre.mono(10.5, .bold))
                                        Image(systemName: "xmark").font(.system(size: 9, weight: .bold))
                                    }
                                    .foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(Cumbre.terraFill, in: Capsule())
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                    AdminField(placeholder: "Buscar por @usuario o nombre para añadir…", text: $query)
                        .onChange(of: query) { _, q in
                            searchTask?.cancel()
                            // Sin la @: el servidor busca el texto tal cual y «@arroba» no está en ningún nombre.
                            let t = q.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "@", with: "")
                            guard t.count >= 2 else { results = []; return }
                            searchTask = Task {
                                try? await Task.sleep(nanoseconds: 250_000_000)
                                guard !Task.isCancelled else { return }
                                results = (try? await AppDependencies.shared.container.searchUsers.invoke(query: t, limit: 10)) ?? []
                            }
                        }
                    ForEach(results.filter { r in !recipients.contains { $0.uid == r.uid } }.prefix(6), id: \.uid) { u in
                        Button {
                            recipients.append(u); query = ""; results = []
                        } label: {
                            HStack(spacing: 10) {
                                AvatarCircle(url: u.photoUrl, size: 30, uid: u.uid)
                                Text(name(u)).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
                                Spacer()
                                Image(systemName: "plus.circle.fill").foregroundStyle(Cumbre.terra)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Cumbre.rule, lineWidth: 1))
                        }.buttonStyle(.plain)
                    }
                    Text(recipients.isEmpty ? "Sin nadie elegido, el aviso llega a TODOS." : "Solo recibirán el aviso estas \(recipients.count) personas.")
                        .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                }
                .adminCard()

                VStack(alignment: .leading, spacing: 8) {
                    AdminSectionLabel(text: "EL AVISO")
                    AdminField(placeholder: "Título", text: $title)
                    AdminField(placeholder: "Mensaje", text: $body_, axis: .vertical, lines: 3...8)
                }
                .adminCard()

                if !title.isEmpty || !body_.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        AdminSectionLabel(text: "ASÍ SE VERÁ")
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "mountain.2.fill").foregroundStyle(.white)
                                .frame(width: 34, height: 34).background(Cumbre.terraFill, in: RoundedRectangle(cornerRadius: 8))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(title.isEmpty ? "Título" : title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                                Text(body_.isEmpty ? "Mensaje" : body_).font(.system(size: 13)).foregroundStyle(Cumbre.ink2)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(12).background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
                    }
                }

                AdminButton(title: recipients.isEmpty ? "ENVIAR A TODOS LOS USUARIOS" : "ENVIAR A \(recipients.count)",
                            symbol: "paperplane.fill", busy: vm.pushBusy) {
                    if recipients.isEmpty { confirmAll = true }
                    else { vm.sendPushMany(targetUids: recipients.map { $0.uid }, title: title, body: body_) }
                }
                .disabled(!canSend).opacity(canSend ? 1 : 0.5)
                .alert("¿Enviar a TODOS?", isPresented: $confirmAll) {
                    Button("Cancelar", role: .cancel) {}
                    Button("Sí, a todos", role: .destructive) { vm.sendPush(targetUid: nil, title: title, body: body_) }
                } message: {
                    Text("El push llegará a todos los usuarios de Cumbre.")
                }
                if let r = vm.pushResult {
                    AdminChip(text: r.uppercased(), color: Cumbre.ok)
                }
            }
            .padding(16)
        }
    }

    private func name(_ u: PublicProfile) -> String { u.username.map { "@" + $0 } ?? (u.displayName ?? String(u.uid.prefix(10))) }
}


/// Fila de nota del panel admin (P6: pulsable → su escuela).
@ViewBuilder
func adminNoteRow(_ n: AdminNoteRow) -> some View {
    VStack(alignment: .leading, spacing: 2) {
        Text(n.text).font(.system(size: 14)).foregroundStyle(Cumbre.ink)
        Text([n.author, n.schoolId, (n.createdAt ?? "").isEmpty ? nil : String((n.createdAt ?? "").prefix(10))]
                .compactMap { $0 }.joined(separator: " - "))
            .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
        Text("Ver escuela ›").font(Cumbre.mono(9, .bold)).foregroundStyle(Cumbre.terra)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .contentShape(Rectangle())
    .padding(.vertical, 8)
}


/// R12: detalle de una nota del panel admin — se LEE entera y desde aquí
/// se salta a su escuela si hace falta.
struct AdminNoteDetailView: View {
    let note: AdminNoteRow
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("NOTA").font(Cumbre.mono(10, .bold)).tracking(1.5)
                    .foregroundStyle(Cumbre.terra)
                Text(note.text).font(.system(size: 15)).foregroundStyle(Cumbre.ink)
                Text([note.author, note.schoolId,
                      (note.createdAt ?? "").isEmpty ? nil : String((note.createdAt ?? "").prefix(10))]
                        .compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(Cumbre.ink3)
                if let sid = note.schoolId, !sid.isEmpty {
                    NavigationLink(destination: SchoolLoaderView(schoolId: sid)) {
                        Text("VER ESCUELA ▸").font(Cumbre.mono(10, .bold)).tracking(1)
                            .foregroundStyle(Cumbre.terra)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
        .background(Cumbre.bg.ignoresSafeArea())
        .navigationTitle("Nota")
        .navigationBarTitleDisplayMode(.inline)
    }
}
