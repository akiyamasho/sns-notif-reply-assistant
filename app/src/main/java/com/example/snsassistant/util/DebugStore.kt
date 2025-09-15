package com.example.snsassistant.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object DebugStore {
    private val map = ConcurrentHashMap<Long, String>()

    fun put(postId: Long, info: String) {
        map[postId] = info
        Log.i("NotifDebug", "Captured debug info for postId=$postId (${info.length} chars)")
    }

    fun logFor(postId: Long) {
        val info = map[postId]
        if (info == null) {
            Log.w("NotifDebug", "No debug info stored for postId=$postId")
        } else {
            Log.i("NotifDebug", "--- DEBUG for postId=$postId ---\n$info\n--- END DEBUG ---")
        }
    }
}

