package com.meteomontana.android.push

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meteomontana.android.MainActivity
import com.meteomontana.android.R
import com.meteomontana.android.data.api.KtorProfileApi
import com.meteomontana.android.data.api.dto.FcmTokenRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * Recibe pushes FCM y construye notificaciones nativas que abren la app
 * en la pantalla correcta (deep link).
 *
 * Data del payload esperada:
 *   targetType: "chat" | "school" | "submission" | "contribution" | "user"
 *   targetId:   id correspondiente
 *   title, body: textos a mostrar
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {

    @Inject lateinit var profileApi: KtorProfileApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log = Logger.withTag("PushService")

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        log.i("Nuevo FCM token: ${token.take(20)}…")
        scope.launch {
            runCatching { profileApi.updateFcmToken(FcmTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "Cumbre"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val targetType = message.data["targetType"]
        val targetId   = message.data["targetId"]
        val avatarUrl  = message.data["avatarUrl"]

        // Grupo silenciado en este dispositivo → no mostrar notificación.
        if (targetType == "group" && targetId != null && MutedChatsStore.isMuted(this, targetId)) {
            return
        }

        // PendingIntent que abre MainActivity con extras → MainActivity los lee y navega.
        val intent = Intent(this, MainActivity::class.java).apply {
            // SINGLE_TOP + launchMode singleTop: con la app ABIERTA el toque llega
            // por onNewIntent() a la Activity viva (navega al chat) en vez de
            // destruir y recrear la app entera (el deep link se perdía).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("targetType", targetType)
            putExtra("targetId", targetId)
        }
        val pi = PendingIntent.getActivity(
            this, Random.nextInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC0532B.toInt())  // Terra: tiñe el icono y el nombre de la app
            .setContentTitle(title)
            .setContentText(body)
            // Los cuerpos largos (alerta del finde con 3 escuelas) se expanden.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Icono grande: el avatar del seguidor si lo hay (circular); si no, el
        // logo de Cumbre a color (para que la notificación lleve el icono de la
        // app, p.ej. en mensajes). onMessageReceived corre en background → la
        // descarga del avatar puede ser bloqueante con timeout corto.
        val largeIcon = fetchCircularBitmap(avatarUrl) ?: appLogoBitmap()
        largeIcon?.let { builder.setLargeIcon(it) }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Random.nextInt(), builder.build())
    }

    /** Logo de Cumbre (a color) como icono grande por defecto de las notificaciones. */
    private fun appLogoBitmap(): android.graphics.Bitmap? = runCatching {
        val src = decodeSampled { o -> BitmapFactory.decodeResource(resources, R.drawable.logo_cumbre, o) }
            ?: return null
        toCircle(src)
    }.getOrNull()

    private fun fetchCircularBitmap(url: String?): android.graphics.Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val bytes = conn.inputStream.use { it.readBytes() }
            val src = decodeSampled { o -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) }
                ?: return null
            toCircle(src)
        }.onFailure { log.w("No se pudo cargar avatar de push: ${it.message}") }
            .getOrNull()
    }

    /**
     * Decodifica a tamaño de icono grande (~64 dp): primero solo mide y luego decodifica con inSampleSize.
     * Antes se decodificaba la foto entera (varios MB en memoria para pintar un círculo de 64 dp).
     */
    private fun decodeSampled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LARGE_ICON_PX) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Recorta el bitmap a un círculo centrado (formato estándar de avatar). */
    private fun toCircle(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(src.width, src.height)
        val out = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val left = (size - src.width) / 2f
        val top = (size - src.height) / 2f
        canvas.drawBitmap(src, left, top, paint)
        return out
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.push_channel_description)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "meteomontana_general"
        private const val LARGE_ICON_PX = 192
    }
}
