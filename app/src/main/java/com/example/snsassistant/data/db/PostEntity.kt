package com.example.snsassistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val text: String,
    val timestamp: Long,
    val link: String?,
    val isDone: Boolean = false,
    val lastError: String? = null
)
