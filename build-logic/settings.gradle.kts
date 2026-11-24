// Plugins de convención de Cumbre: la configuración común de los módulos Android (core/*, y más
// adelante feature/*) vive aquí una sola vez, en vez de copiada en cada build.gradle.kts.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
include(":convention")
