package com.meteomontana.android.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardarraíl de módulos: una función (feature) no importa pantallas de otra. Para ir a otra pantalla usa
 * el Navigator de :core:navigation; para pintar algo de otra función, un parámetro composable (slot).
 * Tampoco los core/... pueden tocar pantallas.
 */
class FeatureIsolationGuardTest {

    private val root = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").isFile }

    private fun ktFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }

    @Test
    fun `features only reference their own screens package`() {
        val features = File(root, "feature").listFiles()?.filter { it.isDirectory }.orEmpty()
        val offenders = features.flatMap { feature ->
            val own = "com.meteomontana.android.ui.screens.${feature.name}"
            ktFiles(File(feature, "src")).flatMap { file ->
                SCREENS.findAll(file.readText()).map { it.value }
                    .filter { it != own && !it.startsWith("$own.") }
                    .map { "${feature.name}/${file.name}: $it" }
            }
        }
        assertTrue("Una función importa pantallas de otra:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `core modules never reference screens`() {
        val core = File(root, "core").listFiles()?.filter { it.isDirectory }.orEmpty()
        val offenders = core.flatMap { module ->
            ktFiles(File(module, "src")).filter { SCREENS.containsMatchIn(it.readText()) }.map { "${module.name}/${it.name}" }.toList()
        }
        assertTrue("Un módulo core depende de pantallas:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private companion object {
        val SCREENS = Regex("""com\.meteomontana\.android\.ui\.screens\.[a-z]+""")
    }
}
