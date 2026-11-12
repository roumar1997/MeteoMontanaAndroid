package com.meteomontana.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorrido para el perfil base: arranque → lista de escuelas (bajar y subir) → ficha de la primera escuela →
 * pestaña de la guía (bajar). Si algún paso no aparece (sin red, onboarding…), el perfil se queda con lo recorrido.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // Lista de escuelas
        device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
        val list = device.findObject(By.scrollable(true)) ?: return@collect
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()

        // Ficha: la primera tarjeta de la lista
        list.children.firstOrNull { it.isClickable }?.click() ?: return@collect
        device.waitForIdle()
        device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()

        // Guía: pestaña BLOQUES / VÍAS
        val tab = device.findObject(By.textContains("BLOQUES")) ?: device.findObject(By.textContains("VÍAS"))
        tab?.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.meteomontana.android"
        const val WAIT_MS = 10_000L
    }
}
