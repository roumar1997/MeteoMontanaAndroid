import SwiftUI
import UIKit

/// Cada pestaña de la ficha (MAPA · GUÍA · TIEMPO · NOTAS) recuerda su propio scroll (Álvaro, 2026-11-13):
/// una pestaña sin visitar empieza arriba, al volver a una visitada sigue donde estaba y tocar la pestaña ya
/// activa sube arriba del todo (animado). Las cuatro comparten UN solo ScrollView; aquí se guarda y se repone
/// la posición de cada una. Mismo comportamiento que `rememberTabScrollMemory` en Android.
final class SchoolTabScrollMemory {
    private weak var scrollView: UIScrollView?
    private var observation: NSKeyValueObservation?
    private var offsets: [SchoolTab: CGFloat] = [:]
    private var recordingTab: SchoolTab = .map
    private var restoring = false
    private var generation = 0

    /// El ScrollView que contiene la ficha (lo encuentra [ScrollViewGrabber] subiendo por las vistas padre).
    func attach(_ sv: UIScrollView) {
        guard scrollView !== sv else { return }
        scrollView = sv
        observation = sv.observe(\.contentOffset, options: [.new]) { [weak self] sv, _ in
            guard let self, !self.restoring else { return }
            self.offsets[self.recordingTab] = sv.contentOffset.y + sv.adjustedContentInset.top
        }
    }

    /// Cambio de pestaña: repone la posición de la nueva (0 si no se había visitado).
    func switchTab(to newTab: SchoolTab) {
        guard newTab != recordingTab else { return }
        recordingTab = newTab
        restore(offset: offsets[newTab] ?? 0)
    }

    /// Tocar la pestaña que ya está activa: sube arriba del todo.
    func scrollToTop() {
        guard let sv = scrollView else { return }
        sv.setContentOffset(CGPoint(x: 0, y: -sv.adjustedContentInset.top), animated: true)
    }

    private func restore(offset target: CGFloat) {
        generation += 1
        restoring = true
        attempt(target: target, tries: 0, generation: generation)
    }

    /// El contenido de la pestaña nueva tarda en medirse: se espera a que haya recorrido suficiente.
    private func attempt(target: CGFloat, tries: Int, generation gen: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) { [weak self] in
            guard let self, gen == self.generation, let sv = self.scrollView else { return }
            let maxOffset = max(0, sv.contentSize.height - sv.bounds.height + sv.adjustedContentInset.bottom)
            if target <= maxOffset + 1 || tries >= 30 {
                let y = min(target, maxOffset) - sv.adjustedContentInset.top
                sv.setContentOffset(CGPoint(x: 0, y: y), animated: false)
                DispatchQueue.main.async { if gen == self.generation { self.restoring = false } }
            } else {
                self.attempt(target: target, tries: tries + 1, generation: gen)
            }
        }
    }
}

/// Vista invisible que localiza el UIScrollView de SwiftUI que la contiene.
struct ScrollViewGrabber: UIViewRepresentable {
    let memory: SchoolTabScrollMemory

    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.isUserInteractionEnabled = false
        return v
    }

    func updateUIView(_ view: UIView, context: Context) {
        DispatchQueue.main.async {
            var parent = view.superview
            while let p = parent, !(p is UIScrollView) { parent = p.superview }
            if let sv = parent as? UIScrollView { memory.attach(sv) }
        }
    }
}
