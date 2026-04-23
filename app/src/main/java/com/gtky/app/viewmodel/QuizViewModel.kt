package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.Constants
import com.gtky.app.data.entity.QuizResult
import com.gtky.app.data.repository.GTKYRepository
import com.gtky.app.data.repository.QuizQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizUiState(
    val isLoading: Boolean = true,
    val noEligibleUsers: Boolean = false,
    val closeCount: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val isAnswerRevealed: Boolean = false,
    val correctCount: Int = 0,
    val answeredCount: Int = 0,
    val canFinish: Boolean = false,
    val isFinished: Boolean = false,
    val sessionResults: List<QuizResult> = emptyList(),
    val justSkippedPerson: String? = null
) {
    val currentQuestion get() = questions.getOrNull(currentIndex)
    val progress get() = if (questions.isEmpty()) 0f else currentIndex.toFloat() / questions.size
}

class QuizViewModel(
    private val repo: GTKYRepository,
    private val quizTakerId: Long,
    private val groupIds: List<Long>
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val pendingResults = mutableListOf<QuizResult>()

    init {
        loadQuiz()
    }

    private fun loadQuiz() {
        viewModelScope.launch {
            val questions = repo.buildQuizSession(quizTakerId, groupIds, 30)
            if (questions.isEmpty()) {
                val closeCount = repo.getAlmostReadyUserCount(quizTakerId)
                _uiState.update { it.copy(isLoading = false, noEligibleUsers = true, closeCount = closeCount) }
            } else {
                _uiState.update { it.copy(isLoading = false, questions = questions) }
            }
        }
    }

    fun selectAnswer(answer: String) {
        val state = _uiState.value
        val q = state.currentQuestion ?: return
        if (state.isAnswerRevealed) return

        val isCorrect = answer == q.correctAnswer
        val result = QuizResult(
            quizTakerId = quizTakerId,
            subjectUserId = q.subjectUser.id,
            questionId = q.question.id,
            givenAnswer = answer,
            isCorrect = isCorrect
        )
        pendingResults.add(result)

        val newAnswered = state.answeredCount + 1
        _uiState.update {
            it.copy(
                selectedAnswer = answer,
                isAnswerRevealed = true,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                answeredCount = newAnswered,
                canFinish = newAnswered >= Constants.QUIZ_MIN_QUESTIONS_BEFORE_FINISH,
                sessionResults = pendingResults.toList()
            )
        }
    }

    fun skipPerson() {
        val state = _uiState.value
        val skippedName = state.currentQuestion?.subjectUser?.name
        val currentSubjectId = state.currentQuestion?.subjectUser?.id ?: return
        val remaining = state.questions.drop(state.currentIndex + 1)
            .filter { it.subjectUser.id != currentSubjectId }
        val kept = state.questions.take(state.currentIndex) + remaining
        if (kept.size <= state.currentIndex) {
            finishQuiz()
        } else {
            _uiState.update {
                it.copy(
                    questions = kept,
                    selectedAnswer = null,
                    isAnswerRevealed = false,
                    justSkippedPerson = skippedName
                )
            }
        }
    }

    fun clearSkipToast() {
        _uiState.update { it.copy(justSkippedPerson = null) }
    }

    fun nextQuestion() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.questions.size) {
            finishQuiz()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    selectedAnswer = null,
                    isAnswerRevealed = false
                )
            }
        }
    }

    fun finishQuiz() {
        viewModelScope.launch {
            repo.saveQuizResults(pendingResults)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    class Factory(
        private val repo: GTKYRepository,
        private val quizTakerId: Long,
        private val groupIds: List<Long>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuizViewModel(repo, quizTakerId, groupIds) as T
    }
}
