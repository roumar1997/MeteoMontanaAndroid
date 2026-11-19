import SwiftUI
import Shared

/// Fila de la lista de rocódromos (maqueta): icono del tipo, nombre en serif, cadena · dirección, etiquetas y la
/// distancia a la derecha. Todo puede crecer con la letra grande: nada tiene alto fijo. Espejo de `GymListRow.kt`.
struct GymListRow: View {
    let item: GymListItem
    let isFavorite: Bool
    let onOpen: () -> Void
    let onToggleFavorite: () -> Void

    var body: some View {
        let gym = item.gym
        HStack(alignment: .top, spacing: 12) {
            // La fila entera abre la ficha; la estrella de la derecha es un botón aparte.
            HStack(alignment: .top, spacing: 12) {
                GymKindBadge(kind: gym.kind)
                VStack(alignment: .leading, spacing: 3) {
                    Text(gym.name).font(Cumbre.serif(18, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    if let subtitle = GymPresentation.subtitle(gym) {
                        Text(subtitle).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).lineLimit(2)
                    }
                    GymTags(gym: gym, status: gym.openStatusNow(), showKind: false).padding(.top, 3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let distance = GymPresentation.distanceText(item.distanceKm?.doubleValue) {
                    Text(distance).font(.system(size: 13)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            GymFavoriteStar(isFavorite: isFavorite, action: onToggleFavorite)
        }
        .padding(.leading, 18).padding(.trailing, 8).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
    }
}

/// Tarjeta de cristal del marcador pulsado (misma que la de sector/parking de la escuela: radio 24, fondo del tema,
/// canto de luz blanco). Dos acciones: «Cómo llegar» (primaria) y «Ver ficha». Espejo de `GymMapCard.kt`.
struct GymMapCard: View {
    let item: GymListItem
    let isFavorite: Bool
    let onToggleFavorite: () -> Void
    let onClose: () -> Void
    let onOpen: () -> Void

    var body: some View {
        let gym = item.gym
        VStack(spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                GymKindBadge(kind: gym.kind, size: 52)
                VStack(alignment: .leading, spacing: 5) {
                    Text(gym.name).font(Cumbre.serif(21, .bold)).foregroundStyle(Cumbre.ink).lineLimit(2)
                    GymTags(gym: gym, status: gym.openStatusNow())
                    if let distance = GymPresentation.distanceText(item.distanceKm?.doubleValue) {
                        Text(distance).font(.system(size: 12)).foregroundStyle(Cumbre.ink2).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                GymFavoriteStar(isFavorite: isFavorite, action: onToggleFavorite)
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(Cumbre.ink2)
                        .frame(width: 32, height: 32).background(Cumbre.ink.opacity(0.07), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("common_close"))
            }
            HStack(spacing: 8) {
                GymActionPill(text: L("gym_directions"), icon: "gym_ic_directions", primary: true) {
                    openDirections(lat: gym.lat, lon: gym.lon)
                }
                GymActionPill(text: L("gym_view_card"), action: onOpen)
            }
        }
        .padding(14)
        .background(Cumbre.bg.opacity(0.55), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 1))
        .shadow(color: .black.opacity(0.22), radius: 12, y: 6)
    }
}
