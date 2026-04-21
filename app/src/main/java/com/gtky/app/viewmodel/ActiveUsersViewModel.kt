package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UserWithAnswerCount(val user: User, val answerCount: Int, val quizCount: Int, val isEligible: Boolean)

data class ActiveUsersUiState(
    val users: List<UserWithAnswerCount> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedGroupId: Long? = null,
    val activeUserId: Long? = null,
    val isLoading: Boolean = true
)

class ActiveUsersViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveUsersUiState())
    val uiState: StateFlow<ActiveUsersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeUserId = repo.getActiveUserId()
            _uiState.update { it.copy(activeUserId = activeUserId) }
            combine(repo.getAllUsers(), repo.getAllGroups()) { users, groups ->
                users to groups
            }.collect { (users, groups) ->
                val withCounts = users.map { user ->
                    val count = repo.getAnswerCountForUser(user.id).first()
                    val quizCount = repo.getQuizAnsweredCountForUser(user.id)
                    UserWithAnswerCount(user, count, quizCount, count >= 15)
                }
                _uiState.update {
                    it.copy(
                        users = withCounts,
                        groups = groups,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectGroup(groupId: Long?) {
        _uiState.update { it.copy(selectedGroupId = groupId) }
        if (groupId != null) {
            viewModelScope.launch {
                repo.getUsersInGroup(groupId).collect { users ->
                    val withCounts = users.map { user ->
                        val count = repo.getAnswerCountForUser(user.id).first()
                        val quizCount = repo.getQuizAnsweredCountForUser(user.id)
                        UserWithAnswerCount(user, count, quizCount, count >= 15)
                    }
                    _uiState.update { it.copy(users = withCounts) }
                }
            }
        } else {
            viewModelScope.launch {
                repo.getAllUsers().collect { users ->
                    val withCounts = users.map { user ->
                        val count = repo.getAnswerCountForUser(user.id).first()
                        val quizCount = repo.getQuizAnsweredCountForUser(user.id)
                        UserWithAnswerCount(user, count, quizCount, count >= 15)
                    }
                    _uiState.update { it.copy(users = withCounts) }
                }
            }
        }
    }

    fun joinGroup(groupId: Long) {
        val userId = _uiState.value.activeUserId ?: return
        viewModelScope.launch {
            repo.joinGroup(userId, groupId)
        }
    }

    fun leaveGroup(groupId: Long) {
        val userId = _uiState.value.activeUserId ?: return
        viewModelScope.launch {
            repo.leaveGroup(userId, groupId)
        }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActiveUsersViewModel(repo) as T
    }
}
