package com.gtky.app.data.database

import androidx.room.TypeConverter
import com.gtky.app.data.entity.QuestionType

class Converters {
    @TypeConverter
    fun fromQuestionType(type: QuestionType): String = type.name

    @TypeConverter
    fun toQuestionType(value: String): QuestionType = QuestionType.valueOf(value)
}
