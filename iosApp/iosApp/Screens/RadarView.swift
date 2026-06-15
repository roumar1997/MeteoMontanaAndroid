import SwiftUI
import WebKit

// Tab Radar — réplica de RadarScreen.kt: WebView con Windy.com embebido
// (animación de viento/lluvia/temp), centrado en la península ibérica.
struct RadarView: View {
    private let url = URL(string:
        "https://embed.windy.com/embed2.html?" +
        "lat=40&lon=-3&detailLat=40&detailLon=-3" +
        "&width=650&height=450&zoom=5" +
        "&level=surface&overlay=rain&product=ecmwf" +
        "&menu=&message=true&marker=&calendar=now&pressure=&type=map" +
        "&location=coordinates&detail=" +
        "&metricWind=km/h&metricTemp=%C2%B0C&radarRange=-1"
    )!

    var body: some View {
        WebViewContainer(url: url).ignoresSafeArea(edges: .bottom)
    }
}

private struct WebViewContainer: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.allowsInlineMediaPlayback = true
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.scrollView.bounces = false
        web.load(URLRequest(url: url))
        return web
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
