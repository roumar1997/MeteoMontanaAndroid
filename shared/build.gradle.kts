plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    // iOS targets — compilación requiere Mac+Xcode; el código commonMain compila en Windows
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coroutines.play.services)
        }
        // iosMain.dependencies { ktor-client-darwin } — cuando llegue Mac
    }
}

// Firebase deps solo en el target Android — platform() no funciona dentro de sourceSets KMP
dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
    add("androidMainImplementation", libs.firebase.auth)
    add("androidMainImplementation", libs.firebase.firestore)
    add("androidMainImplementation", libs.firebase.storage)
}

android {
    namespace = "com.meteomontana.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
