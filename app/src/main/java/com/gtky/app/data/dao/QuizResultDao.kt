package com.gtky.app.data.dao

import androidx.room.*
import com.gtky.app.data.entity.QuizResult
import kotlinx.coroutines.flow.Flow

data class ConnectionScore(
    val userId: Long,
    val totalAttempts: Int,
    val correctAttempts: Int
)

@Dao
interface QuizResultDao {
    @Insert
    suspend fun insertResult(result: QuizResult)

    @Insert
    suspend fun insertResults(results: List<QuizResult>)

    @Query("""
        SELECT subjectUserId AS userId,
               COUNT(*) AS totalAttempts,
               SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END) AS correctAttempts
        FROM quiz_results
        WHERE quizTakerId = :quizTakerId
        GROUP BY subjectUserId
        HAVING totalAttempts >= 3
    """)
    fun getOutgoingConnectionScores(quizTakerId: Long): Flow<List<ConnectionScore>>

    @Query("""
        SELECT quizTakerId AS userId,
               COUNT(*) AS totalAttempts,
               SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END) AS correctAttempts
        FROM quiz_results
        WHERE subjectUserId = :subjectUserId
        GROUP BY quizTakerId
        HAVING totalAttempts >= 3
    """)
    fun getIncomingConnectionScores(subjectUserId: Long): Flow<List<ConnectionScore>>

    @Query("SELECT COUNT(*) FROM quiz_results WHERE quizTakerId = :quizTakerId AND subjectUserId = :subjectUserId")
    suspend fun getAttemptCount(quizTakerId: Long, subjectUserId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_results WHERE quizTakerId = :userId")
    suspend fun getTotalQuizAnsweredByUser(userId: Long): Int

    @Query("DELETE FROM quiz_results WHERE quizTakerId = :userId OR subjectUserId = :userId")
    suspend fun deleteResultsForUser(userId: Long)

    @Query("""
        SELECT questionId FROM quiz_results
        WHERE quizTakerId = :quizTakerId AND subjectUserId = :subjectUserId
    """)
    suspend fun getAlreadyAttemptedQuestionIds(quizTakerId: Long, subjectUserId: Long): List<Long>
}
