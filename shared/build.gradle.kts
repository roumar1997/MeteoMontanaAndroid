plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
            // kotlinx-coroutines-core es multiplatform: StateFlow, Flow, etc.
            implementation(libs.coroutines.core)
        }
    }
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
