package com.example.snsassistant.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class PostWithReplies(
    @Embedded val post: PostEntity,
    @Relation(parentColumn = "id", entityColumn = "postId")
    val replies: List<ReplyEntity>
)

