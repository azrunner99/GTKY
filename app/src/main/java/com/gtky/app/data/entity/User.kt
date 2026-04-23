package com.gtky.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val photoPath: String? = null,
    val photoPromptCount: Int = 0,
    val photoPromptOptOut: Boolean = false
)
