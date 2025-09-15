package com.example.snsassistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReplyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(replies: List<ReplyEntity>)

    @Query("DELETE FROM replies WHERE postId = :postId")
    suspend fun deleteForPost(postId: Long)

    @Query("SELECT * FROM replies WHERE postId = :postId ORDER BY id ASC")
    suspend fun getForPost(postId: Long): List<ReplyEntity>
}
