package com.meteomontana.android.meetups

import android.content.Context
import android.content.SharedPreferences
import com.meteomontana.android.domain.model.Meetup
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.UserLocation
import com.meteomontana.android.domain.port.LocationProvider
import com.meteomontana.android.domain.port.PhotoUploader
import com.meteomontana.android.ui.screens.meetups.MeetupsViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MeetupsViewModel: quedadas. Reglas que protege:
 *  - join/create mapean los códigos del backend (GENDER_REQUIRED, MEETUP_FULL…)
 *    a mensajes claros — si el backend cambia un código, el usuario vería un
 *    error críptico
 *  - leave decrementa el aforo optimista sin bajar de 0
 *  - distanceToMeetup usa Haversine y tolera coords faltantes (null)
 *  - los filtros persisten (relation/privacy/distancia/disciplina)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeetupsViewModelTest {

    private val d = StandardTestDispatcher()

    private lateinit var getMeetups: com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase
    private lateinit var getMeetup: com.meteomontana.android.domain.usecase.meetups.GetMeetupUseCase
    private lateinit var createMeetup: com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase
    private lateinit var joinMeetup: com.meteomontana.android.domain.usecase.meetups.JoinMeetupUseCase
    private lateinit var leaveMeetup: com.meteomontana.android.domain.usecase.meetups.LeaveMeetupUseCase
    private lateinit var deleteMeetup: com.meteomontana.android.domain.usecase.meetups.DeleteMeetupUseCase
    private lateinit var updateMeetup: com.meteomontana.android.domain.usecase.meetups.UpdateMeetupUseCase
    private lateinit var kick: com.meteomontana.android.domain.usecase.meetups.KickMeetupMemberUseCase
    private lateinit var report: com.meteomontana.android.domain.usecase.meetups.ReportMeetupUseCase
    private lateinit var getAlert: com.meteomontana.android.domain.usecase.meetups.GetMeetupAlertUseCase
    private lateinit var setAlert: com.meteomontana.android.domain.usecase.meetups.SetMeetupAlertUseCase
    private lateinit var updateGear: com.meteomontana.android.domain.usecase.meetups.UpdateMyGearUseCase
    private lateinit var searchSchools: com.meteomontana.android.domain.usecase.schools.SearchSchoolsUseCase
    private lateinit var getRangeScores: com.meteomontana.android.domain.usecase.schools.GetRangeScoresUseCase
    private lateinit var getMyProfile: com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase
    private lateinit var location: LocationProvider
    private lateinit var photoUploader: PhotoUploader
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val baseProfile = PrivateProfile(
        uid = "me", email = null, username = "yo", displayName = "Yo", photoUrl = null,
        bio = null, topGrade = null, isPublic = true, isAdmin = false, isPremium = false,
        gender = "MAN", gearJson = null)

    private fun meetup(id: String, members: Int = 2, lat: Double? = 40.75, lon: Double? = -3.85) = Meetup(
        id = id, schoolId = "esc", schoolName = "Pedriza", schoolLat = lat, schoolLon = lon,
        name = "Quedada $id", description = null, discipline = "BOULDER", privacy = "OPEN",
        memberLimit = null, memberCount = members, photoUrl = null, creatorUid = "otro",
        creatorUsername = "otro", creatorPhotoUrl = null, conversationId = "c$id",
        days = listOf("2026-07-25"), lastDay = "2026-07-25", expiresAt = 0, createdAt = 0,
        members = emptyList(), joined = false)

    @Before fun setUp() {
        Dispatchers.setMain(d)
        editor = mockk(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        prefs = mockk(relaxed = true) {
            every { getString(any(), any()) } returns null
            every { getInt(any(), any()) } returns -1
            every { edit() } returns editor
        }
        context = mockk { every { getSharedPreferences(any(), any()) } returns prefs }
        getMeetups = mockk(); getMeetup = mockk(); createMeetup = mockk(); joinMeetup = mockk()
        leaveMeetup = mockk(relaxed = true); deleteMeetup = mockk(relaxed = true)
        updateMeetup = mockk(); kick = mockk(relaxed = true); report = mockk(relaxed = true)
        getAlert = mockk(relaxed = true); setAlert = mockk(relaxed = true); updateGear = mockk()
        searchSchools = mockk(); getRangeScores = mockk(); getMyProfile = mockk()
        location = mockk(relaxed = true); photoUploader = mockk(relaxed = true)
        coEvery { getMeetups.execute(any(), any(), any()) } returns emptyList()
        coEvery { getMyProfile() } returns baseProfile
        coEvery { location.current() } returns null
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = MeetupsViewModel(
        getMeetups, getMeetup, createMeetup, joinMeetup, leaveMeetup, deleteMeetup,
        updateMeetup, kick, report, getAlert, setAlert, updateGear, searchSchools,
        getRangeScores, getMyProfile, location, photoUploader, context)

    @Test fun `join mapea MEETUP_FULL a mensaje claro`() = runTest {
        coEvery { joinMeetup.execute("m1") } throws RuntimeException("MEETUP_FULL")
        val vm = vm(); advanceUntilIdle()
        vm.join("m1"); advanceUntilIdle()
        assertEquals("La quedada está completa.", vm.detailState.value.error)
        assertTrue(!vm.detailState.value.joining)
    }

    @Test fun `join mapea GENDER_REQUIRED a instruccion de perfil`() = runTest {
        coEvery { joinMeetup.execute("m1") } throws RuntimeException("GENDER_REQUIRED")
        val vm = vm(); advanceUntilIdle()
        vm.join("m1"); advanceUntilIdle()
        assertTrue(vm.detailState.value.error!!.contains("Mujer"))
    }

    @Test fun `create mapea GENDER_REQUIRED y llama onError`() = runTest {
        coEvery { createMeetup.execute(any()) } throws RuntimeException("GENDER_REQUIRED")
        val vm = vm(); advanceUntilIdle()
        var errCalled = false
        vm.create(
            com.meteomontana.android.domain.model.CreateMeetupRequest(
                "esc", "X", null, "BOULDER", "OPEN", null, null, listOf("2026-07-25")),
            onSuccess = {}, onError = { errCalled = true })
        advanceUntilIdle()
        assertTrue(errCalled)
        assertTrue(vm.createError.value!!.contains("Mujer"))
    }

    @Test fun `leave decrementa aforo optimista sin bajar de cero`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.loadMeetup("m1")
        coEvery { getMeetup.execute("m1") } returns meetup("m1", members = 0)
        advanceUntilIdle()
        vm.leave("m1"); advanceUntilIdle()
        // memberCount ya era 0 → maxOf(0, -1) = 0
        assertEquals(0, vm.detailState.value.meetup!!.memberCount)
        assertTrue(!vm.detailState.value.meetup!!.joined)
    }

    @Test fun `distanceToMeetup devuelve null si no hay ubicacion o coords`() = runTest {
        val vm = vm(); advanceUntilIdle()
        // Sin ubicación de usuario (location.current devolvió null en setUp).
        assertNull(vm.distanceToMeetup(meetup("m1")))
    }

    @Test fun `distanceToMeetup calcula km cuando hay ubicacion y coords`() = runTest {
        coEvery { location.current() } returns UserLocation(40.4168, -3.7038) // Madrid
        val vm = vm(); advanceUntilIdle()
        val km = vm.distanceToMeetup(meetup("m1", lat = 40.7680, lon = -3.8520)) // Pedriza ~42km
        assertTrue("debe rondar 30-60 km, fue $km", km != null && km in 20.0..70.0)
    }

    @Test fun `searchSchools ignora consultas de menos de 2 letras`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.searchSchools("a"); advanceUntilIdle()
        assertTrue(vm.schoolResults.value.isEmpty())
        io.mockk.coVerify(exactly = 0) { searchSchools(any(), any()) }
    }

    @Test fun `setFilterPrivacy persiste en prefs`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.setFilterPrivacy("WOMEN"); advanceUntilIdle()
        assertEquals("WOMEN", vm.listState.value.filterPrivacy)
        io.mockk.verify { editor.putString("privacy", "WOMEN") }
    }

    @Test fun `toggleFilterDay agrega y quita del set`() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.toggleFilterDay("2026-07-25")
        assertTrue(vm.listState.value.filterDays.contains("2026-07-25"))
        vm.toggleFilterDay("2026-07-25")
        assertTrue(vm.listState.value.filterDays.isEmpty())
    }
}
