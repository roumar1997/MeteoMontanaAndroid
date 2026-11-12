import SwiftUI
import UIKit

/// Sustituto de `AsyncImage` que pasa por `ImageCache`: memoria + disco (se ve offline) y decodificación
/// reducida fuera del hilo principal. Mismo uso: `content` recibe la imagen y `placeholder` se ve mientras
/// tanto (y si falla, como hacía `AsyncImage`).
struct CachedImage<Content: View, Placeholder: View>: View {
    let url: URL
    let maxPixel: Int?
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: UIImage?

    init(url: URL, maxPixel: Int? = ImageCache.displayMaxPixel,
         @ViewBuilder content: @escaping (Image) -> Content,
         @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.url = url
        self.maxPixel = maxPixel
        self.content = content
        self.placeholder = placeholder
        _image = State(initialValue: ImageCache.cached(url.absoluteString, maxPixel: maxPixel))
    }

    var body: some View {
        Group {
            if let image {
                content(Image(uiImage: image))
            } else {
                placeholder()
            }
        }
        .task(id: url) {
            if let hit = ImageCache.cached(url.absoluteString, maxPixel: maxPixel) { image = hit; return }
            image = await ImageCache.image(url.absoluteString, maxPixel: maxPixel)
        }
    }
}
