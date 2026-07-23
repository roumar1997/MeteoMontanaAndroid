package com.meteomontana.android.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Guardado de la foto de celebración en el carrete (Pictures/Cumbre) — efecto
 * de PLATAFORMA (MediaStore), fuera de la UI. Red de seguridad: aunque algo
 * falle al publicar, la foto no se pierde. Espejo del
 * UIImageWriteToSavedPhotosAlbum de iOS (FeedCelebrationPhoto.swift).
 *
 * Solo API 29+ (en 26-28 exigiría WRITE_EXTERNAL_STORAGE y se omite).
 * Best-effort: cualquier fallo se traga (el guardado en carrete es un extra,
 * jamás debe romper el flujo de publicar).
 */
fun saveCelebrationToGallery(context: Context, source: Uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,
                "cumbre-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Cumbre")
        }
        val resolver = context.contentResolver
        val target = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return
        resolver.openOutputStream(target)?.use { out ->
            resolver.openInputStream(source)?.use { input -> input.copyTo(out) }
        }
    }
}
