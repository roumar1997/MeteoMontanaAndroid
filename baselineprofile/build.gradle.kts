// Genera el perfil base de la app (arranque, lista de escuelas, ficha y guía) con Macrobenchmark.
// Se lanza a mano con un emulador o móvil conectado (API 28+), por ejemplo el Pixel_7:
//   ./gradlew.bat :app:generateReleaseBaselineProfile
// El resultado se copia solo a app/src/release/generated/baselineProfiles/. Firma debug (no necesita el keystore) y
// no sube mapping a Crashlytics. No entra en assembleDebug ni en los tests normales.
// Lo útil (ficha y guía) va detrás del login. Para perfilarlo: lanzar una vez con
//   -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
// iniciar sesión a mano en la app que queda instalada y volver a lanzar con el mismo parámetro (sin él, la tarea
// desinstala la app al acabar y se pierde la sesión). Sin sesión el perfil solo cubre el arranque.
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
