package com.meteomontana.android.data.storage

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.meteomontana.android.domain.port.PhotoUploader
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStoragePhotoUploader @Inject constructor(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : PhotoUploader {
    override suspend fun uploadBoulderPhoto(bytes: ByteArray, mimeType: String, schoolId: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ts = System.currentTimeMillis()
        val ref = storage.reference.child("piedra-photos-pending/${uid}_${schoolId}_${ts}.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }
}
