import SwiftUI
import Shared
import CoreLocation
import UIKit
import PhotosUI
import FirebaseAuth

// Estrellas de una vía: media y votar.

struct LineStarsRow: View {
    let lineId: String
    let avgStars: Float?
    let myStars: Int
    let onRate: (Int) -> Void

    // Estilo Google Play: las estrellas muestran la MEDIA (amarillo) y son
    // tocables para votar; el toque se ve al instante y luego la media se
    // recalcula con el dato refrescado.
    @State private var pending: Int? = nil
    private let amber = Color(red: 0.96, green: 0.62, blue: 0.04)

    private var shown: Int { pending ?? Int((avgStars ?? 0).rounded()) }

    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                Button {
                    let newStars = myStars == i ? 0 : i   // re-tocar tu voto → quitarlo
                    pending = newStars > 0 ? newStars : nil
                    onRate(newStars)
                } label: {
                    Image(systemName: i <= shown ? "star.fill" : "star")
                        .font(.system(size: 13))
                        .foregroundStyle(i <= shown ? amber : Cumbre.ink3)
                }
                .buttonStyle(.plain)
            }
            if let avg = avgStars, avg > 0 {
                Text(String(format: "%.1f", avg))
                    .font(Cumbre.mono(11)).foregroundStyle(Cumbre.ink3)
                    .padding(.leading, 4)
            }
            if myStars > 0 {
                Text(L("· tu voto %@★", myStars))
                    .font(Cumbre.mono(11)).foregroundStyle(amber)
                    .padding(.leading, 4)
            }
        }
        .padding(.leading, 34)
        .onChange(of: avgStars) { _, _ in pending = nil }
        .onChange(of: myStars) { _, _ in pending = nil }
    }
}

/// Extrae la fecha (YYYY-MM-DD) de un timestamp ISO; si no encaja, devuelve tal cual.
