package com.gtky.app.data.repository

import com.gtky.app.Constants
import com.gtky.app.data.dao.ConnectionScore
import com.gtky.app.data.database.GTKYDatabase
import com.gtky.app.data.entity.*
import com.gtky.app.util.normalizeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

// Internal top-level functions — testable without DB access

internal fun resolveEligibleSubjects(
    quizTakerId: Long,
    groupIds: List<Long>,
    subjectUserIds: List<Long>,
    allUsers: List<User>,
    answerCounts: Map<Long, Int>,
    groupMemberships: Map<Long, List<Long>>  // groupId -> list of userIds
): List<User> {
    val threshold = Constants.QUIZ_UNLOCK_THRESHOLD
    return when {
        subjectUserIds.isNotEmpty() -> {
            val subjectSet = subjectUserIds.toSet()
            allUsers.filter { it.id != quizTakerId && it.id in subjectSet && (answerCounts[it.id] ?: 0) >= threshold }
        }
        groupIds.isEmpty() || 0L in groupIds -> {
            allUsers.filter { it.id != quizTakerId && (answerCounts[it.id] ?: 0) >= threshold }
        }
        else -> {
            val eligibleIds = groupIds.flatMap { gId -> groupMemberships[gId] ?: emptyList() }.toSet()
            allUsers.filter { it.id != quizTakerId && it.id in eligibleIds && (answerCounts[it.id] ?: 0) >= threshold }
                .distinctBy { it.id }
        }
    }
}

internal fun buildQuizSessionFromPools(
    subjectPools: MutableMap<User, MutableList<QuizQuestion>>,
    timesQuizzed: Map<Long, Int>,
    count: Int,
    rng: Random = Random
): List<QuizQuestion> {
    val questions = mutableListOf<QuizQuestion>()
    while (questions.size < count && subjectPools.isNotEmpty()) {
        val entries = subjectPools.entries.toList()
        val weights = entries.map { (user, _) -> 1.0 / (1.0 + (timesQuizzed[user.id] ?: 0).toDouble()) }
        val totalWeight = weights.sum()
        var pick = rng.nextDouble() * totalWeight
        var chosenKey: User? = null
        for ((entry, weight) in entries.zip(weights)) {
            pick -= weight
            if (pick <= 0.0) { chosenKey = entry.key; break }
        }
        if (chosenKey == null) chosenKey = entries.last().key
        val pool = subjectPools[chosenKey]!!
        questions.add(pool.removeFirst())
        if (pool.isEmpty()) subjectPools.remove(chosenKey)
    }
    return questions
}

data class QuizQuestion(
    val question: SurveyQuestion,
    val subjectUser: User,
    val options: List<String>,
    val optionsEs: List<String>,
    val correctAnswer: String
)

data class SubjectPool(val user: User, val availableCount: Int)

data class ConnectionEntry(
    val userA: User,
    val userB: User,
    val scoreAtoB: Double,
    val scoreBtoA: Double,
    val mutualScore: Double
)

enum class MatchKind {
    EXACT,          // normalized names are identical
    PREFIX_LONGER,  // existing user's last name starts with what was typed ("Alex S" typed, "Alex Smith" exists)
    PREFIX_SHORTER, // typed last name starts with existing user's ("Alex Smith" typed, "Alex S" exists)
    SAME_INITIAL    // same first name, different last names, same leading letter
}

data class SimilarNameMatch(val user: User, val matchKind: MatchKind)

internal fun classifyNameMatch(
    typedFirst: String,
    typedLast: String,
    existingFirst: String,
    existingLast: String
): MatchKind? {
    val tf = typedFirst.lowercase()
    val tl = typedLast.lowercase()
    val ef = existingFirst.lowercase()
    val el = existingLast.lowercase()
    if (tf != ef) return null
    return when {
        el == tl -> MatchKind.EXACT
        tl.isNotEmpty() && el.isNotEmpty() && el.startsWith(tl) -> MatchKind.PREFIX_LONGER
        tl.isNotEmpty() && el.isNotEmpty() && tl.startsWith(el) -> MatchKind.PREFIX_SHORTER
        tl.isNotEmpty() && el.isNotEmpty() && tl[0] == el[0] -> MatchKind.SAME_INITIAL
        else -> null
    }
}

class GTKYRepository(val db: GTKYDatabase) {

    // Users
    fun getAllUsers(): Flow<List<User>> = db.userDao().getAllUsers()

    suspend fun getUserById(id: Long) = db.userDao().getUserById(id)

    suspend fun getUserByName(name: String) = db.userDao().getUserByName(name)

