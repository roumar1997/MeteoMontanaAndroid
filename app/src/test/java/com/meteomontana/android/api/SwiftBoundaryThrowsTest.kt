package com.meteomontana.android.api

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GUARD arquitectónico (KMP↔Swift). Las clases `Ktor*Api` del módulo compartido
 * que el contenedor expone a Swift como propiedades PÚBLICAS se llaman desde
 * Swift con `try?`. Si una `suspend fun` pública puede lanzar (I/O de Ktor) y NO
 * lleva `@Throws`, SKIE genera una firma Swift NO-throwing → sin red la
 * excepción escapa a Kotlin/Native y ABORTA el proceso (crash offline que no se
 * ve compilando; el CI verde solo prueba que compila).
 *
 * Este test recorre esas clases y exige `@Throws` en cada `suspend fun` pública,
 * SALVO que el cuerpo trague la excepción él mismo (patrón `try { } catch`, como
 * KtorModerationApi). Habría cazado el crash de `KtorAppVersionApi.get()`
 * (gate de versión 2.19.0) el día que se añadió.
 */
class SwiftBoundaryThrowsTest {

    /** Clases API expuestas a Swift como `val` público en IosDependencyContainer. */
    private val swiftReachableApis = setOf(
        "KtorAppVersionApi", "KtorSchoolApi", "KtorRadarApi", "KtorMountainApi",
        "KtorNoteApi", "KtorPhotoApi", "KtorChatPushApi", "KtorBlockApi",
        "KtorMeetupApi", "KtorModerationApi"
    )

    @Test
    fun `toda suspend publica expuesta a Swift va con @Throws o traga la excepcion`() {
        val apiDir = findApiDir()
        val offenders = mutableListOf<String>()

        for (name in swiftReachableApis) {
            val file = File(apiDir, "$name.kt")
            assertTrue("No encuentro $name.kt en ${apiDir.path}", file.exists())
            val lines = file.readLines()

            lines.forEachIndexed { i, raw ->
                val line = raw.trim()
                // Solo suspend PÚBLICAS (sin private/internal).
                if (!line.startsWith("suspend fun ")) return@forEachIndexed

                val hasThrows = i > 0 && lines[i - 1].trim().startsWith("@Throws")
                // Cuerpo que traga: `= try { ... } catch` o abre `try {` en el bloque.
                val swallows = swallowsWithinBody(lines, i)
                if (!hasThrows && !swallows) {
                    offenders += "$name.kt:${i + 1}  ${line.take(60)}"
                }
            }
        }

        assertTrue(
            "suspend públicas expuestas a Swift sin @Throws (crash offline latente):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** True si la función traga la excepción internamente (try/catch en su cuerpo). */
    private fun swallowsWithinBody(lines: List<String>, declIndex: Int): Boolean {
        // Mira las ~12 líneas siguientes hasta el próximo `suspend fun`/fin de clase.
        val end = (declIndex + 12).coerceAtMost(lines.size)
        for (j in declIndex until end) {
            val l = lines[j].trim()
            if (j != declIndex && l.startsWith("suspend fun ")) break
            if (l.contains("try {") || l.startsWith("try ") || l.contains("} catch")) return true
        }
        return false
    }

    private fun findApiDir(): File {
        // El working dir del test es el módulo (app/); subimos a la raíz y bajamos
        // a shared. Robustez: probamos varias raíces.
        val candidates = listOf(
            File("../shared/src/commonMain/kotlin/com/meteomontana/android/data/api"),
            File("shared/src/commonMain/kotlin/com/meteomontana/android/data/api")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("No encuentro el directorio data/api del módulo compartido " +
                "(cwd=${File(".").absolutePath})")
    }
}
