package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.Forecast
import com.meteomontana.android.domain.model.RockDrying
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetRockDryOutlookUseCaseTest {
    private val today = LocalDate(2026, 11, 23)
    private var now = 0L
    private var calls = 0
    private var wet = true
    private var fail = false

    private fun forecast(): Forecast {
        val current = Current(
            "2026-11-23T10:00", 10.0, 80.0, 5.0, 0.0, 0, 50, null, 0.0, 0.0, !wet, 5, "ok", emptyList(),
            drying = if (wet) RockDrying(true, 2, null) else null
        )
        val days = (0..4).map { DayForecast(today.plus(DatePeriod(days = it)).toString(), 15.0, 5.0, 0.0, 5, "ok") }
        return Forecast("a", "A", 0.0, 0.0, current, emptyList(), days, null, null)
    }

    private fun useCase() = GetRockDryOutlookUseCase(
        getForecast = { calls++; if (fail) error("sin red"); forecast() },
        ttlMillis = 1_000, nowMillis = { now }, today = { today }
    )

    @Test fun withinTtlDoesNotAskAgain() = runTest {
        val uc = useCase()
        uc("a"); now = 999; uc("a")
        assertEquals(1, calls)
    }

    @Test fun afterTtlAsksAgainAndWetBecomesDry() = runTest {
        val uc = useCase()
        assertTrue(uc("a")!!.wet)
        wet = false; now = 1_000
        assertEquals(false, uc("a")!!.wet)
        assertEquals(2, calls)
    }

    @Test fun networkFailureReturnsLastKnownOrNull() = runTest {
        val uc = useCase()
        fail = true
        assertNull(uc("a"))
        fail = false; uc("a"); fail = true; now = 5_000
        assertNotNull(uc("a")); assertTrue(uc("a")!!.wet)
    }

    @Test fun clearForcesNewRequest() = runTest {
        val uc = useCase()
        uc("a"); uc.clear(); uc("a")
        assertEquals(2, calls)
    }
}
