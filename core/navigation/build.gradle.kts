// Contrato de navegación (Destination, Navigator, rutas). Sin pantallas: lo usan app y features.
plugins {
    alias(libs.plugins.cumbre.android.library)
}

android {
    namespace = "com.meteomontana.android.core.navigation"
}

dependencies {
    implementation(libs.navigation.compose)
}
