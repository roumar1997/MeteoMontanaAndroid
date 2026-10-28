import SwiftUI
import Shared

/// Tarjetas de las respuestas nuevas del asistente (octubre de 2026): restricciones, plan de viaje y "¿qué piedra
/// es esta?". Mismo estilo que las de sectores: papel, esquinas de 14 y borde fino.
private struct AssistantCardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Cumbre.rule, lineWidth: 1))
    }
}

private extension View {
    func assistantCard() -> some View { modifier(AssistantCardStyle()) }
}

// MARK: - Restricciones

struct AssistantRestrictionsView: View {
    let restrictions: AssistantRestrictions
    @EnvironmentObject private var vm: AssistantViewModel

    var body: some View {
        if !restrictions.items.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(restrictions.items.enumerated()), id: \.offset) { index, item in
                    if index > 0 { Divider().overlay(Cumbre.rule) }
                    row(item)
                }
                Text(L("Datos que mantiene Cumbre a partir de fuentes oficiales. Mira siempre los carteles de la zona."))
                    .font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .background(Cumbre.bg.opacity(0.6))
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .assistantCard()
        }
    }

    private func row(_ item: AssistantRestriction) -> some View {
        Button { vm.requestOpen(schoolId: item.schoolId) } label: {
            HStack(alignment: .top, spacing: 10) {
                Text(AssistantPresenter.restrictionKind(item.kind).uppercased())
                    .font(Cumbre.mono(10, .bold)).tracking(0.8)
                    .foregroundStyle(item.active ? .white : color(item.kind))
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(item.active ? color(item.kind) : color(item.kind).opacity(0.12), in: Capsule())
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(item.schoolName) · \(AssistantPresenter.restrictionPlace(item))")
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                        .multilineTextAlignment(.leading)
                    Text(AssistantPresenter.restrictionWhen(item))
                        .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                    if let reason = item.reason, !reason.isEmpty {
                        Text(reason).font(.system(size: 12)).foregroundStyle(Cumbre.ink2)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if let source = item.sourceName, !source.isEmpty {
                        Text(L("Fuente: %@", source)).font(.system(size: 11)).foregroundStyle(Cumbre.ink3)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Cumbre.ink3)
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func color(_ kind: String) -> Color {
        switch kind {
        case "CLOSED": return Cumbre.bad
        case "REGULATED": return Cumbre.warn
        default: return Cumbre.ink2
        }
    }
}

// MARK: - Plan de viaje

struct AssistantTripView: View {
    let plan: [AssistantPresenter.TripDay]
    @EnvironmentObject private var vm: AssistantViewModel

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(plan.enumerated()), id: \.element.id) { index, day in
                if index > 0 { Divider().overlay(Cumbre.rule) }
                Button { vm.requestOpen(schoolId: day.schoolId) } label: {
                    HStack(spacing: 12) {
                        Text(AssistantPresenter.dayLabel(day.date))
                            .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                            .frame(width: 74, alignment: .leading)
                        Text(day.schoolName)
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Cumbre.ink)
                            .lineLimit(2)
                        Spacer(minLength: 4)
                        if day.rainy {
                            Image(systemName: "cloud.rain.fill").font(.system(size: 12)).foregroundStyle(Cumbre.rain)
                        }
                        Text("\(day.score)")
                            .font(Cumbre.mono(13, .bold)).foregroundStyle(.white)
                            .frame(width: 34, height: 26)
                            .background(Cumbre.score(day.score), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .assistantCard()
    }
}

// MARK: - ¿Qué piedra es esta?

struct AssistantPhotoMatchesView: View {
    let candidates: [AssistantPhotoMatcher.Candidate]
    @EnvironmentObject private var vm: AssistantViewModel

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(candidates.enumerated()), id: \.element.id) { index, c in
                Button { vm.requestOpen(schoolId: c.schoolId, viaId: c.lineId) } label: {
                    HStack(spacing: 12) {
                        thumbnail(c.photoUrl)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(c.blockName)
                                .font(.system(size: 15, weight: .semibold)).foregroundStyle(Cumbre.ink)
                            Text(detail(c, first: index == 0))
                                .font(Cumbre.mono(11, .bold)).foregroundStyle(Cumbre.ink2)
                        }
                        Spacer(minLength: 0)
                        Text(L("ABRIR"))
                            .font(Cumbre.mono(10, .bold)).tracking(1)
                            .foregroundStyle(Cumbre.terra)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Cumbre.terraBg, in: Capsule())
                    }
                    .padding(10)
                    .assistantCard()
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func detail(_ c: AssistantPhotoMatcher.Candidate, first: Bool) -> String {
        AssistantPresenter.photoDetail(c)
    }

    @ViewBuilder private func thumbnail(_ url: String?) -> some View {
        if let url {
            MiniTopoThumbnail(photoUrl: url, points: [], grade: nil)
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            Image(systemName: "photo")
                .font(.system(size: 18)).foregroundStyle(Cumbre.ink3)
                .frame(width: 56, height: 56)
                .background(Cumbre.bg, in: RoundedRectangle(cornerRadius: 10))
        }
    }
}