    suspend fun findSimilarNames(typedName: String): List<SimilarNameMatch> {
        val typed = normalizeName(typedName)
        if (typed.isBlank()) return emptyList()
        val typedParts = typed.split(" ", limit = 2)
        val typedFirst = typedParts.getOrNull(0)?.lowercase() ?: return emptyList()
        val typedLast = typedParts.getOrNull(1)?.lowercase() ?: ""

        val allUsers = db.userDao().getAllUsers().first()
        val matches = mutableListOf<SimilarNameMatch>()

        for (user in allUsers) {
            val existingParts = user.name.split(" ", limit = 2)
            val existingFirst = existingParts.getOrNull(0)?.lowercase() ?: continue
            val existingLast = existingParts.getOrNull(1)?.lowercase() ?: ""
            val kind = classifyNameMatch(typedFirst, typedLast, existingFirst, existingLast)
            if (kind != null) matches.add(SimilarNameMatch(user, kind))
        }

        return matches.sortedBy { match ->
            when (match.matchKind) {
                MatchKind.EXACT -> 0
                MatchKind.PREFIX_LONGER, MatchKind.PREFIX_SHORTER -> 1
                MatchKind.SAME_INITIAL -> 2
            }
        }
    }

    suspend fun createUser(name: String): Long = db.userDao().insertUser(User(name = normalizeName(name)))

    suspend fun deleteUser(user: User) {
        db.surveyAnswerDao().deleteAllAnswersForUser(user.id)
        db.quizResultDao().deleteResultsForUser(user.id)
        user.photoPath?.let { path ->
            try { java.io.File(path).delete() } catch (_: Exception) { }
        }
        db.userDao().deleteUser(user)
    }

    suspend fun renameUser(userId: Long, newName: String) =
        db.userDao().updateName(userId, normalizeName(newName))

    suspend fun setUserPhotoPath(userId: Long, path: String?) =
        db.userDao().updatePhotoPath(userId, path)

    suspend fun incrementPhotoPromptCount(userId: Long) =
        db.userDao().incrementPhotoPromptCount(userId)

    suspend fun markPhotoPromptOptOut(userId: Long) =
        db.userDao().setPhotoPromptOptOut(userId)

