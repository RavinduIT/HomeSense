package lk.ac.ucsc.scs3311.smarthome.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import lk.ac.ucsc.scs3311.smarthome.R

/**
 * Local notification channels and posting.
 *
 * Two channels, deliberately separate: a safety cut-off is not the same class
 * of event as a device going offline, and the user must be able to silence one
 * without silencing the other. Android's per-channel controls only help if the
 * channels actually mean different things.
 */
object SafetyNotifications {

    const val CHANNEL_SAFETY = "safety_alerts"
    const val CHANNEL_DEVICE = "device_status"

    /**
     * Must match the channel id the worker sets on its FCM messages
     * (`worker/src/notifications.ts`), or a push arriving while the app is in
     * the background lands on the default channel with default importance.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SAFETY,
                "Safety cut-offs",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "An appliance was switched off automatically for safety."
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEVICE,
                "Device status",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A device stopped responding or went offline."
            },
        )
    }

    fun postSafetyCutoff(context: Context, message: String, notificationId: Int) {
        post(context, CHANNEL_SAFETY, "Safety cut-off", message, notificationId, critical = true)
    }

    fun postDeviceProblem(context: Context, title: String, message: String, notificationId: Int) {
        post(context, CHANNEL_DEVICE, title, message, notificationId, critical = false)
    }

    private fun post(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int,
        critical: Boolean,
    ) {
        // From API 33 the permission can be refused, and posting without it
        // throws. Silently skipping is correct here: the alert is already in
        // /alerts and the in-app alert centre will show it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent = openApp?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (critical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(
                if (critical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS,
            )
            .setAutoCancel(true)
            .apply { pendingIntent?.let(::setContentIntent) }
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
