import SwiftUI

/// Recortador de foto de perfil (equivalente iOS de uCrop en Android): muestra la
/// foto dentro de un recuadro cuadrado con guía circular; el usuario hace zoom
/// (pellizco) y arrastra para encuadrar, y al confirmar se exporta el recorte
/// cuadrado. La vista de avatar ya recorta a círculo al pintarla.
struct ImageCropView: View {
    let image: UIImage
    var onCancel: () -> Void
    var onDone: (UIImage) -> Void

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height) - 32
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 24) {
                    Spacer()
                    cropContent(side: side)
                        .overlay(Circle().stroke(.white.opacity(0.9), lineWidth: 2))
                        .overlay(Rectangle().stroke(.white.opacity(0.3), lineWidth: 1))
                        .gesture(
                            SimultaneousGesture(
                                MagnificationGesture()
                                    .onChanged { v in scale = min(max(lastScale * v, 1), 6) }
                                    .onEnded { _ in lastScale = scale },
                                DragGesture()
                                    .onChanged { v in
                                        offset = CGSize(width: lastOffset.width + v.translation.width,
                                                        height: lastOffset.height + v.translation.height)
                                    }
                                    .onEnded { _ in lastOffset = offset }
                            )
                        )
                    Text("Pellizca para ampliar · arrastra para mover")
                        .font(.system(size: 13)).foregroundStyle(.white.opacity(0.7))
                    Spacer()
                    HStack(spacing: 12) {
                        Button { onCancel() } label: {
                            Text("CANCELAR").font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
                                .overlay(Rectangle().stroke(.white.opacity(0.5), lineWidth: 1))
                        }
                        Button { onDone(render(side: side)) } label: {
                            Text("USAR FOTO").font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.black).frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(.white)
                        }
                    }
                    .padding(.horizontal, 24).padding(.bottom, 12)
                }
            }
        }
    }

    /// El cuadrado recortable: la foto transformada (zoom+pan) recortada a la
    /// ventana cuadrada. Sin guías (lo que se exporta).
    private func cropContent(side: CGFloat) -> some View {
        Color.clear
            .frame(width: side, height: side)
            .overlay(
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .scaleEffect(scale)
                    .offset(offset)
            )
            .clipped()
    }

    /// Exporta el recorte a un UIImage (~1024 px de lado).
    @MainActor private func render(side: CGFloat) -> UIImage {
        let renderer = ImageRenderer(content: cropContent(side: side).frame(width: side, height: side))
        renderer.scale = max(1, 1024 / side)
        return renderer.uiImage ?? image
    }
}
