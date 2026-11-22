import SwiftUI

// Piezas comunes de TODOS los chats (quedada, privado, escuela y rocódromo; maqueta 7A). Espejo de ChatStyle.kt,
// ChatTopBar.kt, ChatInput.kt y ChatGroupAvatar.kt de Android: papel blanco para la cabecera, el campo y las
// burbujas ajenas sobre el fondo hueso, para que se distingan del resto de la app.

enum ChatStyle {
    /// Blanco de las piezas del chat (en oscuro, el papel oscuro de la app).
    static let card = Cumbre.dyn(0xFFFFFF, 0x211F19)
    /// Lado del avatar junto a cada mensaje en los chats de grupo.
    static let avatarSize: CGFloat = 28

    /// Burbujas con cola: las ajenas la llevan abajo a la izquierda, las mías abajo a la derecha.
    static func bubbleShape(mine: Bool) -> UnevenRoundedRectangle {
        mine
            ? UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 6, topTrailingRadius: 18)
            : UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 6, bottomTrailingRadius: 18, topTrailingRadius: 18)
    }
}

extension View {
    /// Burbuja con cola: las mías terracota; las ajenas blancas con borde fino y una sombra suave.
    func chatBubbleSurface(mine: Bool) -> some View {
        let shape = ChatStyle.bubbleShape(mine: mine)
        return self
            .background(mine ? Cumbre.terraFill : ChatStyle.card, in: shape)
            .overlay(shape.stroke(mine ? Color.clear : Cumbre.rule, lineWidth: 1))
            .shadow(color: .black.opacity(mine ? 0.12 : 0.06), radius: mine ? 2 : 1, y: 1)
    }
}

/// Posición de un mensaje dentro de su racha (mensajes seguidos del mismo emisor, sin cruzar de día).
struct ChatRunPos {
    let first: Bool
    let last: Bool
}

/// [uids] en orden cronológico. El nombre va en el primero de la racha y el avatar en el último; [startsDay] corta la racha.
func chatRunPositions(_ uids: [String], startsDay: (Int) -> Bool = { _ in false }) -> [ChatRunPos] {
    uids.indices.map { i in
        ChatRunPos(
            first: i == 0 || uids[i - 1] != uids[i] || startsDay(i),
            last: i == uids.count - 1 || uids[i + 1] != uids[i] || startsDay(i + 1)
        )
    }
}

/// Como `chatDayStarts` pero para cualquier mensaje (id + hora): id del primer mensaje de cada día -> su hora.
func chatDayStartIds(_ messages: [(String, Int64?)]) -> [String: Int64] {
    var out: [String: Int64] = [:]
    var lastDay: DateComponents?
    for (id, ms) in messages {
        guard let ms, ms > 0 else { continue }
        let day = Calendar.current.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(ms) / 1000))
        if day != lastDay { out[id] = ms; lastDay = day }
    }
    return out
}

/// Cabecera ÚNICA de todos los chats: volver en círculo, avatar, título serif + subtítulo y acciones a la derecha.
struct ChatTopBar<Avatar: View, Subtitle: View, Actions: View>: View {
    let title: String
    let onBack: () -> Void
    var onTitleClick: (() -> Void)?
    @ViewBuilder let avatar: () -> Avatar
    @ViewBuilder let subtitle: () -> Subtitle
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold)).foregroundStyle(Cumbre.ink)
                    .frame(width: 40, height: 40).background(Cumbre.bg, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("common_back"))
            Spacer().frame(width: 10)
            Button { onTitleClick?() } label: {
                HStack(spacing: 0) {
                    avatar()
                    VStack(alignment: .leading, spacing: 1) {
                        Text(title).font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink).lineLimit(1)
                        subtitle()
                    }
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(onTitleClick == nil)
            actions()
        }
        .padding(.leading, 8).padding(.trailing, 4).padding(.vertical, 10)
        .background(ChatStyle.card)
        .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
        .zIndex(1)
    }
}

/// Subtítulo de una línea de `ChatTopBar`.
struct ChatSubtitle: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
    }
}

/// Avatar de letra (rocódromo, escuela): círculo de color con la inicial.
struct ChatInitialAvatar: View {
    let name: String
    let color: Color
    var body: some View {
        Circle().fill(color).frame(width: 44, height: 44)
            .overlay(Text(String(name.prefix(1)).uppercased()).font(Cumbre.serif(18, .bold)).foregroundStyle(.white))
    }
}

/// Acción redonda de la cabecera (campana, info...).
struct ChatHeaderAction: View {
    let symbol: String
    let a11y: String
    var tint: Color = Cumbre.ink
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 16, weight: .semibold)).foregroundStyle(tint)
                .frame(width: 40, height: 40).background(Cumbre.bg, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a11y)
    }
}

