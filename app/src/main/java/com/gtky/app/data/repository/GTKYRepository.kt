package com.gtky.app.data.repository

import com.gtky.app.data.dao.ConnectionScore
import com.gtky.app.data.database.GTKYDatabase
import com.gtky.app.data.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

data class QuizQuestion(
    val question: SurveyQuestion,
    val subjectUser: User,
    val options: List<String>,
    val optionsEs: List<String>,
    val correctAnswer: String
)

data class ConnectionEntry(
    val userA: User,
    val userB: User,
    val scoreAtoB: Double,
    val scoreBtoA: Double,
    val mutualScore: Double
)

class GTKYRepository(val db: GTKYDatabase) {

    // Users
    fun getAllUsers(): Flow<List<User>> = db.userDao().getAllUsers()

    suspend fun getUserById(id: Long) = db.userDao().getUserById(id)

    suspend fun getUserByName(name: String) = db.userDao().getUserByName(name)

    suspend fun createUser(name: String): Long = db.userDao().insertUser(User(name = name.trim()))

    suspend fun deleteUser(user: User) {
        db.surveyAnswerDao().deleteAllAnswersForUser(user.id)
        db.quizResultDao().deleteResultsForUser(user.id)
        db.userDao().deleteUser(user)
    }

    // Groups
    fun getAllGroups(): Flow<List<Group>> = db.groupDao().getAllGroups()

    suspend fun createGroup(name: String): Long = db.groupDao().insertGroup(Group(name = name.trim()))

    suspend fun deleteGroup(group: Group) = db.groupDao().deleteGroup(group)

    suspend fun joinGroup(userId: Long, groupId: Long) =
        db.groupDao().addUserToGroup(UserGroupMembership(userId, groupId))

    suspend fun leaveGroup(userId: Long, groupId: Long) =
        db.groupDao().removeUserFromGroup(UserGroupMembership(userId, groupId))

    suspend fun isUserInGroup(userId: Long, groupId: Long) =
        db.groupDao().isUserInGroup(userId, groupId)

    suspend fun getGroupIdsForUser(userId: Long) =
        db.groupDao().getGroupIdsForUser(userId)

    fun getGroupIdsForUserFlow(userId: Long): Flow<List<Long>> =
        db.groupDao().getGroupIdsForUserFlow(userId)

    fun getUsersInGroup(groupId: Long): Flow<List<User>> =
        db.userDao().getUsersInGroup(groupId)

    // Survey
    suspend fun getNextSurveyQuestions(userId: Long, count: Int = 20) =
        db.surveyQuestionDao().getUnansweredQuestionsForUser(userId, count)

    fun getAnswerCountForUser(userId: Long): Flow<Int> =
        db.surveyAnswerDao().getAnswerCountForUser(userId)

    suspend fun saveSurveyAnswer(userId: Long, questionId: Long, answer: String) {
        db.surveyAnswerDao().insertAnswer(SurveyAnswer(userId = userId, questionId = questionId, answer = answer))
    }

    suspend fun getQuizAnsweredCountForUser(userId: Long): Int =
        db.quizResultDao().getTotalQuizAnsweredByUser(userId)

    suspend fun getAllAnswersForUser(userId: Long) =
        db.surveyAnswerDao().getAllAnswersForUser(userId)

