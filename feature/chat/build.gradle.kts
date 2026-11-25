// Chats: lista, chat 1:1, grupos y «nuevo grupo». Navega solo con el contrato de :core:navigation;
// lo que pinta de otras funciones le llega por parámetros (slots), nunca importando sus pantallas.
plugins {
    alias(libs.plugins.cumbre.android.library.compose)
    alias(libs.plugins.cumbre.android.hilt)
}

android {
    namespace = "com.meteomontana.android.feature.chat"
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.ktor.client.core)
    implementation(libs.kermit)
}
