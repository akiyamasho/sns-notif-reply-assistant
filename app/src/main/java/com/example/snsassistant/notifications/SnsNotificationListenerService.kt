package com.example.snsassistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.snsassistant.util.Platform
import com.example.snsassistant.util.ServiceLocator
import com.example.snsassistant.util.UrlExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SnsNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val platform = Platform.fromPackageName(sbn.packageName) ?: return
        val extras = sbn.notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        val combined = listOfNotNull(title, text).joinToString(": ")
        if (combined.isBlank()) return

        // Platform-specific filtering rules
        val include = when (platform) {
            Platform.LinkedIn -> {
                val lc = combined.lowercase()
                lc.contains("posted:") && lc.contains("post from 5 requests per second")
            }
            Platform.X, Platform.Instagram -> combined.contains(":")
        }
        if (!include) return

        val link = UrlExtractor.firstUrlFrom(combined)
        val timestamp = sbn.postTime

        scope.launch {
            try {
                ServiceLocator.repository.addIncomingPost(
                    platform.display,
                    combined.take(2000),
                    timestamp,
                    link
                )
            } catch (t: Throwable) {
                Log.e("NotifService", "Failed to save/generate: ${t.message}")
            }
        }
    }
}
