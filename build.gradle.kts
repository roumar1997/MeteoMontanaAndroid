// Top-level build file.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// Análisis de código (detekt): solo falla por problemas NUEVOS; lo que ya había está en
// <módulo>/detekt-baseline.xml, que solo puede encoger. Reglas en config/detekt/detekt.yml.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        parallel = true
        source.setFrom(
            files("src/main/java", "src/main/kotlin", "src/commonMain", "src/androidMain", "src/iosMain")
        )
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "11"
        exclude("**/build/**", "**/generated/**")
        reports { html.required.set(true); xml.required.set(false); sarif.required.set(false); txt.required.set(true) }
    }
    dependencies { "detektPlugins"("io.nlopez.compose.rules:detekt:0.4.23") }
}
