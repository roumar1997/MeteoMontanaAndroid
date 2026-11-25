// Datos del lado Android: sesión, cachés locales, ubicación, fotos offline, escuelas guardadas,
// visión (piedras) y avisos silenciados. Los clientes del API viven en :shared.
plugins {
    alias(libs.plugins.cumbre.android.library)
    alias(libs.plugins.cumbre.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meteomontana.android.core.data"
}

dependencies {
    api(project(":shared"))
    implementation(project(":core:ui"))
    implementation(project(":core:map"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.kermit)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.location)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.coil.compose)
}
