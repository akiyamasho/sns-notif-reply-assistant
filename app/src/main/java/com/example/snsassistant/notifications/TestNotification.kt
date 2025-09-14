package com.example.snsassistant.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.snsassistant.R
import com.example.snsassistant.ui.MainActivity

object TestNotification {
    private const val CHANNEL_ID = "sns_assistant_test"
    private const val NOTIF_ID = 1001

    fun send(context: Context) {
        // On Android 13+, ensure POST_NOTIFICATIONS permission is granted
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Best-effort: open app notification settings to grant permission
                runCatching {
                    val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                        .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return
            }
        }

        createChannel(context)

        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SNS Assistant Test")
            .setContentText("Tap or use the action to open the app.")
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Open in App", contentPendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SNS Assistant",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Test notifications for debugging" }
            mgr.createNotificationChannel(channel)
        }
    }
}

