package com.gtky.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_results",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["quizTakerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["subjectUserId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SurveyQuestion::class, parentColumns = ["id"], childColumns = ["questionId"])
    ]
)
data class QuizResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizTakerId: Long,
    val subjectUserId: Long,
    val questionId: Long,
    val givenAnswer: String,
    val isCorrect: Boolean,
    val attemptedAt: Long = System.currentTimeMillis()
)
