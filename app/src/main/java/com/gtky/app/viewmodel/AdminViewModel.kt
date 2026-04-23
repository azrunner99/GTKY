package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UserAnswerDetail(
    val user: User,
    val answers: List<Pair<String, String>>
)

data class AdminUiState(
    val isAuthenticated: Boolean = false,
    val isPinDefault: Boolean = false,
    val mustChangePin: Boolean = false,
    val pinError: String? = null,
    val users: List<User> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedUserDetail: UserAnswerDetail? = null,
    val isLoading: Boolean = false,
    val pinChangeSuccess: Boolean = false
)

class AdminViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isPinDefault = repo.getAdminPinIsDefault()
            _uiState.update { it.copy(isPinDefault = isPinDefault) }
        }
    }

    fun authenticate(pin: String) {
        viewModelScope.launch {
            val valid = repo.verifyAdminPin(pin)
            if (valid) {
                val mustChange = repo.getAdminPinIsDefault()
                _uiState.update { it.copy(isAuthenticated = true, pinError = null, mustChangePin = mustChange) }
                loadUsers()
            } else {
                _uiState.update { it.copy(pinError = "Incorrect PIN") }
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            repo.getAllUsers().collect { users ->
                _uiState.update { it.copy(users = users) }
            }
        }
        viewModelScope.launch {
            repo.getAllGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { repo.createGroup(name) }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch { repo.deleteGroup(group) }
    }

    fun loadUserAnswers(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val answers = repo.getAllAnswersForUser(user.id)
            val questions = answers.mapNotNull { answer ->
                val q = repo.db.surveyQuestionDao().getQuestionById(answer.questionId)
                if (q != null) {
                    val displayQ = q.questionTemplate.replace("[NAME]", user.name)
                    displayQ to answer.answer
                } else null
            }
            _uiState.update {
                it.copy(
                    selectedUserDetail = UserAnswerDetail(user, questions),
                    isLoading = false
                )
            }
        }
    }

    fun clearSelectedUser() {
        _uiState.update { it.copy(selectedUserDetail = null) }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            repo.deleteUser(user)
            _uiState.update { it.copy(selectedUserDetail = null) }
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmPin: String) {
        viewModelScope.launch {
            when {
                !repo.verifyAdminPin(currentPin) ->
                    _uiState.update { it.copy(pinError = "Current PIN is incorrect") }
                newPin.length < 4 ->
                    _uiState.update { it.copy(pinError = "PIN must be at least 4 digits") }
                newPin != confirmPin ->
                    _uiState.update { it.copy(pinError = "New PINs do not match") }
                else -> {
                    repo.setAdminPin(newPin)
                    _uiState.update { it.copy(pinChangeSuccess = true, pinError = null, mustChangePin = false, isPinDefault = false) }
                }
            }
        }
    }

    fun clearPinChangeStatus() {
        _uiState.update { it.copy(pinChangeSuccess = false, pinError = null) }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(repo) as T
    }
}
