package com.example.snsassistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: PostEntity): Long

    @Transaction
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun observePostsWithReplies(): Flow<List<PostWithReplies>>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getById(id: Long): PostEntity?

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE posts SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE posts SET lastError = :error WHERE id = :id")
    suspend fun setLastError(id: Long, error: String?)
}
