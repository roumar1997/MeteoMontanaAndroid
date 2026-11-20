package com.meteomontana.android.domain.usecase.forecast

import com.meteomontana.android.domain.model.Current
import com.meteomontana.android.domain.model.DayForecast
import com.meteomontana.android.domain.model.RockDrying
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RockDryOutlookTest {
    private val today = LocalDate(2026, 11, 20)

    private fun current(dry: Boolean, hours: Int? = null) = Current(
        "2026-11-20T10:00", 10.0, 80.0, 5.0, 0.0, 0, 50, null, 0.0, 0.0, dry, 5, "ok", emptyList(),
        drying = if (dry) null else RockDrying(true, hours, null)
    )

    private fun day(offset: Int, mm: Double) =
        DayForecast(today.plus(DatePeriod(days = offset)).toString(), 15.0, 5.0, mm, 5, "ok")

    @Test fun wetWithFewHoursSaysHours() {
        val o = RockDryOutlook.of(current(false, 6), (0..4).map { day(it, 0.0) }, today)
        assertTrue(o.wet); assertEquals(6, o.dryInHours); assertNull(o.dryFromDate)
        assertEquals(RockDayState.WET, o.strip.first().second)
    }

    @Test fun wetWithManyHoursSaysDay() {
        val o = RockDryOutlook.of(current(false, 50), (0..4).map { day(it, 0.0) }, today)
        assertNull(o.dryInHours); assertEquals(LocalDate(2026, 11, 23), o.dryFromDate)
        assertEquals(
            listOf(RockDayState.WET, RockDayState.WET, RockDayState.WET, RockDayState.DRY, RockDayState.DRY),
            o.strip.map { it.second }
        )
    }

    @Test fun dryUntilWorsens() {
        val days = listOf(day(0, 0.0), day(1, 0.0), day(2, 0.0), day(3, 6.0), day(4, 0.0))
        val o = RockDryOutlook.of(current(true), days, today)
        assertEquals(false, o.wet)
        assertEquals(LocalDate(2026, 11, 22), o.dryUntilDate)
        assertEquals(LocalDate(2026, 11, 23), o.worsensOnDate)
        assertEquals(RockDayState.WET, o.strip[3].second)
    }

    @Test fun dryAllWeekHasNoWorsening() {
        val o = RockDryOutlook.of(current(true), (0..6).map { day(it, 0.0) }, today)
        assertNull(o.worsensOnDate); assertNull(o.dryUntilDate); assertEquals(5, o.strip.size)
    }

    @Test fun worsensTomorrowHasNoUntil() {
        val o = RockDryOutlook.of(current(true), listOf(day(0, 0.0), day(1, 3.0)), today)
        assertEquals(LocalDate(2026, 11, 21), o.worsensOnDate); assertNull(o.dryUntilDate)
    }

    @Test fun dubiousThresholds() {
        assertEquals(RockDayState.DUBIOUS, RockDryOutlook.dayState(0.5))
        assertEquals(RockDayState.DRY, RockDryOutlook.dayState(0.1))
    }
}
