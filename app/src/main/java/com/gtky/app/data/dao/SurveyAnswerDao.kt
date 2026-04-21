package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.SurveyAnswer
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyAnswerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: SurveyAnswer)

    @Query("SELECT * FROM survey_answers WHERE userId = :userId ORDER BY answeredAt DESC")
    fun getAnswersForUser(userId: Long): Flow<List<SurveyAnswer>>

    @Query("SELECT COUNT(*) FROM survey_answers WHERE userId = :userId")
    fun getAnswerCountForUser(userId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM survey_answers WHERE userId = :userId")
    suspend fun getAnswerCountForUserSync(userId: Long): Int

    @Query("SELECT answer FROM survey_answers WHERE userId = :userId AND questionId = :questionId LIMIT 1")
    suspend fun getAnswerForUserQuestion(userId: Long, questionId: Long): String?

    @Query("SELECT * FROM survey_answers WHERE userId = :userId")
    suspend fun getAllAnswersForUser(userId: Long): List<SurveyAnswer>

    @Query("DELETE FROM survey_answers WHERE userId = :userId")
    suspend fun deleteAllAnswersForUser(userId: Long)
}
