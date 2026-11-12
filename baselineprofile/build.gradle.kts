// Genera el perfil base de la app (arranque, lista de escuelas, ficha y guía) con Macrobenchmark.
// Se lanza a mano con un emulador o móvil conectado (API 28+), por ejemplo el Pixel_7:
//   ./gradlew.bat :app:generateReleaseBaselineProfile
// El resultado se copia solo a app/src/release/generated/baselineProfiles/. Necesita KEYSTORE_PASSWORD y
// KEY_PASSWORD como un build de release. No entra en assembleDebug ni en los tests normales.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.meteomontana.android.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
