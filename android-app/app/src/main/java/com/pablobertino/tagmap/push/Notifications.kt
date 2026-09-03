package com.pablobertino.tagmap.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pablobertino.tagmap.MainActivity
import com.pablobertino.tagmap.R

/** Canales Android de la spec §8. Los ids coinciden con los que envía la Edge Function `notify`. */
object Notifications {
    const val CH_ARRIVALS = "arrivals"
    const val CH_DEPARTURES = "departures"
    const val CH_STALE = "stale_trackers"
    const val CH_SYSTEM = "system"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_ARRIVALS, "Llegadas", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_DEPARTURES, "Salidas", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_STALE, "Tags sin actualizar", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_SYSTEM, "Estado del sistema", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { nm.createNotificationChannel(it) }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, title: String, body: String, id: Int) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
