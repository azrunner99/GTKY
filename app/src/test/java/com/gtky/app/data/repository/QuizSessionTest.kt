package com.gtky.app.data.repository

import com.gtky.app.data.entity.QuestionType
import com.gtky.app.data.entity.SurveyQuestion
import com.gtky.app.data.entity.User
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QuizSessionTest {

    private fun makeUser(id: Long, name: String = "User$id") = User(id = id, name = name)

    private fun makeQuestion(id: Long, subjectUser: User) = QuizQuestion(
        question = SurveyQuestion(
            id = id,
            questionTemplate = "Q$id?",
            type = QuestionType.MULTIPLE_CHOICE,
            optionsJson = "[\"A\",\"B\",\"C\",\"D\"]",
            category = "test"
        ),
        subjectUser = subjectUser,
        options = listOf("A", "B", "C", "D"),
        optionsEs = listOf("A", "B", "C", "D"),
        correctAnswer = "A"
    )

    private fun makePool(user: User, count: Int, startId: Long = 1L): MutableList<QuizQuestion> =
        (startId until startId + count).map { makeQuestion(it, user) }.toMutableList()

    @Test
    fun distribution_fiveSubjectsEachWith20Questions_allAppear4To8Times() {
        val users = (1L..5L).map { makeUser(it) }
        val totalCounts = users.associate { it.id to 0 }.toMutableMap()

        // Aggregate 10 seeded runs (300 picks total) for a deterministic distribution check
        for (seed in 0L until 10L) {
            val pools = mutableMapOf<User, MutableList<QuizQuestion>>()
            users.forEachIndexed { i, user -> pools[user] = makePool(user, 20, startId = i * 20L + 1) }
            val timesQuizzed = users.associate { it.id to 0 }
            val result = buildQuizSessionFromPools(pools, timesQuizzed, 30, Random(seed))
            assertEquals(30, result.size)
            result.forEach { q -> totalCounts[q.subjectUser.id] = totalCounts.getValue(q.subjectUser.id) + 1 }
        }

        // 10 runs × 30 picks = 300 total; 5 equal-weight subjects → 60 expected each, accept 40-80
        for (user in users) {
            val c = totalCounts.getValue(user.id)
            assertTrue("Expected 40-80 aggregate for user ${user.id}, got $c", c in 40..80)
        }
    }

    @Test
    fun underdogWeighting_subjectBAppearsAtLeast2xMoreThanA() {
        val userA = makeUser(1L, "A")
        val userB = makeUser(2L, "B")
        val pools = mutableMapOf(
            userA to makePool(userA, 20, 1L),
            userB to makePool(userB, 20, 21L)
        )
        val timesQuizzed = mapOf(1L to 10, 2L to 0)

        val result = buildQuizSessionFromPools(pools, timesQuizzed, 20)

        assertEquals(20, result.size)
        val countA = result.count { it.subjectUser.id == 1L }
        val countB = result.count { it.subjectUser.id == 2L }
        assertTrue("B ($countB) should appear at least 2x more than A ($countA)", countB >= countA * 2)
    }

    @Test
    fun poolExhaustion_returnsExactlyAvailableQuestions() {
        val userA = makeUser(1L)
        val userB = makeUser(2L)
        val pools = mutableMapOf(
            userA to makePool(userA, 3, 1L),
            userB to makePool(userB, 3, 4L)
        )
        val timesQuizzed = mapOf(1L to 0, 2L to 0)

        val result = buildQuizSessionFromPools(pools, timesQuizzed, 30)

        assertEquals(6, result.size)
        assertEquals(3, result.count { it.subjectUser.id == 1L })
        assertEquals(3, result.count { it.subjectUser.id == 2L })
    }

    @Test
    fun subjectOverride_ignoresGroupFilter() {
        val taker = makeUser(1L, "Me")
        val user42 = makeUser(42L, "Alex")
        val other = makeUser(99L, "Other")
        val allUsers = listOf(taker, user42, other)
        // answer counts all above threshold (8)
        val answerCounts = mapOf(1L to 10, 42L to 10, 99L to 10)
        // group 1 contains user 1 and user 99 — but NOT user 42
        val groupMemberships = mapOf(1L to listOf(1L, 99L))

        val result = resolveEligibleSubjects(
            quizTakerId = 1L,
            groupIds = listOf(1L),
            subjectUserIds = listOf(42L),
            allUsers = allUsers,
            answerCounts = answerCounts,
            groupMemberships = groupMemberships
        )

        assertEquals(1, result.size)
        assertEquals(42L, result[0].id)
    }

    @Test
    fun countParity_uncappedResultSizeEqualsPoolSizeSum() {
        val users = (1L..3L).map { makeUser(it) }
        val poolSizes = listOf(5, 8, 3)
        val pools = mutableMapOf<User, MutableList<QuizQuestion>>()
        users.forEachIndexed { i, user ->
            pools[user] = makePool(user, poolSizes[i], startId = (i * 10 + 1).toLong())
        }
        val timesQuizzed = users.associate { it.id to 0 }

        val result = buildQuizSessionFromPools(pools, timesQuizzed, Int.MAX_VALUE)

        assertEquals(poolSizes.sum(), result.size)
    }
}
