// Sistema visual de Cumbre: tema (colores, tipografía, formas, movimiento) y componentes «tontos»
// que solo pintan lo que reciben. Sin ViewModels, sin datos y sin pantallas (ver DESIGN.md).
plugins {
    alias(libs.plugins.cumbre.android.library.compose)
    alias(libs.plugins.cumbre.android.hilt)
}

android {
    namespace = "com.meteomontana.android.core.designsystem"
}

dependencies {
    // Modelos de dominio que el tema colorea (grados, tonos de rocódromo).
    api(project(":shared"))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
