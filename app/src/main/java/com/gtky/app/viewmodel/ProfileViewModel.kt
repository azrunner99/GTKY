package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.SurveyQuestion
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import com.gtky.app.util.forQuiz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val name: String = "",
    val answers: List<Pair<String, String>> = emptyList()
)

class ProfileViewModel(
    private val repo: GTKYRepository,
    private val userId: Long,
    private val language: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = repo.getUserById(userId) ?: return@launch
            val rawAnswers = repo.getAllAnswersForUser(userId)
            val pairs = rawAnswers.mapNotNull { answer ->
                val q = repo.db.surveyQuestionDao().getQuestionById(answer.questionId) ?: return@mapNotNull null
                val template = if (language == "es" && q.questionTemplateEs.isNotEmpty())
                    q.questionTemplateEs else q.questionTemplate
                val displayAnswer = translateAnswer(q, answer.answer, language)
                forQuiz(template, user.name) to displayAnswer
            }
            _uiState.value = ProfileUiState(isLoading = false, user = user, name = user.name, answers = pairs)
        }
    }

    private fun translateAnswer(q: SurveyQuestion, englishAnswer: String, lang: String): String {
        if (lang != "es" || q.optionsJsonEs.isEmpty()) return englishAnswer
        val enOpts = parseOptions(q.optionsJson)
        val esOpts = parseOptions(q.optionsJsonEs)
        if (enOpts.size != esOpts.size) return englishAnswer
        val idx = enOpts.indexOf(englishAnswer)
        return if (idx >= 0) esOpts[idx] else englishAnswer
    }

    private fun parseOptions(json: String): List<String> = try {
        Json.decodeFromString(ListSerializer(String.serializer()), json)
    } catch (e: Exception) { emptyList() }

    class Factory(
        private val repo: GTKYRepository,
        private val userId: Long,
        private val language: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repo, userId, language) as T
    }
}
