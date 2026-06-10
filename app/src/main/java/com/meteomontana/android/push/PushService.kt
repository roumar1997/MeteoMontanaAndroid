package com.meteomontana.android.push

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
        val title = message.notification?.title ?: message.data["title"] ?: "MeteoMontana"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val targetType = message.data["targetType"]
        val targetId   = message.data["targetId"]
        val avatarUrl  = message.data["avatarUrl"]

        // PendingIntent que abre MainActivity con extras → MainActivity los lee y navega.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("targetType", targetType)
            putExtra("targetId", targetId)
        }
        val pi = PendingIntent.getActivity(
            this, Random.nextInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Avatar del seguidor como icono grande (circular). onMessageReceived corre
        // en un hilo de background, así que podemos descargar de forma bloqueante
        // con un timeout corto — si falla, la notificación sale sin avatar.
        fetchCircularBitmap(avatarUrl)?.let { builder.setLargeIcon(it) }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Random.nextInt(), builder.build())
    }

    private fun fetchCircularBitmap(url: String?): android.graphics.Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val src = conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                ?: return null
            toCircle(src)
        }.onFailure { log.w("No se pudo cargar avatar de push: ${it.message}") }
            .getOrNull()
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
            val ch = NotificationChannel(CHANNEL_ID, "Avisos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mensajes, propuestas aprobadas, nuevos seguidores"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    companion object { private const val CHANNEL_ID = "meteomontana_general" }
}
