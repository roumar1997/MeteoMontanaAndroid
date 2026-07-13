plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.meteomontana.android"
    compileSdk {
        version = release(36)
    }

    signingConfigs {
        create("release") {
            storeFile = file("../meteomontana-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "meteomontana"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.meteomontana.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 55
        versionName = "2.17.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // STAGING: los builds de desarrollo (debug) hablan con el backend de
            // staging, NUNCA con producción → no afecta a los testers de Play/App Store.
            // Para depurar contra el PC local, descomenta una de las dos de abajo:
            // 10.0.2.2  = emulador Android → localhost del PC
            // 192.168.0.12 = móvil físico en la misma red que el PC (Ethernet)
            // buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.12:8080/api/\"")
            buildConfigField("String", "API_BASE_URL", "\"https://meteomontanaapi-staging.up.railway.app/api/\"")
        }
        release {
            // R8 activado: Compose sin minificar es notablemente más lento (jank).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE_URL", "\"https://api.climbingteams.com/api/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // habilita BuildConfig.API_BASE_URL
    }
}

dependencies {
    // Módulo shared KMP (domain/model, domain/port, domain/util)
    implementation(project(":shared"))

    // Core Android + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material2: necesario para ModalBottomSheetState (lo usa el BottomSheetNavigator
    // de accompanist; se construye a mano con skipHalfExpanded=true en MainScreen).
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.accompanist.navigation.material)  // bottomSheet destinations

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network — Ktor (HTTP client compartido con shared/commonMain)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    // Imágenes
    implementation(libs.coil.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)

    // Credential Manager (Google Sign-In moderno)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Location
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)

    // Maps (MapLibre native)
    implementation(libs.maplibre)

    // Cropper de foto de perfil (zoom + rotación + circular)
    implementation(libs.ucrop)
    implementation(libs.androidx.appcompat)  // requerido por uCrop activity

    // kotlinx-serialization (Json) — outbox y deserialización de payloads
    implementation(libs.kotlinx.serialization.json)

    // Widget "Favoritas hoy" en la pantalla de inicio
    implementation(libs.androidx.glance.appwidget)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // org.json real para tests unitarios (el de Android es stub en src/test/)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