    suspend fun removeUserPhoto(userId: Long) {
        val user = db.userDao().getUserById(userId) ?: return
        user.photoPath?.let { java.io.File(it).delete() }
        db.userDao().updatePhotoPath(userId, null)
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
    suspend fun getUnansweredSurveyQuestions(userId: Long) =
        db.surveyQuestionDao().getUnansweredQuestionsForUser(userId)

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
    suspend fun buildQuizSession(
        quizTakerId: Long,
        groupIds: List<Long>,
        subjectUserIds: List<Long> = emptyList(),
        count: Int = 30
    ): List<QuizQuestion> {
        val eligibleUsers = getEligibleSubjects(quizTakerId, groupIds, subjectUserIds)
        if (eligibleUsers.isEmpty()) return emptyList()

        val subjectPools = mutableMapOf<User, MutableList<QuizQuestion>>()
        for (subjectUser in eligibleUsers) {
            val alreadyAttempted = db.quizResultDao()
                .getAlreadyAttemptedQuestionIds(quizTakerId, subjectUser.id).toSet()
            val answered = db.surveyQuestionDao()
                .getAnsweredQuestionsForUser(subjectUser.id, 50)
                .filter { it.id !in alreadyAttempted }
            if (answered.isEmpty()) continue

            val pool = mutableListOf<QuizQuestion>()
            for (q in answered.shuffled()) {
                val correctAnswer = db.surveyAnswerDao()
                    .getAnswerForUserQuestion(subjectUser.id, q.id) ?: continue
                val enOpts = parseOptions(q.optionsJson)
                val esOpts = parseOptions(q.optionsJsonEs).takeIf { it.size == enOpts.size } ?: enOpts
                val shuffledPairs = enOpts.zip(esOpts).shuffled()
                pool.add(QuizQuestion(q, subjectUser, shuffledPairs.map { it.first }, shuffledPairs.map { it.second }, correctAnswer))
            }
            if (pool.isNotEmpty()) subjectPools[subjectUser] = pool
        }
        if (subjectPools.isEmpty()) return emptyList()

        val timesQuizzed = subjectPools.keys.associate { user ->
            user.id to db.quizResultDao().getAttemptCount(quizTakerId, user.id)
        }
        return buildQuizSessionFromPools(subjectPools, timesQuizzed, count)
    }

    fun observeTotalAnswers(): Flow<Int> = db.surveyAnswerDao().getTotalAnswerCountFlow()

    fun getQuizzableUsers(excludeUserId: Long): Flow<List<User>> =
        combine(getAllUsers(), db.surveyAnswerDao().getTotalAnswerCountFlow()) { users, _ -> users }
            .map { users ->
                users.filter { user ->
                    user.id != excludeUserId &&
                    db.surveyAnswerDao().getAnswerCountForUserSync(user.id) >= Constants.QUIZ_UNLOCK_THRESHOLD
                }
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

    suspend fun getReadyUsersByGroup(excludingUserId: Long, groups: List<Group>): Map<Long, List<User>> {
        val allReady = getAllUsersWithMinAnswers(excludingUserId)
        val result = mutableMapOf<Long, List<User>>(0L to allReady)
        for (group in groups) {
            val inGroup = db.userDao().getUsersInGroup(group.id).first()
            result[group.id] = inGroup.filter { u -> allReady.any { it.id == u.id } }
        }
        return result
    }

    suspend fun getAlmostReadyUserCount(excludingUserId: Long): Int {
        val half = Constants.QUIZ_UNLOCK_THRESHOLD / 2
        val users = db.userDao().getAllUsers().first()
        return users.count { it.id != excludingUserId &&
            db.surveyAnswerDao().getAnswerCountForUserSync(it.id) in half until Constants.QUIZ_UNLOCK_THRESHOLD }
    }

    suspend fun getReadyUserCount(excludingUserId: Long): Int {
        val users = db.userDao().getAllUsers().first()
        return users.count { it.id != excludingUserId &&
            db.surveyAnswerDao().getAnswerCountForUserSync(it.id) >= Constants.QUIZ_UNLOCK_THRESHOLD }
    }

    private suspend fun getAllUsersWithMinAnswers(excludeId: Long): List<User> {
        val all = db.userDao().getAllUsers().first()
        return all.filter { user ->
            user.id != excludeId &&
            db.surveyAnswerDao().getAnswerCountForUserSync(user.id) >= Constants.QUIZ_UNLOCK_THRESHOLD
        }
    }

    suspend fun loadAllSubjectPools(quizTakerId: Long): List<SubjectPool> {
        val users = db.userDao().getAllUsers().first()
        val counts = users.associate { it.id to db.surveyAnswerDao().getAnswerCountForUserSync(it.id) }
        val eligible = users.filter { it.id != quizTakerId && (counts[it.id] ?: 0) >= Constants.QUIZ_UNLOCK_THRESHOLD }
        return eligible.map { subject ->
            val alreadyAttempted = db.quizResultDao()
                .getAlreadyAttemptedQuestionIds(quizTakerId, subject.id).toSet()
            val available = db.surveyQuestionDao()
                .getAnsweredQuestionsForUser(subject.id, 50)
                .count { it.id !in alreadyAttempted }
            SubjectPool(subject, available)
        }
    }

    suspend fun getGroupMembersMap(groupIds: List<Long>): Map<Long, Set<Long>> =
        groupIds.associate { gId ->
            gId to db.userDao().getUsersInGroup(gId).first().map { it.id }.toSet()
        }

    fun filterSubjectPools(
        pools: List<SubjectPool>,
        groupIds: List<Long>,
        subjectUserIds: List<Long>,
        groupMembers: Map<Long, Set<Long>>
    ): List<SubjectPool> = when {
        subjectUserIds.isNotEmpty() -> {
            val subjectSet = subjectUserIds.toSet()
            pools.filter { it.user.id in subjectSet }
        }
        groupIds.isEmpty() || 0L in groupIds -> pools
        else -> {
            val eligibleIds = groupIds.flatMap { gId -> groupMembers[gId] ?: emptySet() }.toSet()
            pools.filter { it.user.id in eligibleIds }
        }
    }

    private suspend fun getEligibleSubjects(
        quizTakerId: Long,
        groupIds: List<Long>,
        subjectUserIds: List<Long>
    ): List<User> {
        val allUsers = db.userDao().getAllUsers().first()
        val answerCounts = allUsers.associate { it.id to db.surveyAnswerDao().getAnswerCountForUserSync(it.id) }
        val groupMemberships: Map<Long, List<Long>> =
            if (subjectUserIds.isEmpty() && groupIds.isNotEmpty() && 0L !in groupIds) {
                groupIds.associate { gId -> gId to db.userDao().getUsersInGroup(gId).first().map { it.id } }
            } else emptyMap()
        return resolveEligibleSubjects(quizTakerId, groupIds, subjectUserIds, allUsers, answerCounts, groupMemberships)
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

    suspend fun getAdminPinIsDefault(): Boolean =
        db.appConfigDao().getValue("admin_pin_is_default") == "true"

    suspend fun setAdminPin(pin: String) {
        db.appConfigDao().setValue(AppConfig("admin_pin", pin))
        db.appConfigDao().deleteKey("admin_pin_is_default")
    }

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
