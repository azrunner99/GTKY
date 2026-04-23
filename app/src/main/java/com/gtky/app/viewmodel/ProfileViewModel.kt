package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.repository.GTKYRepository
import com.gtky.app.util.forQuiz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val answers: List<Pair<String, String>> = emptyList()
)

class ProfileViewModel(
    private val repo: GTKYRepository,
    private val userId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = repo.getUserById(userId) ?: return@launch
            val rawAnswers = repo.getAllAnswersForUser(userId)
            val pairs = rawAnswers.mapNotNull { answer ->
                val q = repo.db.surveyQuestionDao().getQuestionById(answer.questionId)
                if (q != null) forQuiz(q.questionTemplate, user.name) to answer.answer else null
            }
            _uiState.value = ProfileUiState(isLoading = false, name = user.name, answers = pairs)
        }
    }

    class Factory(private val repo: GTKYRepository, private val userId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repo, userId) as T
    }
}
