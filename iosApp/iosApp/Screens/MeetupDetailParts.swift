import SwiftUI
import Shared

// Piezas de la ficha de quedada: compartir, filas de persona, editar descripción, material y sus cálculos.

/// Abre Google Maps (o Apple Maps) con indicaciones a las coordenadas de la escuela.
func openDirections(lat: Double, lon: Double) {
    if let url = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)") {
        UIApplication.shared.open(url)
    }
}

/// Texto de compartir/invitar. Con enlace de invitación (miembros): permite
/// unirse aunque no haya relación de follows (espejo de Android).
func meetupShareText(_ meetup: Meetup, inviteLink: String?) -> String {
    let days = meetup.days.map { detailFormatDayMonth($0) }.joined(separator: ", ")
    var text = L("🧗 Te invito a la quedada *%@*", meetup.name)
    if let s = meetup.placeName ?? meetup.schoolName { text += " en \(s)" }
    if !days.isEmpty { text += " (\(days))" }
    text += "\n"
    if let link = inviteLink, !link.isEmpty {
        text += L("👉 Únete desde aquí:\n%@", link)
    } else {
        text += L("👉 Búscala en Cumbre (pestaña Quedadas)")
    }
    return text
}

func detailFormatDayMonth(_ iso: String) -> String {
    let weekdays = ["dom","lun","mar","mie","jue","vie","sab"]
    let months = ["ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"]
    let parts = iso.split(separator: "-")
    guard parts.count == 3,
          let y = Int(parts[0]), let mo = Int(parts[1]), let d = Int(parts[2]) else { return iso }
    var comps = DateComponents()
    comps.year = y; comps.month = mo; comps.day = d
    if let date = Calendar.current.date(from: comps) {
        let wd = Calendar.current.component(.weekday, from: date)
        return "\(weekdays[wd - 1]) \(d) \(months[mo - 1])"
    }
    return "\(d) \(months[mo - 1])"
}

func detailScoreColor(_ score: Int) -> Color {
    if score >= 80 { return Color(red: 0.13, green: 0.77, blue: 0.37) }
    if score >= 60 { return Color(red: 0.96, green: 0.62, blue: 0.04) }
    if score >= 40 { return Color(red: 0.93, green: 0.27, blue: 0.27) }
    return Color(red: 0.42, green: 0.44, blue: 0.50)
}

// ── Sub-views ────────────────────────────────────────────────────────────────

struct DetailInfoRow<Content: View>: View {
    let icon: String
    @ViewBuilder let content: Content
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(Cumbre.terra)
                .frame(width: 18)
            content
        }
    }
}

struct EditDescriptionSheet: View {
    @Binding var text: String
    let saving: Bool
    let onSave: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NavigationView {
            VStack {
                TextEditor(text: $text)
                    .frame(minHeight: 140)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Cumbre.ink.opacity(0.2), lineWidth: 1))
                    .padding()
                Spacer()
            }
            .navigationTitle("Detalles de la quedada")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onCancel() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() }
                    else { Button("Guardar") { onSave() } }
                }
            }
        }
    }
}

