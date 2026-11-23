package com.meteomontana.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard against callbacks that a screen accepts but never uses (it happened with
 * `onOpenGymTraining` / `onOpenSchoolEntries` / `onOpenGymAscents` in ProfileScreen: wired from
 * MainScreen, forwarded one level and then ignored, so the entry point silently did not exist).
 */
class DeadCallbackGuardTest {

    private fun source(rel: String): String {
        val path = "src/main/java/com/meteomontana/android/ui/$rel"
        return listOf(File(path), File("app/$path")).first { it.exists() }.readText()
    }

    private fun callbackNames(src: String) =
        Regex("""\b(on[A-Z]\w*)\s*:\s*\(""").findAll(src).map { it.groupValues[1] }.toSet()

    /** A callback declared twice in the same file (Screen -> Content) must be consumed inside it. */
    @Test
    fun `ProfileScreen declares no callback that is never consumed`() {
        val src = source("screens/profile/ProfileScreen.kt")
        val dead = callbackNames(src).filter { n ->
            val declarations = Regex("""\b$n\s*:\s*\(""").findAll(src).count()
            val forwards = Regex("""\b$n\s*=\s*$n\b""").findAll(src).count()
            val total = Regex("""\b$n\b""").findAll(src).count()
            declarations >= 2 && total - declarations - forwards * 2 <= 0
        }
        assertTrue("Callbacks declared in ProfileScreen but never used: $dead", dead.isEmpty())
    }

    /** Leaf composables: a callback parameter must be referenced at least once besides its declaration. */
    @Test
    fun `leaf composables do not declare callbacks they never use`() {
        val files = listOf(
            "screens/profile/ProfileClimbBlocks.kt",
            "screens/schools/SchoolListScreen.kt",
            "components/TopoPhotoViewer.kt",
            "components/BlocksSection.kt"
        )
        val dead = files.flatMap { f ->
            val src = source(f)
            callbackNames(src).filter { Regex("""\b$it\b""").findAll(src).count() < 2 }.map { "$f:$it" }
        }
        assertTrue("Callbacks declared but never used: $dead", dead.isEmpty())
    }
}
