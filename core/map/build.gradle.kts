// Mapa (MapLibre): arranque, estilos, teselas sin conexión, iconos de marcadores y ciclo de vida del MapView.
plugins {
    alias(libs.plugins.cumbre.android.library.compose)
    alias(libs.plugins.cumbre.android.hilt)
}

android {
    namespace = "com.meteomontana.android.core.map"
}

dependencies {
    api(project(":shared"))
    api(libs.maplibre)
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.kermit)
}
