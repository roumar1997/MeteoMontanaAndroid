package com.meteomontana.android.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardarraíl de capas: `ui/components` son piezas reutilizables y NO pueden
 * depender de ninguna pantalla (`ui.screens`). Si una pieza necesita una
 * pantalla, es que pertenece a esa pantalla: muévela a su paquete.
 * Paso previo a partir la app en módulos por funcionalidad.
 */
class ComponentsLayerGuardTest {

    private val componentsDir = listOf(
        File("src/main/java/com/meteomontana/android/ui/components"),
        File("app/src/main/java/com/meteomontana/android/ui/components"),
    ).first { it.isDirectory }

    @Test
    fun `ui components never reference ui screens`() {
        val offenders = componentsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                    if (SCREENS_REF.containsMatchIn(code)) "${file.name}:${i + 1}: $code" else null
                }
            }
            .toList()
        assertTrue(
            "ui/components no puede depender de ui.screens:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        val SCREENS_REF = Regex("""com\.meteomontana\.android\.ui\.screens\b""")
    }
}
