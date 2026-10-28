import SwiftUI

// Esqueletos de carga (octubre de 2026). Regla de la app:
//  - CONTENIDO que carga (listas, feed, diario, fichas) → esqueleto con brillo con la forma de lo que viene.
//  - ACCIONES (enviar, guardar, unirse…) → spinner pequeño dentro del botón, como siempre.
// El esqueleto no sale si los datos llegan en menos de 0,2 s (`SkeletonDelay`), y el contenido entra con un fundido y
// subiendo unos píxeles (`loadedEntrance`) en vez de aparecer de golpe. Espejo de ui/components/Skeleton.kt.

/// Brillo que cruza el esqueleto de izquierda a derecha. Se aplica al GRUPO entero (no a cada hueso) para que la
/// franja de luz recorra la pantalla de una vez. Con "Reducir movimiento" se queda quieto.
struct Shimmer: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var scheme
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay {
                if !reduceMotion {
                    GeometryReader { g in
                        let band = max(g.size.width * 0.45, 120)
                        LinearGradient(colors: [.clear, Color.white.opacity(scheme == .dark ? 0.07 : 0.55), .clear],
                                       startPoint: .leading, endPoint: .trailing)
                            .frame(width: band)
                            .offset(x: -band + phase * (g.size.width + band))
                    }
                    .mask(content)
                    .allowsHitTesting(false)
                }
            }
            .onAppear {
                withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) { phase = 1 }
            }
    }
}

extension View {
    func shimmer() -> some View { modifier(Shimmer()) }

    /// Entrada del contenido cuando sustituye al esqueleto: fundido corto + sube 6 pt.
    func loadedEntrance() -> some View {
        transition(.asymmetric(insertion: .opacity.combined(with: .offset(y: 6)), removal: .opacity))
    }

    /// Anima el cambio esqueleto → contenido. `value` es lo que cambia al terminar de cargar.
    func animatesLoading<V: Equatable>(_ value: V) -> some View {
        animation(.easeOut(duration: 0.28), value: value)
    }
}

/// Un "hueso": rectángulo, pastilla o círculo del color del esqueleto.
struct Bone: View {
    enum Shape { case rect, pill, circle }
    var width: CGFloat? = nil
    var height: CGFloat
    var shape: Shape = .rect

    static let tone = Cumbre.ink3.opacity(0.14)

    var body: some View {
        Group {
            switch shape {
            case .rect: RoundedRectangle(cornerRadius: 3).fill(Self.tone)
            case .pill: Capsule().fill(Self.tone)
            case .circle: Circle().fill(Self.tone)
            }
        }
        .frame(width: shape == .circle ? height : width, height: height)
        .frame(maxWidth: width == nil && shape != .circle ? .infinity : nil, alignment: .leading)
    }
}

/// No enseña nada durante 0,2 s: si los datos llegan antes, no hay parpadeo de esqueleto.
struct SkeletonDelay<Content: View>: View {
    @ViewBuilder var content: Content
    @State private var visible = false

    var body: some View {
        content
            .opacity(visible ? 1 : 0)
            .task {
                try? await Task.sleep(nanoseconds: 200_000_000)
                withAnimation(.easeOut(duration: 0.15)) { visible = true }
            }
    }
}

/// Hueco de una foto mientras llega: tono cálido de piedra con brillo. (Cuando el servidor dé el color medio de cada
/// foto, se pasará aquí para que el hueco ya tenga su color.) La foto entra encima con un fundido (`.transition(.opacity)`).
struct PhotoPlaceholder: View {
    var body: some View {
        Rectangle().fill(Color(red: 0.60, green: 0.53, blue: 0.46)).shimmer()
    }
}

// MARK: - Formas de cada pantalla

/// Lista de esqueletos con brillo, con su retardo. `count` filas de la forma `row`.
struct SkeletonList<Row: View>: View {
    var count: Int = 6
    var dividers: Bool = true
    @ViewBuilder var row: () -> Row

    var body: some View {
        SkeletonDelay {
            VStack(spacing: 0) {
                ForEach(0..<count, id: \.self) { _ in
                    row()
                    if dividers { Divider().overlay(Cumbre.rule) }
                }
            }
            .shimmer()
            .frame(maxWidth: .infinity, alignment: .top)
        }
        .accessibilityLabel(L("Cargando…"))
    }
}

