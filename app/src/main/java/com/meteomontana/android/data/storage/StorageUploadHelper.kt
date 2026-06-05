package com.meteomontana.android.data.storage

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageUploadHelper @Inject constructor(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    /** Sube la foto de una piedra pendiente a Firebase Storage y devuelve la URL de descarga. */
    suspend fun uploadBoulderPhoto(uri: Uri, schoolId: String): String {
        val uid = auth.currentUser?.uid ?: error("Usuario no autenticado")
        val ts = System.currentTimeMillis()
        val ref = storage.reference.child("piedra-photos-pending/${uid}_${schoolId}_${ts}.jpg")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer la foto")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }
}
