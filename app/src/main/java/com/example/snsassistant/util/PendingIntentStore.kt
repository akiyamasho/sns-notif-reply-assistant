package com.example.snsassistant.util

import android.app.PendingIntent
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object PendingIntentStore {
    private val map = ConcurrentHashMap<Long, PendingIntent>()

    fun put(postId: Long, pi: PendingIntent?) {
        if (pi == null) return
        map[postId] = pi
        Log.i("PendingIntentStore", "Stored PendingIntent for postId=$postId")
    }

    fun clear(postId: Long) {
        map.remove(postId)
    }

    fun sendFor(postId: Long): Boolean {
        val pi = map[postId] ?: return false
        return runCatching {
            pi.send()
            Log.i("PendingIntentStore", "Sent PendingIntent for postId=$postId")
            true
        }.onFailure { Log.e("PendingIntentStore", "Failed to send PendingIntent for postId=$postId: ${it.message}") }
            .getOrDefault(false)
    }

    fun logInfo(postId: Long) {
        val pi = map[postId]
        if (pi == null) {
            Log.w("PendingIntentStore", "No PendingIntent for postId=$postId")
        } else {
            Log.i(
                "PendingIntentStore",
                "PendingIntent info: creatorPackage=${pi.creatorPackage} creatorUid=${pi.creatorUid} toString=${pi}"
            )
        }
    }
}
