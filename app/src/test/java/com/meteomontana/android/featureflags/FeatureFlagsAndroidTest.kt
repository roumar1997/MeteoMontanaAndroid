package com.meteomontana.android.featureflags

import android.content.Context
import android.content.SharedPreferences
import com.meteomontana.android.EsTextSource
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.domain.model.BlockLine
import com.meteomontana.android.domain.model.CreateMeetupRequest
import com.meteomontana.android.domain.model.FeatureDisabledException
import com.meteomontana.android.domain.model.FeatureKey
import com.meteomontana.android.domain.usecase.feed.PublishFeedPostUseCase
import com.meteomontana.android.ui.components.effectiveShare
import com.meteomontana.android.ui.screens.detail.TickFeedPublisher
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Interruptores de funciones en la app: crear quedada, publicar en Comunidad. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsAndroidTest {

    private val d = StandardTestDispatcher()
    private val flags = FakeFeatureFlagRepository()
    private val createMeetup: com.meteomontana.android.domain.usecase.meetups.CreateMeetupUseCase = mockk()
    private val request = CreateMeetupRequest("esc", "X", null, "BOULDER", "OPEN", null, null, listOf("2026-07-25"))

    @Before fun setUp() {
        EsTextSource.install()
        Dispatchers.setMain(d)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun meetupsVm(): MeetupsViewModel {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString(any(), any()) } returns null
            every { getInt(any(), any()) } returns -1
            every { edit() } returns editor
        }
        val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns prefs }
        val getMeetups = mockk<com.meteomontana.android.domain.usecase.meetups.GetMeetupsUseCase>()
        coEvery { getMeetups.execute(any(), any(), any()) } returns emptyList()
        val profile = mockk<com.meteomontana.android.domain.usecase.profile.GetMyProfileUseCase>()
        coEvery { profile() } throws RuntimeException("sin perfil en este test")
        val location = mockk<com.meteomontana.android.domain.port.LocationProvider>(relaxed = true)
        coEvery { location.current() } returns null
        return MeetupsViewModel(
            getMeetups, mockk(), createMeetup, mockk(), mockk(relaxed = true), mockk(relaxed = true),
            mockk(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(), mockk(), mockk(), profile, location, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), context)
    }

    @Test fun `un 503 FEATURE_DISABLED al crear deja el aviso y no un error generico`() = runTest {
        coEvery { createMeetup.execute(any()) } throws FeatureDisabledException(FeatureKey.MEETUP_CREATE, "En pausa")
        val vm = meetupsVm(); advanceUntilIdle()

        vm.create(request, onSuccess = {}, onError = {})
        advanceUntilIdle()

        assertEquals("En pausa", vm.createPaused.value?.message)
        assertNull(vm.createError.value)
    }

    @Test fun `con todo encendido no hay aviso`() = runTest {
        val vm = meetupsVm(); advanceUntilIdle()
        assertNull(vm.createPaused.value)
    }

    @Test fun `el interruptor de compartir nunca esta encendido con publicar en pausa`() {
        assertTrue(effectiveShare(share = true, publishEnabled = true))
        assertFalse(effectiveShare(share = true, publishEnabled = false))
        assertFalse(effectiveShare(share = false, publishEnabled = true))
    }

    @Test fun `publicar con 503 FEATURE_DISABLED avisa con el mensaje del servidor y no como fallo generico`() = runTest {
        val publish = mockk<PublishFeedPostUseCase>()
        coEvery { publish(any(), any(), any(), any(), any()) } throws
            FeatureDisabledException(FeatureKey.FEED_PUBLISH, "Comunidad en pausa")
        val publisher = TickFeedPublisher(publish, mockk(relaxed = true), mockk(relaxed = true))
        val block = mockk<Block>(relaxed = true)
        val line = mockk<BlockLine>(relaxed = true) { every { id } returns "l1" }
        var offMessage: String? = null
        var genericFailure = false

        publisher.publish(block, line, wasProject = false,
            onPublishFailed = { genericFailure = true }, onFeatureOff = { offMessage = it })

        assertEquals("Comunidad en pausa", offMessage)
        assertFalse(genericFailure)
    }
}
