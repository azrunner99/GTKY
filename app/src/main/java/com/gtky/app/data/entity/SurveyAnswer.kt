package com.gtky.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "survey_answers",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SurveyQuestion::class, parentColumns = ["id"], childColumns = ["questionId"])
    ],
    indices = [Index(value = ["userId", "questionId"], unique = true)]
)
data class SurveyAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val questionId: Long,
    val answer: String,
    val answeredAt: Long = System.currentTimeMillis()
)
