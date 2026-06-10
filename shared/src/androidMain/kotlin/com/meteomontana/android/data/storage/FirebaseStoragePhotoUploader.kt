package com.meteomontana.android.data.storage

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.meteomontana.android.domain.port.PhotoUploader
import kotlinx.coroutines.tasks.await

class FirebaseStoragePhotoUploader(
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

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ext = when (mimeType) {
            "image/png"  -> "png"
            "image/webp" -> "webp"
            else         -> "jpg"
        }
        val path = "profile-photos/${uid}.${ext}"
        val log = co.touchlab.kermit.Logger.withTag("PhotoUploader")
        log.i("uploadProfilePhoto path=$path size=${bytes.size}B uid=$uid")
        val ref = storage.reference.child(path)
        ref.putBytes(bytes).await()
        log.i("putBytes ok, fetching downloadUrl")
        val url = ref.downloadUrl.await().toString()
        log.i("downloadUrl=$url")
        return url
    }
}