    // Quiz
    suspend fun buildQuizSession(quizTakerId: Long, groupIds: List<Long>, count: Int = 30): List<QuizQuestion> {
        val eligibleUsers = if (groupIds.isEmpty() || 0L in groupIds) {
            getAllUsersWithMinAnswers(quizTakerId)
        } else {
            groupIds.flatMap { gId ->
                db.userDao().getQuizEligibleUsersInGroup(gId).filter { it.id != quizTakerId }
            }.distinctBy { it.id }
        }
        if (eligibleUsers.isEmpty()) return emptyList()

        val questions = mutableListOf<QuizQuestion>()
        val shuffledUsers = eligibleUsers.shuffled()

        for (subjectUser in shuffledUsers) {
            val alreadyAttempted = db.quizResultDao()
                .getAlreadyAttemptedQuestionIds(quizTakerId, subjectUser.id).toSet()
            val answered = db.surveyQuestionDao()
                .getAnsweredQuestionsForUser(subjectUser.id, 50)
                .filter { it.id !in alreadyAttempted }

            for (q in answered.shuffled()) {
                val correctAnswer = db.surveyAnswerDao()
                    .getAnswerForUserQuestion(subjectUser.id, q.id) ?: continue
                val enOpts = parseOptions(q.optionsJson)
                val esOpts = parseOptions(q.optionsJsonEs).takeIf { it.size == enOpts.size } ?: enOpts
                val shuffledPairs = enOpts.zip(esOpts).shuffled()
                val options = shuffledPairs.map { it.first }
                val optionsEs = shuffledPairs.map { it.second }
                questions.add(QuizQuestion(q, subjectUser, options, optionsEs, correctAnswer))
                if (questions.size >= count) break
            }
            if (questions.size >= count) break
        }
        return questions.shuffled()
    }

    suspend fun saveQuizResults(results: List<QuizResult>) =
        db.quizResultDao().insertResults(results)

    // Connections
    suspend fun getAllConnectionEntries(allUsers: List<User>): List<ConnectionEntry> {
        val entries = mutableListOf<ConnectionEntry>()
        val userMap = allUsers.associateBy { it.id }

        for (i in allUsers.indices) {
            for (j in i + 1 until allUsers.size) {
                val a = allUsers[i]
                val b = allUsers[j]
                val aToB = getDirectScore(a.id, b.id)
                val bToA = getDirectScore(b.id, a.id)
                if (aToB > 0.0 || bToA > 0.0) {
                    val mutual = when {
                        aToB > 0.0 && bToA > 0.0 -> (aToB + bToA) / 2.0
                        else -> maxOf(aToB, bToA)
                    }
                    entries.add(ConnectionEntry(a, b, aToB, bToA, mutual))
                }
            }
        }
        return entries.sortedByDescending { it.mutualScore }
    }

    private suspend fun getAllUsersWithMinAnswers(excludeId: Long): List<User> {
        val all = db.userDao().getAllUsers().first()
        return all.filter { user ->
            user.id != excludeId &&
            db.surveyAnswerDao().getAnswerCountForUserSync(user.id) >= 15
        }
    }

    private suspend fun getDirectScore(fromId: Long, toId: Long): Double {
        val scores = db.quizResultDao().getOutgoingConnectionScores(fromId).first()
        val score = scores.find { it.userId == toId } ?: return 0.0
        return if (score.totalAttempts == 0) 0.0
        else score.correctAttempts.toDouble() / score.totalAttempts.toDouble() * 100.0
    }

    // Admin
    suspend fun getAdminPin(): String =
        db.appConfigDao().getValue("admin_pin") ?: "1234"

    suspend fun setAdminPin(pin: String) =
        db.appConfigDao().setValue(AppConfig("admin_pin", pin))

    suspend fun verifyAdminPin(pin: String): Boolean =
        getAdminPin() == pin

    suspend fun getLanguage(): String = db.appConfigDao().getValue("app_language") ?: "en"
    suspend fun setLanguage(lang: String) = db.appConfigDao().setValue(AppConfig("app_language", lang))

    // Active user session
    suspend fun getActiveUserId(): Long? =
        db.appConfigDao().getValue("active_user_id")?.toLongOrNull()

    suspend fun setActiveUserId(id: Long) =
        db.appConfigDao().setValue(AppConfig("active_user_id", id.toString()))

    suspend fun clearActiveUser() =
        db.appConfigDao().deleteKey("active_user_id")

    private fun parseOptions(json: String): List<String> =
        try {
            Json.decodeFromString(ListSerializer(String.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }
}
