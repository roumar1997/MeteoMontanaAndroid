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
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Random.nextInt(), notif)
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
