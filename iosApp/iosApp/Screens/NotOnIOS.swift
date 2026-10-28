import Foundation

/// Puntos de la guía y la ayuda (compartidas con Android) de cosas que iOS ya NO tiene o tiene en otro sitio. Se quitan
/// al enseñarlas. Espejo de `NOT_YET_ON_ANDROID` (Android): cuando Android porte el cambio, se mueve de aquí allí.
let notOnIOS: Set<String> = [
    "weather.compare", // la comparación de favoritas pasó del Tiempo a Escuelas (estrella FAVS), 2026-10-28
]