struct MeetupMemberRow: View {
    let member: MeetupMember
    let canKick: Bool
    let onKick: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            MeetupAvatarCircle(url: member.photoUrl, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(member.displayName ?? member.username ?? member.uid)
                    .font(.subheadline).fontWeight(.medium)
                if let u = member.username {
                    Text("@\(u)").font(.caption).foregroundColor(Cumbre.ink.opacity(0.5))
                }
            }
            Spacer()
            if canKick {
                Button(action: onKick) {
                    Image(systemName: "person.badge.minus").foregroundColor(.red)
                }
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}

struct MeetupAvatarCircle: View {
    let url: String?
    let size: CGFloat
    var body: some View {
        ZStack {
            Circle().fill(Cumbre.ink.opacity(0.08))
            if let u = url, let imgUrl = URL(string: u) {
                CachedImage(url: imgUrl) { img in img.resizable().scaledToFill() }
                    placeholder: { Color.clear }
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.fill").foregroundColor(Cumbre.ink.opacity(0.4))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

// MARK: - Gear helpers

func parseGear(_ json: String?) -> [String: Int] {
    guard let json = json?.trimmingCharacters(in: .whitespaces),
          !json.isEmpty, json != "{}" else { return [:] }
    var result: [String: Int] = [:]
    let inner = json.trimmingCharacters(in: CharacterSet(charactersIn: "{}"))
    for pair in inner.split(separator: ",") {
        let parts = pair.split(separator: ":")
        guard parts.count == 2 else { continue }
        let key = parts[0].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        if let val_ = Int(parts[1].trimmingCharacters(in: .whitespaces)), val_ > 0 {
            result[key] = val_
        }
    }
    return result
}

func buildGearJson(_ gear: [String: Int]) -> String {
    let entries = gear.filter { $0.value > 0 }
    if entries.isEmpty { return "{}" }
    let pairs = entries.map { "\"\($0.key)\":\($0.value)" }
    return "{\(pairs.joined(separator: ","))}"
}

func gearItemsForDiscipline(_ discipline: String?) -> [(key: String, label: String)] {
    switch discipline {
    case "BOULDER": return [("crashpads", L("Crashpads"))]
    case "ROUTE": return [("cintas", L("Cintas")), ("cuerda", L("Cuerdas")), ("grigri", "Gri-gri")]
    default: return [("crashpads", L("Crashpads")), ("cintas", L("Cintas")), ("cuerda", L("Cuerdas")), ("grigri", "Gri-gri")]
    }
}

func gearLabel(_ key: String) -> String {
    switch key {
    case "crashpads": return "crashpads"
    case "cintas": return "cintas"
    case "cuerda": return "cuerdas"
    case "grigri": return "gri-gri"
    default: return key
    }
}

/// Cuerda y grigri son "sí/no" por persona; cintas y crashpads son cantidad.
/// Al sumar entre miembros, cada "sí" cuenta 1 -> "5 cuerdas" si 5 dijeron que sí.
func isBooleanGearKey(_ key: String) -> Bool { key == "cuerda" || key == "grigri" }

func totalGear(_ members: [MeetupMember]) -> [String: Int] {
    var totals: [String: Int] = [:]
    for m in members {
        for (k, v) in parseGear(m.gearJson) {
            totals[k, default: 0] += v
        }
    }
    return totals.filter { $0.value > 0 }
}

func totalGearSummary(_ members: [MeetupMember]) -> String {
    let totals = totalGear(members)
    if totals.isEmpty { return "" }
    return totals.map { "\($0.value) \(gearLabel($0.key))" }.joined(separator: " \u{00B7} ")
}

// MARK: - Gear section view

struct GearSectionView: View {
    let meetup: Meetup
    let myUid: String?
    let isCreator: Bool
    let onEditGear: () -> Void

    var body: some View {
        let totals = totalGear(Array(meetup.members))

        VStack(alignment: .leading, spacing: 10) {
            // Eyebrow
            HStack(spacing: 6) {
                Image(systemName: "bag").font(.system(size: 13))
                    .foregroundColor(Cumbre.ink.opacity(0.6))
                Text(NSLocalizedString("meetup_detail_material", comment: ""))
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .tracking(1.8)
                    .foregroundColor(Cumbre.ink.opacity(0.6))
            }

            // Total pills
            if !totals.isEmpty {
                FlowLayoutView {
                    ForEach(Array(totals.keys.sorted()), id: \.self) { key in
                        HStack(spacing: 4) {
                            Text("\(totals[key] ?? 0)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Cumbre.terra)
                            Text(gearLabel(key))
                                .font(.system(size: 12))
                                .foregroundColor(Cumbre.ink.opacity(0.6))
                        }
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Cumbre.ink.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                    }
                }
            } else {
                Text("Nadie ha indicado material todavia. Pulsa abajo para añadir el tuyo.")
                    .font(.caption).italic().foregroundColor(Cumbre.ink.opacity(0.5))
            }

            // Per-member breakdown
            ForEach(meetup.members, id: \.uid) { member in
                let gear = parseGear(member.gearJson)
                let summary = gear.isEmpty ? "" : gear.map { "\($0.value) \(gearLabel($0.key))" }.joined(separator: " \u{00B7} ")
                HStack(spacing: 8) {
                    MeetupAvatarCircle(url: member.photoUrl, size: 28)
                    Text(member.displayName ?? member.username ?? String(member.uid.prefix(6)))
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    if !summary.isEmpty {
                        Text(summary)
                            .font(.system(size: 12))
                            .foregroundColor(Cumbre.ink.opacity(0.6))
                    } else {
                        Text(NSLocalizedString("meetup_detail_no_gear", comment: ""))
                            .font(.system(size: 12)).italic()
                            .foregroundColor(Cumbre.ink.opacity(0.35))
                    }
                }
            }

            // Edit button
            if meetup.joined || isCreator {
                Button(action: onEditGear) {
                    HStack(spacing: 6) {
                        Image(systemName: "pencil").font(.caption2)
                        Text(NSLocalizedString("meetup_detail_edit_gear", comment: "")).font(.system(size: 13, weight: .medium))
                    }
                    .foregroundColor(Cumbre.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.ink.opacity(0.3), lineWidth: 1))
                }
            }
        }
        .padding(16)
    }
}

// MARK: - Edit gear sheet

/// Hoja «¿Qué llevas tú?» (maqueta 5): una tarjeta por material con interruptor (o contador) y cuántos del grupo lo
/// llevan. Solo el material que tiene sentido para el lugar (shared: `MeetupGearItem.forPlace`). Espejo de `EditGearDialog`.
struct EditGearSheet: View {
    let discipline: String?
    let currentGearJson: String?
    var gym = false
    var placeName: String?
    var members: [MeetupMember] = []
    var myUid: String?
    let onSave: (String) -> Void
    let onCancel: () -> Void

    @State private var mine: [String: Int] = [:]

    private var keys: [String] {
        MeetupGearItem.shared.forPlace(discipline: discipline, gym: gym).map { item in
            switch item {
            case "ROPE": return "cuerda"
            case "GRIGRI": return "grigri"
            case "QUICKDRAWS": return "cintas"
            default: return "crashpads"
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    if let placeName { Text(placeName.uppercased()).eyebrow(Cumbre.terra).lineLimit(1) }
                    Text(L("meetup_gear_sheet_title")).font(Cumbre.serif(24, .bold)).foregroundStyle(Cumbre.ink)
                }
                Spacer()
                Button(action: onCancel) {
                    Image(systemName: "xmark").font(.system(size: 17)).foregroundStyle(Cumbre.ink).frame(width: 40, height: 40)
                }
                .buttonStyle(.plain).accessibilityLabel(L("common_close"))
            }
            ScrollView {
                VStack(spacing: 10) {
                    if keys.isEmpty {
                        Text(L("meetup_gear_sheet_none")).font(.system(size: 14)).foregroundStyle(Cumbre.ink2).padding(.vertical, 16)
                    }
                    ForEach(keys, id: \.self) { tile($0) }
                }
                .padding(.top, 16)
            }
            if !keys.isEmpty {
                Button { onSave(buildGearJson(mine)) } label: {
                    Text(L("common_save").uppercased()).font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 15).background(Cumbre.terraFill, in: Capsule())
                }
                .buttonStyle(.plain).padding(.top, 16)
            }
        }
        .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 16)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .onAppear {
            let current = parseGear(currentGearJson)
            mine = Dictionary(uniqueKeysWithValues: keys.map { ($0, current[$0] ?? 0) })
        }
    }

    private func icon(_ key: String) -> String {
        switch key {
        case "cuerda": return "link"
        case "grigri": return "shield"
        case "cintas": return "ruler"
        default: return "rectangle.stack"
        }
    }

    private func label(_ key: String) -> String {
        switch key {
        case "cuerda": return L("gear_rope")
        case "grigri": return L("gear_grigri")
        case "cintas": return L("gear_quickdraws")
        default: return L("gear_crashpad")
        }
    }

    private func tile(_ key: String) -> some View {
        let value = mine[key] ?? 0
        let on = value > 0
        let others = members.filter { $0.uid != myUid }
        let carriers = others.filter { (parseGear($0.gearJson)[key] ?? 0) > 0 }.count + (on ? 1 : 0)
        let groupSize = max(1, members.count)
        let sub = carriers == 0 ? L("meetup_gear_sheet_nobody")
            : (carriers == 1 && on ? L("meetup_gear_sheet_only_you") : L("meetup_gear_sheet_group_of", carriers, groupSize))
        return HStack(spacing: 12) {
            Image(systemName: icon(key)).font(.system(size: 22)).foregroundStyle(on ? Cumbre.terraFill : Cumbre.ink2).frame(width: 28)
            VStack(alignment: .leading, spacing: 1) {
                Text(label(key)).font(.system(size: 17, weight: .bold)).foregroundStyle(Cumbre.ink)
                Text(sub).font(.system(size: 12)).foregroundStyle(carriers == 0 ? Cumbre.terraFill : Cumbre.ink2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if isBooleanGearKey(key) {
                Toggle("", isOn: Binding(get: { on }, set: { mine[key] = $0 ? 1 : 0 })).labelsHidden().tint(Cumbre.terraFill)
            } else {
                HStack(spacing: 6) {
                    StepperButton(symbol: "−", action: value > 0 ? { mine[key] = value - 1 } : nil, primary: false, size: 36)
                    Text("\(value)").font(Cumbre.serif(20, .bold)).foregroundStyle(Cumbre.ink)
                    StepperButton(symbol: "+", action: { mine[key] = min(Int(MeetupGearItem.shared.MAX_QTY), value + 1) }, primary: true, size: 36)
                }
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(on ? Cumbre.terraBg : Cumbre.paper, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(on ? Cumbre.terraFill : Cumbre.rule, lineWidth: on ? 2 : 1))
        .contentShape(RoundedRectangle(cornerRadius: 18))
        .onTapGesture {
            if isBooleanGearKey(key) { mine[key] = on ? 0 : 1 } else if !on { mine[key] = 1 }
        }
    }
}
