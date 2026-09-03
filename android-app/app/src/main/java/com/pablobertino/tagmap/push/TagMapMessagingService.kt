package com.pablobertino.tagmap.push

import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pablobertino.tagmap.BuildConfig
import com.pablobertino.tagmap.TagMapApp
import com.pablobertino.tagmap.data.AppContainer
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Recibe los push de la Edge Function `notify` y registra el token en `tagmap.mobile_devices`. */
class TagMapMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registerToken((application as TagMapApp).container, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val title = msg.notification?.title ?: d["title"] ?: return
        val body = msg.notification?.body ?: d["body"] ?: ""
        val channel = msg.notification?.channelId
            ?: when (d["event_type"]) { "ENTRY" -> Notifications.CH_ARRIVALS; "EXIT" -> Notifications.CH_DEPARTURES; else -> Notifications.CH_SYSTEM }
        Notifications.show(this, channel, title, body, (d["event_id"] ?: title).hashCode())
    }

    companion object {
        private const val TAG = "TagMapPush"

        /** Llamar al iniciar sesión / abrir la app: obtiene el token FCM y lo registra en Supabase. */
        fun syncToken(container: AppContainer) {
            if (!BuildConfig.HAS_FIREBASE) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    registerToken(container, token)
                }.onFailure { Log.w(TAG, "No se pudo obtener el token FCM: ${it.message}") }
            }
        }

        private fun registerToken(container: AppContainer, token: String) {
            if (container.supabase.auth.currentUserOrNull() == null) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    container.placesRepository.registerDevice(token, "${Build.MANUFACTURER} ${Build.MODEL}")
                }.onFailure { Log.w(TAG, "No se pudo registrar el dispositivo: ${it.message}") }
            }
        }
    }
}
