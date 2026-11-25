// Piezas de UI compartidas por varias funciones: textos de catálogo, calendario, previsión y errores,
// idioma de la app y navegador interno. Sin pantallas (ver ARCHITECTURE.md).
plugins {
    alias(libs.plugins.cumbre.android.library.compose)
    alias(libs.plugins.cumbre.android.hilt)
}

android {
    namespace = "com.meteomontana.android.core.ui"
}

dependencies {
    api(project(":shared"))
    api(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.ui)
    implementation(libs.ktor.client.core)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
}
