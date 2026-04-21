package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.SurveyQuestion
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyQuestionDao {
    @Query("SELECT * FROM survey_questions ORDER BY id ASC")
    fun getAllQuestions(): Flow<List<SurveyQuestion>>

    @Query("SELECT * FROM survey_questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): SurveyQuestion?

    @Query("""
        SELECT * FROM survey_questions
        WHERE id NOT IN (
            SELECT questionId FROM survey_answers WHERE userId = :userId
        )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getUnansweredQuestionsForUser(userId: Long, limit: Int = 20): List<SurveyQuestion>

    @Query("SELECT COUNT(*) FROM survey_questions")
    suspend fun getQuestionCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestions(questions: List<SurveyQuestion>)

    @Query("SELECT * FROM survey_questions ORDER BY id ASC")
    suspend fun getAllQuestionsOnce(): List<SurveyQuestion>

    @Query("UPDATE survey_questions SET questionTemplateEs = :templateEs, optionsJsonEs = :optionsJsonEs WHERE id = :id")
    suspend fun updateSpanish(id: Long, templateEs: String, optionsJsonEs: String)

    @Query("""
        SELECT sq.* FROM survey_questions sq
        INNER JOIN survey_answers sa ON sq.id = sa.questionId
        WHERE sa.userId = :userId
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getAnsweredQuestionsForUser(userId: Long, limit: Int = 50): List<SurveyQuestion>
}
