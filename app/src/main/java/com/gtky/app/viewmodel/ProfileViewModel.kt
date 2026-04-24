package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val photoPath: String? = null,
    val answerCount: Int = 0,
    val youAboutThemCorrect: Int = 0,
    val youAboutThemTotal: Int = 0,
    val themAboutYouCorrect: Int = 0,
    val themAboutYouTotal: Int = 0
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
            val activeUserId = repo.getActiveUserId()
            val answerCount = repo.db.surveyAnswerDao().getAnswerCountForUserSync(userId)

            val (youCorrect, youTotal, themCorrect, themTotal) = if (activeUserId != null) {
                val youResults = repo.db.quizResultDao()
                    .getResultsBetween(quizTakerId = activeUserId, subjectUserId = userId)
                val themResults = repo.db.quizResultDao()
                    .getResultsBetween(quizTakerId = userId, subjectUserId = activeUserId)
                listOf(
                    youResults.count { it.isCorrect },
                    youResults.size,
                    themResults.count { it.isCorrect },
                    themResults.size
                )
            } else listOf(0, 0, 0, 0)

            _uiState.value = ProfileUiState(
                isLoading = false,
                name = user.name,
                photoPath = user.photoPath,
                answerCount = answerCount,
                youAboutThemCorrect = youCorrect,
                youAboutThemTotal = youTotal,
                themAboutYouCorrect = themCorrect,
                themAboutYouTotal = themTotal
            )
        }
    }

    class Factory(
        private val repo: GTKYRepository,
        private val userId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(repo, userId) as T
    }
}
