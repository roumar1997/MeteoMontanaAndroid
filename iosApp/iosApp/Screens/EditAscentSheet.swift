import SwiftUI

/// Estilo de un ascenso como UNA eleccion excluyente (Ninguno / A vista / Al flash).
/// En el servidor y en el diario viven como dos booleanos; este enum es el puente y
/// garantiza que nunca queden los dos encendidos. Espejo de AscentStyle.kt de Android.
enum AscentStyle: CaseIterable {
    case none, onsight, flash

    var aVista: Bool { self == .onsight }
    var alFlash: Bool { self == .flash }

    /// Si por datos viejos vinieran los dos a true, gana a vista (el mas exigente).
    init(aVista: Bool, alFlash: Bool) {
        if aVista {
            self = .onsight
        } else if alFlash {
            self = .flash
        } else {
            self = .none
        }
    }

    var label: String {
        switch self {
        case .none: return L("Ninguno")
        case .onsight: return L("A vista")
        case .flash: return L("Al flash")
        }
    }
}

/// «Editar ascenso» (menu «⋯» / deslizar de «Mis bloques» y «Mis vias»): fecha + estilo
/// en una hoja corta con un solo boton GUARDAR. Espejo de EditAscentSheet.kt de Android.
struct EditAscentSheet: View {
    let initialDate: String
    let initialStyle: AscentStyle
    let onSave: (_ date: String, _ style: AscentStyle) -> Void
    let onCancel: () -> Void

    @State private var pickedDate = Date()
    @State private var style: AscentStyle = .none

    private func eyebrow(_ text: String) -> some View {
        Text(text).font(Cumbre.mono(10, .bold)).tracking(1.8)
            .foregroundStyle(Cumbre.ink3)
            .padding(.top, 18).padding(.bottom, 7)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("EDITAR ASCENSO")).font(Cumbre.mono(11, .bold)).tracking(1.8)
                .foregroundStyle(Cumbre.terra)
                .padding(.top, 4)
            eyebrow(L("CUÁNDO"))
            HStack {
                Image(systemName: "calendar").foregroundStyle(Cumbre.terra)
                DatePicker("", selection: $pickedDate, in: ...Date(), displayedComponents: .date)
                    .labelsHidden()
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 11))
            eyebrow(L("ESTILO"))
            HStack(spacing: 3) {
                ForEach(AscentStyle.allCases, id: \.self) { option in
                    Button { style = option } label: {
                        Text(option.label).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                            .foregroundStyle(style == option ? Cumbre.ink : Cumbre.ink3)
                            .frame(maxWidth: .infinity).padding(.vertical, 9)
                            .background(style == option ? Cumbre.bg : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 9))
                            .overlay(RoundedRectangle(cornerRadius: 9)
                                .stroke(style == option ? Cumbre.rule : Color.clear, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(Cumbre.paper, in: RoundedRectangle(cornerRadius: 11))
            Button {
                let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"; df.locale = Locale(identifier: "en_US_POSIX")
                onSave(df.string(from: pickedDate), style)
            } label: {
                Text(L("GUARDAR")).font(Cumbre.mono(12, .bold)).tracking(1.8)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Cumbre.terra, in: RoundedRectangle(cornerRadius: 2))
            }
            .buttonStyle(.plain)
            .padding(.top, 22)
            Button(action: onCancel) {
                Text(L("Cancelar")).font(.system(size: 15))
                    .foregroundStyle(Cumbre.ink3)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.bottom, 8)
        .background(Cumbre.bg.ignoresSafeArea())
        .presentationDetents([.height(380)])
        .onAppear {
            let p = DateFormatter(); p.dateFormat = "yyyy-MM-dd"; p.locale = Locale(identifier: "en_US_POSIX")
            pickedDate = p.date(from: String(initialDate.prefix(10))) ?? Date()
            style = initialStyle
        }
    }
}

/// Guarda lo que cambio en «Editar ascenso» por los mismos casos de uso de siempre
/// (fecha y estilo, que ya pasan por la cola offline); solo llama a lo que cambio.
func saveAscentEdit(_ entry: JournalSession, date newDate: String, style: AscentStyle) async {
    let container = AppDependencies.shared.container
    if newDate != String(entry.date.prefix(10)) {
        _ = await reporting(L("No se pudo cambiar la fecha"), {
            try await container.updateJournalDate.invoke(id: entry.id, date: newDate)
        })
    }
    if style != AscentStyle(aVista: entry.aVista, alFlash: entry.alFlash) {
        _ = await reporting(L("No se pudo cambiar el estilo"), {
            try await container.updateJournalStyle.invoke(id: entry.id, aVista: style.aVista, alFlash: style.alFlash)
        })
    }
}
