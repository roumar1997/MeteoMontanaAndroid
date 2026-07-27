package com.meteomontana.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GUARDARRAÍL de las reglas de capas de ARCHITECTURE.md §1, al estilo del
 * ArchitectureTest (ArchUnit) del backend pero escaneando fuentes — mismo
 * patrón que [com.meteomontana.android.api.SwiftBoundaryThrowsTest], sin
 * añadir dependencias al build.
 *
 * Razón de ser: tras el refactor SOLID (P2.3), estas reglas solo se mantienen
 * si algo las vigila. Si una feature nueva mete un `Ktor*Api` en un ViewModel
 * o un `*Dto` en una pantalla, el CI se pone rojo ANTES de llegar a main.
 *
 * Si una regla estorba, se discute y se cambia — nunca se ignora en silencio.
 */
class ArchitectureRulesTest {

    /**
     * REGLA 1: los ViewModels hablan con use cases, no con APIs de red.
     * (ARCHITECTURE.md §1.1)
     */
    @Test
    fun `ningun ViewModel inyecta una Ktor Api directamente`() {
        val offenders = mutableListOf<String>()
        uiFiles().filter { it.name.endsWith("ViewModel.kt") }.forEach { f ->
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
                if (KTOR_API_REGEX.containsMatchIn(line)) {
                    offenders += "${f.name}:${i + 1}  ${line.take(80)}"
                }
            }
        }
        assertTrue(
            "ViewModels que usan Ktor*Api directamente (deben ir por use cases):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * REGLA 2: la UI (pantallas y componentes) no toca DTOs de red — usa
     * modelos de dominio. Un DTO en la UI acopla la pantalla al contrato HTTP.
     * (ARCHITECTURE.md §1.1)
     */
    @Test
    fun `ninguna pantalla ni componente usa un Dto de red`() {
        val offenders = mutableListOf<String>()
        uiFiles().forEach { f ->
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
                // Solo DTOs del paquete data.api (los *Request siguen siendo DTOs
                // a propósito: son el cuerpo de escritura, no estado de pantalla).
                if (DATA_API_DTO_REGEX.containsMatchIn(line) && !line.contains("Request")) {
                    offenders += "${f.relativeTo(uiRoot()).path}:${i + 1}  ${line.take(80)}"
                }
            }
        }
        assertTrue(
            "UI que usa DTOs de red (debe usar modelos de dominio):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * REGLA 3: el dominio compartido es puro — no mira hacia `data`.
     * La flecha va data → domain, nunca al revés. (ARCHITECTURE.md §1.2)
     *
     * Excepción viva y documentada: los *Request de escritura viven en
     * `data/api/dto` y algunos puertos los aceptan como parámetro (deuda
     * conocida; se salda al migrar los Request a modelos de dominio).
     */
    @Test
    fun `el dominio compartido no importa la capa de datos`() {
        val offenders = mutableListOf<String>()
        val domainRoot = File(sharedRoot(), "domain")
        domainRoot.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (!line.startsWith("import com.meteomontana.android.data.")) return@forEachIndexed
                // Excepción documentada: los *Request de escritura.
                if (line.endsWith("Request")) return@forEachIndexed
                offenders += "${f.relativeTo(domainRoot).path}:${i + 1}  ${line.take(80)}"
            }
        }
        assertTrue(
            "dominio que importa data/ (la flecha va al revés):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    // ------------------------------------------------------------ helpers

    private companion object {
        /** `KtorAlgoApi` como tipo (inyección, propiedad o llamada), no en texto. */
        val KTOR_API_REGEX = Regex("""\bKtor[A-Za-z]+Api\b""")
        /** Tipos `*Dto` del paquete data.api (cualificados o importados). */
        val DATA_API_DTO_REGEX = Regex("""com\.meteomontana\.android\.data\.api\.[A-Za-z.]*[A-Za-z]+Dto\b""")
    }

    private fun uiRoot(): File = firstExisting(
        "app/src/main/java/com/meteomontana/android/ui",
        "src/main/java/com/meteomontana/android/ui"
    )

    private fun sharedRoot(): File = firstExisting(
        "shared/src/commonMain/kotlin/com/meteomontana/android",
        "../shared/src/commonMain/kotlin/com/meteomontana/android"
    )

    private fun uiFiles(): List<File> =
        uiRoot().walkTopDown().filter { it.extension == "kt" }.toList()

    private fun firstExisting(vararg paths: String): File =
        paths.map(::File).firstOrNull { it.isDirectory }
            ?: paths.map { File("../$it") }.firstOrNull { it.isDirectory }
            ?: error("No encuentro ninguna de $paths (cwd=${File(".").absolutePath})")
}
