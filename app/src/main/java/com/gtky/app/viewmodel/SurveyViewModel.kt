package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.Constants
import com.gtky.app.data.entity.SurveyQuestion
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

data class SurveyUiState(
    val currentQuestion: SurveyQuestion? = null,
    val options: List<String> = emptyList(),
    val optionsEs: List<String> = emptyList(),
    val answeredCount: Int = 0,
    val totalAnswered: Int = 0,
    val selectedAnswer: String? = null,
    val isLoading: Boolean = true,
    val isDone: Boolean = false,
    val canQuit: Boolean = false,
    val justUnlockedQuiz: Boolean = false
)

class SurveyViewModel(private val repo: GTKYRepository, private val userId: Long) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    private val pendingQuestions = ArrayDeque<SurveyQuestion>()

    init {
        viewModelScope.launch {
            var prevCount = -1
            repo.getAnswerCountForUser(userId).collect { count ->
                val justUnlocked = prevCount != -1 &&
                    count == Constants.QUIZ_UNLOCK_THRESHOLD &&
                    prevCount < Constants.QUIZ_UNLOCK_THRESHOLD
                prevCount = count
                _uiState.update {
                    it.copy(
                        totalAnswered = count,
                        canQuit = count >= Constants.QUIZ_UNLOCK_THRESHOLD,
                        justUnlockedQuiz = if (justUnlocked) true else it.justUnlockedQuiz
                    )
                }
            }
        }
        viewModelScope.launch {
            val questions = repo.getUnansweredSurveyQuestions(userId)
            if (questions.isEmpty()) {
                _uiState.update { it.copy(isDone = true, isLoading = false) }
                return@launch
            }
            pendingQuestions.addAll(questions)
            showNext()
        }
    }

    private fun showNext() {
        if (pendingQuestions.isEmpty()) {
            _uiState.update { it.copy(isDone = true, isLoading = false) }
            return
        }
        val q = pendingQuestions.removeFirst()
        val options = parseOptions(q.optionsJson)
        val optionsEs = parseOptions(q.optionsJsonEs).takeIf { it.size == options.size } ?: options
        _uiState.update {
            it.copy(
                currentQuestion = q,
                options = options,
                optionsEs = optionsEs,
                selectedAnswer = null,
                isLoading = false
            )
        }
    }

    fun skipQuestion() = showNext()

    fun submitAnswer(answer: String) {
        val q = _uiState.value.currentQuestion ?: return
        _uiState.update { it.copy(selectedAnswer = answer) }
        viewModelScope.launch {
            repo.saveSurveyAnswer(userId, q.id, answer)
            showNext()
        }
    }

    fun clearUnlockToast() {
        _uiState.update { it.copy(justUnlockedQuiz = false) }
    }

    private fun parseOptions(json: String): List<String> =
        try {
            Json.decodeFromString(ListSerializer(String.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }

    class Factory(private val repo: GTKYRepository, private val userId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SurveyViewModel(repo, userId) as T
    }
}
