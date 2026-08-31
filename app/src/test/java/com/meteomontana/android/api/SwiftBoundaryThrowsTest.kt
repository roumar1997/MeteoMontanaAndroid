package com.meteomontana.android.api

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GUARD arquitectónico (KMP↔Swift). El contenedor `IosDependencyContainer`
 * expone a Swift, como propiedades PÚBLICAS, no solo las clases `Ktor*Api` sino
 * también USE CASES y REPOSITORIES. Swift llama sus métodos `suspend` con `try?`.
 * Si una `suspend fun` pública puede lanzar (I/O de Ktor / SQLDelight) y NO lleva
 * `@Throws`, SKIE genera una firma Swift NO-throwing → sin red la excepción
 * escapa a Kotlin/Native y ABORTA el proceso (crash offline que no se ve
 * compilando; el CI verde solo prueba que compila).
 *
 * Este test recorre esos ficheros y exige `@Throws` en cada `suspend fun`
 * pública, SALVO que el cuerpo trague la excepción él mismo (patrón
 * `try { } catch` sin relanzar, como KtorModerationApi o GetMeetupsUseCase).
 * Habría cazado el crash de `KtorAppVersionApi.get()` (gate 2.19.0) y el de los
 * 10 `MeetupUseCases.execute` sin `@Throws` (P0.2).
 */
class SwiftBoundaryThrowsTest {

    /** Ficheros del módulo compartido cuyas clases se exponen a Swift como `val`
     *  público en IosDependencyContainer. Rutas relativas a shared/commonMain. */
    private val swiftReachableFiles = listOf(
        // ── Ktor*Api directos (10) ──
        "data/api/KtorAppVersionApi.kt", "data/api/KtorSchoolApi.kt",
        "data/api/KtorRadarApi.kt", "data/api/KtorMountainApi.kt",
        "data/api/KtorNoteApi.kt", "data/api/KtorPhotoApi.kt",
        "data/api/KtorChatPushApi.kt", "data/api/KtorBlockApi.kt",
        "data/api/KtorMeetupApi.kt", "data/api/KtorModerationApi.kt",
        // ── Use cases expuestos como val público ──
        "domain/usecase/meetups/MeetupUseCases.kt",
        "domain/usecase/profile/WeekendAlertUseCases.kt",
        "domain/usecase/blocks/RateLineUseCase.kt",
        // ── Repositories expuestos como val público ──
        "data/stats/MonthlyStatsRepository.kt",
        "data/saved/CachedSchoolsRepository.kt",
        "data/saved/SavedSchoolRepository.kt"
    )

    @Test
    fun `toda suspend publica expuesta a Swift va con @Throws o traga la excepcion`() {
        val root = findSharedCommonRoot()
        val offenders = mutableListOf<String>()

        for (rel in swiftReachableFiles) {
            val file = File(root, rel)
            assertTrue("No encuentro $rel en ${root.path}", file.exists())
            val lines = file.readLines()

            lines.forEachIndexed { i, raw ->
                val line = raw.trim()
                // Solo suspend PÚBLICAS (sin private/internal). Cubre `suspend fun`
                // y `suspend operator fun` (invoke de los use cases).
                if (!line.startsWith("suspend fun ") &&
                    !line.startsWith("suspend operator fun ")) return@forEachIndexed
                if (line.startsWith("private ") || line.startsWith("internal ")) return@forEachIndexed

                val hasThrows = i > 0 && lines[i - 1].trim().startsWith("@Throws")
                val swallows = swallowsWithinBody(lines, i)
                if (!hasThrows && !swallows) {
                    offenders += "$rel:${i + 1}  ${line.take(60)}"
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
        // Mira las ~12 líneas siguientes hasta la próxima declaración/fin de clase.
        val end = (declIndex + 12).coerceAtMost(lines.size)
        for (j in declIndex until end) {
            val l = lines[j].trim()
            if (j != declIndex && (l.startsWith("suspend fun ") ||
                    l.startsWith("suspend operator fun "))) break
            if (l.contains("try {") || l.startsWith("try ") || l.contains("} catch")) return true
        }
        return false
    }

    private fun findSharedCommonRoot(): File {
        // El working dir del test es el módulo (app/); subimos a la raíz y bajamos
        // a shared. Robustez: probamos varias raíces.
        val base = "shared/src/commonMain/kotlin/com/meteomontana/android"
        val candidates = listOf(File("../$base"), File(base))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("No encuentro el módulo compartido (cwd=${File(".").absolutePath})")
    }
}
