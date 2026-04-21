package com.gtky.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class QuestionType { MULTIPLE_CHOICE, TRUE_FALSE }

@Entity(tableName = "survey_questions")
data class SurveyQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionTemplate: String,
    val questionTemplateEs: String = "",
    val type: QuestionType,
    val optionsJson: String,
    val optionsJsonEs: String = "",
    val category: String
)
