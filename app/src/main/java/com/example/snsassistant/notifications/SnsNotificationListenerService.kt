package com.example.snsassistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.text.Spanned
import android.text.style.URLSpan
import com.example.snsassistant.util.Platform
import com.example.snsassistant.util.ServiceLocator
import com.example.snsassistant.util.UrlExtractor
import com.example.snsassistant.util.PendingIntentStore
import com.example.snsassistant.util.DebugStore
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
        val lowered = combined.lowercase()
        val include = when (platform) {
            Platform.LinkedIn -> {
                val lc = lowered
                lc.contains("posted:") && lc.contains("post from 5 requests per second")
            }
            Platform.X -> lowered.contains(":") && !lowered.contains("liked your comment:")
            Platform.Instagram -> combined.contains(":")
        }
        if (!include) return

        val link = extractUrl(title) ?: extractUrl(text) ?: UrlExtractor.firstUrlFrom(combined)
        val timestamp = sbn.postTime

        scope.launch {
            try {
                val id = ServiceLocator.repository.addIncomingPost(
                    platform.display,
                    combined.take(2000),
                    timestamp,
                    link
                )
                // Store the notification's PendingIntent so UI can jump exactly where the notif would
                runCatching {
                    PendingIntentStore.put(id, sbn.notification.contentIntent)
                }
                // Capture debug snapshot for later viewing
                runCatching {
                    val dbg = buildString {
                        appendLine("pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag} postTime=${sbn.postTime}")
                        appendLine("platform=${platform.display}")
                        appendLine("title=${title}")
                        appendLine("text=${text}")
                        appendLine("extractedLink=${link ?: ""}")
                        val n = sbn.notification
                        appendLine("hasContentIntent=${n.contentIntent != null}")
                        n.contentIntent?.let { pi ->
                            appendLine("pi.creatorPackage=${pi.creatorPackage} creatorUid=${pi.creatorUid}")
                        }
                        val extras = n.extras
                        appendLine("extras keys=${extras.keySet()}")
                        extras.keySet().forEach { k ->
                            val v = extras.get(k)
                            appendLine("  $k=${v?.toString()?.take(300)}")
                        }
                        n.actions?.let { acts ->
                            appendLine("actions=${acts.size}")
                            acts.forEachIndexed { idx, a ->
                                appendLine("  action[$idx] title=${a.title} hasIntent=${a.actionIntent != null}")
                            }
                        }
                    }
                    DebugStore.put(id, dbg)
                }
            } catch (t: Throwable) {
                Log.e("NotifService", "Failed to save/generate: ${t.message}")
            }
        }
    }
}

private fun extractUrl(cs: CharSequence?): String? {
    if (cs == null) return null
    if (cs is Spanned) {
        val spans = cs.getSpans(0, cs.length, URLSpan::class.java)
        if (spans.isNotEmpty()) return spans.first().url
    }
    return UrlExtractor.firstUrlFrom(cs.toString())
}
