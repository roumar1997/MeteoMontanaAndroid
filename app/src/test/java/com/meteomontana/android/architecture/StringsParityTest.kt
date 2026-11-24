package com.meteomontana.android.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cada módulo (app, core y feature) debe tener las MISMAS claves de texto en español (values) e inglés
 * (values-en). Al partir la app en módulos, un texto movido a medias se vería en un solo idioma.
 */
class StringsParityTest {

    private val root = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").isFile }

    private fun modules(): List<File> {
        val core = File(root, "core").listFiles()?.filter { it.isDirectory }.orEmpty()
        val feature = File(root, "feature").listFiles()?.filter { it.isDirectory }.orEmpty()
        return listOf(File(root, "app")) + core + feature
    }

    private fun keys(dir: File): Set<String> =
        dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
            .flatMap { f -> KEY.findAll(f.readText()).map { it.groupValues[1] }.toList() }
            .toSet()

    @Test
    fun `every module has the same string keys in Spanish and English`() {
        val problems = modules().mapNotNull { module ->
            val res = File(module, "src/main/res")
            val es = keys(File(res, "values"))
            val en = keys(File(res, "values-en"))
            val onlyEs = es - en
            val onlyEn = en - es
            if (onlyEs.isEmpty() && onlyEn.isEmpty()) null
            else "${module.name}: solo ES=${onlyEs.sorted()} solo EN=${onlyEn.sorted()}"
        }
        assertTrue("Claves ES/EN distintas:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `core modules are found`() {
        assertTrue(modules().any { it.name == "designsystem" })
    }

    private companion object {
        val KEY = Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"""")
    }
}