/// Fila de persona/aviso/chat/comentario: avatar redondo + dos líneas.
struct SkeletonPersonRow: View {
    var avatar: CGFloat = 44
    var trailing: Bool = false
    var body: some View {
        HStack(spacing: 12) {
            Bone(height: avatar, shape: .circle)
            VStack(alignment: .leading, spacing: 7) {
                Bone(width: 150, height: 14)
                Bone(width: 220, height: 11)
            }
            Spacer(minLength: 0)
            if trailing { Bone(width: 64, height: 28, shape: .pill) }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Fila de vía/bloque del diario, proyectos o la pestaña BLOQUES: pastilla del grado + nombre + chips + lugar.
struct SkeletonGradeRow: View {
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Bone(width: 52, height: 38)
            VStack(alignment: .leading, spacing: 8) {
                Bone(width: 160, height: 17)
                HStack(spacing: 6) { Bone(width: 62, height: 18, shape: .pill); Bone(width: 70, height: 18, shape: .pill) }
                Bone(width: 200, height: 11)
            }
            Spacer(minLength: 0)
            Bone(width: 70, height: 11)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
    }
}

/// Tarjeta del feed: cabecera (avatar, nombre, etiqueta, hora) + hueco de la foto.
struct SkeletonFeedCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Bone(height: 40, shape: .circle)
                VStack(alignment: .leading, spacing: 6) { Bone(width: 110, height: 14); Bone(width: 80, height: 10) }
                Spacer(minLength: 0)
                Bone(width: 72, height: 10)
            }
            .padding(12)
            Bone(height: 300).clipShape(Rectangle())
            HStack(spacing: 18) { Bone(width: 22, height: 22); Bone(width: 22, height: 22); Bone(width: 22, height: 22) }
                .padding(12)
        }
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

/// Tarjeta de quedada / propuesta: título, dos líneas y una fila de avatares.
struct SkeletonCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Bone(width: 180, height: 17)
            Bone(width: 240, height: 11)
            Bone(width: 140, height: 11)
            HStack(spacing: -6) { ForEach(0..<3, id: \.self) { _ in Bone(height: 26, shape: .circle) } }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Cumbre.rule, lineWidth: 1))
        .padding(.horizontal, 16).padding(.vertical, 6)
    }
}

/// Cabecera de perfil: avatar grande, nombre, usuario y tres contadores.
struct SkeletonProfileHeader: View {
    var body: some View {
        SkeletonDelay {
            VStack(spacing: 12) {
                Bone(height: 84, shape: .circle)
                Bone(width: 150, height: 20)
                Bone(width: 100, height: 12)
                HStack(spacing: 28) {
                    ForEach(0..<3, id: \.self) { _ in VStack(spacing: 6) { Bone(width: 34, height: 18); Bone(width: 56, height: 10) } }
                }
                .padding(.top, 6)
                VStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in SkeletonGradeRow() } }.padding(.top, 10)
            }
            .padding(.top, 24)
            .shimmer()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

/// Tiempo: tarjeta del día de hoy + filas de días.
struct SkeletonWeather: View {
    var body: some View {
        SkeletonDelay {
            VStack(alignment: .leading, spacing: 14) {
                Bone(width: 120, height: 11)
                Bone(height: 150)
                ForEach(0..<7, id: \.self) { _ in
                    HStack(spacing: 12) { Bone(width: 44, height: 14); Bone(width: 28, height: 28, shape: .circle); Bone(height: 10); Bone(width: 50, height: 14) }
                }
            }
            .padding(16)
            .shimmer()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

/// Ficha de escuela al abrir: puntuación, 16 h y las pestañas.
struct SkeletonSchoolDetail: View {
    var body: some View {
        SkeletonDelay {
            VStack(alignment: .leading, spacing: 14) {
                Bone(width: 220, height: 26)
                Bone(width: 160, height: 12)
                HStack(spacing: 12) { Bone(width: 84, height: 84); VStack(alignment: .leading, spacing: 8) { Bone(height: 14); Bone(width: 160, height: 11); Bone(width: 120, height: 11) } }
                Bone(height: 64)
                HStack(spacing: 8) { ForEach(0..<4, id: \.self) { _ in Bone(height: 30, shape: .pill) } }
                Bone(height: 220)
            }
            .padding(16)
            .shimmer()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

/// Conversación: burbujas alternas.
struct SkeletonChat: View {
    var body: some View {
        SkeletonDelay {
            VStack(spacing: 12) {
                ForEach(0..<7, id: \.self) { i in
                    HStack {
                        if i % 2 == 1 { Spacer(minLength: 60) }
                        Bone(width: [180, 120, 220, 150, 90, 200, 140][i], height: i % 3 == 0 ? 52 : 36)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        if i % 2 == 0 { Spacer(minLength: 60) }
                    }
                }
            }
            .padding(16)
            .shimmer()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

/// Formulario (editar perfil, etc.): etiqueta + campo, varias veces.
struct SkeletonForm: View {
    var fields: Int = 5
    var body: some View {
        SkeletonDelay {
            VStack(alignment: .leading, spacing: 18) {
                Bone(height: 84, shape: .circle).frame(maxWidth: .infinity)
                ForEach(0..<fields, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: 6) { Bone(width: 90, height: 10); Bone(height: 40) }
                }
            }
            .padding(16)
            .shimmer()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}
