package com.meteomontana.android.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorrido para el perfil base: arranque → lista de escuelas (bajar y subir) → ficha de la primera escuela (scroll)
 * → pestaña GUÍA (bloques/vías) abriendo un par de sectores → pestaña MAPA.
 *
 * Todo está detrás del login: sin sesión solo se perfila el arranque y la pantalla de entrada. Para perfilar la
 * ficha y la guía hay que conservar la sesión entre ejecuciones (ver build.gradle.kts de este módulo).
 * Las tarjetas se buscan por testTag (expuesto como resourceId en MainActivity).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        dismissPermissionDialog()

        if (!device.wait(Until.hasObject(By.res(SCHOOL_CARD)), LIST_WAIT_MS)) {
            Log.w(TAG, "No hay lista de escuelas (¿sin sesión?): el perfil solo cubre el arranque")
            return@collect
        }
        val list = device.findObject(By.scrollable(true))
        list?.setGestureMargin(device.displayWidth / 5)
        list?.fling(Direction.DOWN)
        device.waitForIdle()
        list?.fling(Direction.UP)
        device.waitForIdle()

        device.wait(Until.findObject(By.res(SCHOOL_CARD)), WAIT_MS)?.click() ?: return@collect
        device.wait(Until.hasObject(By.res(TAB_GUIDE)), WAIT_MS)
        scrollDownAndUp()

        device.findObject(By.res(TAB_GUIDE))?.click() ?: return@collect
        device.wait(Until.hasObject(By.res(GUIDE_SECTOR)), WAIT_MS)
        repeat(2) { i ->
            val sectors = device.findObjects(By.res(GUIDE_SECTOR))
            sectors.getOrNull(i)?.click()
            device.waitForIdle()
            device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            device.waitForIdle()
        }
        scrollDownAndUp()

        device.findObject(By.res(TAB_MAP))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.dismissPermissionDialog() {
        val deny = device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_deny_button")), 3_000)
        deny?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollDownAndUp() {
        val s = device.findObject(By.scrollable(true)) ?: return
        s.setGestureMargin(device.displayWidth / 5)
        s.fling(Direction.DOWN)
        device.waitForIdle()
        s.fling(Direction.DOWN)
        device.waitForIdle()
        s.fling(Direction.UP)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.meteomontana.android"
        const val TAG = "BaselineProfile"
        const val SCHOOL_CARD = "school_card"
        const val TAB_GUIDE = "detail_tab_BLOCKS"
        const val TAB_MAP = "detail_tab_MAP"
        const val GUIDE_SECTOR = "guide_sector"
        const val WAIT_MS = 10_000L
        const val LIST_WAIT_MS = 20_000L
    }
}