/// Campo de escribir de TODOS los chats: píldora con borde (terracota al escribir) y enviar redondo terracota.
struct ChatInputPill: View {
    @Binding var text: String
    var enabled = true
    var placeholder: String = L("chat_write")
    var focused: FocusState<Bool>.Binding?
    let onSend: () -> Void

    private var canSend: Bool { enabled && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "pencil").font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.terraFill)
                field
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 26))
            .overlay(RoundedRectangle(cornerRadius: 26)
                .stroke(text.trimmingCharacters(in: .whitespaces).isEmpty ? Cumbre.rule : Cumbre.terraFill, lineWidth: 1.5))
            Button(action: onSend) {
                Image(systemName: "paperplane.fill").font(.system(size: 19)).foregroundStyle(.white)
                    .frame(width: 50, height: 50)
                    .background(Cumbre.terraFill.opacity(canSend ? 1 : 0.4), in: Circle())
                    .shadow(color: .black.opacity(canSend ? 0.18 : 0), radius: 4, y: 2)
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel(L("common_send"))
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(ChatStyle.card)
    }

    @ViewBuilder private var field: some View {
        let tf = TextField(placeholder, text: $text, axis: .vertical).lineLimit(1...4).font(.system(size: 15))
        if let focused { tf.focused(focused) } else { tf }
    }
}

/// Sustituye al campo cuando no estás «Aquí»: candado, «Solo escriben los que están aquí» y el botón para marcarlo.
struct ChatLockedBar<Here: View>: View {
    @ViewBuilder let onHere: () -> Here
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock").font(.system(size: 16)).foregroundStyle(Cumbre.ink2)
            VStack(alignment: .leading, spacing: 1) {
                Text(L("chat_locked_title")).font(.system(size: 14, weight: .bold)).foregroundStyle(Cumbre.ink)
                Text(L("chat_locked_sub")).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            onHere()
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(ChatStyle.card, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 12).padding(.vertical, 10)
    }
}

/// Chat vacío: icono en círculo, título y una frase para animar a escribir el primero.
struct ChatEmptyState: View {
    var title: String = L("chat_empty_title")
    var subtitle: String = L("chat_empty_sub")
    var body: some View {
        VStack(spacing: 0) {
            Circle().fill(Cumbre.terraFill.opacity(0.12)).frame(width: 84, height: 84)
                .overlay(Image(systemName: "bubble.left").font(.system(size: 34)).foregroundStyle(Cumbre.terraFill))
            Text(title).font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                .multilineTextAlignment(.center).padding(.top, 16)
            Text(subtitle).font(.system(size: 14)).foregroundStyle(Cumbre.ink2)
                .multilineTextAlignment(.center).padding(.top, 6)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(false)
    }
}

/// Hueco del avatar: con [show] pinta la foto (con su aureola) y abre el perfil al tocarla; si no, solo reserva el sitio.
struct ChatSenderAvatarSlot: View {
    let show: Bool
    let photoUrl: String?
    let uid: String
    let onTap: () -> Void
    var body: some View {
        Group {
            if show {
                Button(action: onTap) { AvatarCircle(url: photoUrl, size: ChatStyle.avatarSize, uid: uid) }.buttonStyle(.plain)
            } else {
                Color.clear
            }
        }
        .frame(width: ChatStyle.avatarSize, height: ChatStyle.avatarSize)
    }
}

/// Burbuja de un chat abierto (escuela o rocódromo): nombre arriba en el primero de la racha, avatar en el último,
/// hora y «· aquí» dentro. Tocar un mensaje ajeno abre el chat 1 a 1.
struct OpenChatBubble: View {
    let text: String
    let millis: Int64?
    let mine: Bool
    let name: String
    let present: Bool
    let run: ChatRunPos
    let photoUrl: String?
    let uid: String
    let onOpenProfile: () -> Void
    let onTap: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            if mine { Spacer(minLength: 48) } else {
                ChatSenderAvatarSlot(show: run.last, photoUrl: photoUrl, uid: uid, onTap: onOpenProfile)
            }
            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                if !mine && run.first {
                    Text(name).font(.system(size: 11, weight: .bold)).foregroundStyle(Cumbre.terra).padding(.horizontal, 4)
                }
                VStack(alignment: .trailing, spacing: 3) {
                    Text(text).font(.system(size: 15)).foregroundStyle(mine ? .white : Cumbre.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(clock).font(.system(size: 10)).foregroundStyle(mine ? Color.white.opacity(0.8) : Cumbre.ink3)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .chatBubbleSurface(mine: mine)
                .frame(maxWidth: 280, alignment: mine ? .trailing : .leading)
                .onTapGesture { if !mine { onTap() } }
            }
            if !mine { Spacer(minLength: 48) }
        }
    }

    private var clock: String {
        let base = millis.map { chatClock($0) } ?? ""
        return present && !mine ? base + " · " + L("chat_here_tag") : base
    }
}
