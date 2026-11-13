package com.meteomontana.android.domain.usecase.profile

import com.meteomontana.android.data.api.dto.FcmTokenRequest
import com.meteomontana.android.data.api.dto.UpdateProfileRequest
import com.meteomontana.android.domain.model.HeightGroup
import com.meteomontana.android.domain.model.PrivateProfile
import com.meteomontana.android.domain.model.WeekendAlert
import com.meteomontana.android.domain.port.KeyValueStore
import com.meteomontana.android.domain.port.PendingHeightStore
import com.meteomontana.android.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Altura sin conexión (Álvaro, 2026-11-13): se guarda en local y se sube sola al volver la red. */
class HeightUseCasesTest {

    private class FakeRepo : ProfileRepository {
        var online = true
        var saved: HeightGroup? = null
        override suspend fun getMyProfile(): PrivateProfile = error("no")
        override suspend fun updateMyProfile(req: UpdateProfileRequest): PrivateProfile = error("no")
        override suspend fun updateFcmToken(req: FcmTokenRequest) = Unit
        override suspend fun deleteMyAccount() = Unit
        override suspend fun getMyHeight(): HeightGroup? { check(online); return saved }
        override suspend fun setMyHeight(group: HeightGroup): HeightGroup {
            check(online) { "offline" }; saved = group; return group
        }
        override suspend fun getWeekendAlert(): WeekendAlert = error("no")
        override suspend fun updateWeekendAlert(alert: WeekendAlert): WeekendAlert = error("no")
    }

    private class MemoryStore : PendingHeightStore {
        var value: HeightGroup? = null
        override suspend fun save(group: HeightGroup) { value = group }
        override suspend fun pending(): HeightGroup? = value
        override suspend fun clear() { value = null }
    }

    @Test
    fun offlineHeightIsQueuedAndUploadedWhenBackOnline() = runTest {
        val repo = FakeRepo().apply { online = false }
        val store = MemoryStore()

        assertEquals(HeightGroup.TALL, SetMyHeightUseCase(repo, store)(HeightGroup.TALL))
        assertEquals(HeightGroup.TALL, store.value)
        assertEquals(HeightGroup.TALL, GetMyHeightUseCase(repo, store)())

        repo.online = true
        FlushPendingHeightUseCase(repo, store)()
        assertEquals(HeightGroup.TALL, repo.saved)
        assertNull(store.value)
    }

    @Test
    fun onlineSaveClearsAnOlderPendingHeight() = runTest {
        val repo = FakeRepo()
        val store = MemoryStore().apply { value = HeightGroup.SHORT }

        SetMyHeightUseCase(repo, store)(HeightGroup.TALL)
        assertEquals(HeightGroup.TALL, repo.saved)
        assertNull(store.value)
    }

    private class MemoryKv : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
    }

    @Test
    fun knownHeightIsRememberedAndUsedOffline() = runTest {
        val repo = FakeRepo().apply { saved = HeightGroup.TALL }
        val get = GetMyHeightUseCase(repo, MemoryStore(), MemoryKv())

        assertEquals(HeightGroup.TALL, get())   // con red la guarda
        repo.online = false
        assertEquals(HeightGroup.TALL, get())   // sin red la lee de local: no hay que pedirla
    }

    @Test
    fun heightChosenOfflineIsStillKnownAfterItUploads() = runTest {
        val repo = FakeRepo().apply { online = false }
        val store = MemoryStore()
        val kv = MemoryKv()
        SetMyHeightUseCase(repo, store, kv)(HeightGroup.SHORT)
        repo.online = true
        FlushPendingHeightUseCase(repo, store)()
        repo.online = false
        assertEquals(HeightGroup.SHORT, GetMyHeightUseCase(repo, store, kv)())
    }

    @Test
    fun offlineWithNothingKnownStillFails() = runTest {
        val repo = FakeRepo().apply { online = false }
        var failed = false
        try { GetMyHeightUseCase(repo, MemoryStore(), MemoryKv())() } catch (e: Exception) { failed = true }
        assertEquals(true, failed)
    }
}
