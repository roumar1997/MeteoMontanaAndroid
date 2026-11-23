package com.meteomontana.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard against callbacks that a screen accepts but never uses (it happened with
 * `onOpenGymTraining` / `onOpenSchoolEntries` in ProfileScreen: wired from MainScreen,
 * forwarded one level and then ignored, so the entry point silently did not exist).
 * Scope: ProfileScreen.kt, the screen where it was found. A callback counts as used when
 * it appears somewhere other than its declarations and pure `onX = onX` forwards.
 */
class DeadCallbackGuardTest {

    @Test
    fun `ProfileScreen declares no callback that is never consumed`() {
        val rel = "src/main/java/com/meteomontana/android/ui/screens/profile/ProfileScreen.kt"
        val src = listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
        val names = Regex("""\b(on[A-Z]\w*)\s*:\s*\(""").findAll(src).map { it.groupValues[1] }.toSet()
        val dead = names.filter { n ->
            val declarations = Regex("""\b$n\s*:\s*\(""").findAll(src).count()
            val forwards = Regex("""\b$n\s*=\s*$n\b""").findAll(src).count()
            val total = Regex("""\b$n\b""").findAll(src).count()
            // One declaration forwarded to another file is fine; a chain that ends inside this file must be consumed.
            declarations >= 2 && total - declarations - forwards * 2 <= 0
        }
        assertTrue("Callbacks declared in ProfileScreen but never used: $dead", dead.isEmpty())
    }
}
