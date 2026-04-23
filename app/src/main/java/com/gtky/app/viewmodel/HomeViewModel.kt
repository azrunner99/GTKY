package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class NoUser(
        val error: String? = null,
        val prefillFirstName: String = "",
        val prefillLastName: String = ""
    ) : HomeUiState()
    data class DuplicateName(
        val firstName: String,
        val lastName: String,
        val collidingUser: User
    ) : HomeUiState()
    data class PickGroups(val user: User, val groups: List<Group>) : HomeUiState()
    data class UserSelected(
        val user: User,
        val answerCount: Int,
        val readyCount: Int = 0,
        val renameError: String? = null
    ) : HomeUiState()
}

class HomeViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _readyUsersByGroup = MutableStateFlow<Map<Long, List<User>>>(emptyMap())
    val readyUsersByGroup: StateFlow<Map<Long, List<User>>> = _readyUsersByGroup.asStateFlow()

    private var answerCountJob: Job? = null

    init {
        viewModelScope.launch {
            repo.getAllUsers().collect { users ->
                _allUsers.value = users
                refreshReadyUsersByGroup()
            }
        }
        viewModelScope.launch {
            repo.getAllGroups().collect { g ->
                _groups.value = g
                refreshReadyUsersByGroup()
            }
        }
        loadActiveUser()
    }

    private fun refreshReadyUsersByGroup() {
        val activeUserId = (_uiState.value as? HomeUiState.UserSelected)?.user?.id ?: return
        viewModelScope.launch {
            _readyUsersByGroup.value = repo.getReadyUsersByGroup(activeUserId, _groups.value)
        }
    }

    private fun loadActiveUser() {
        viewModelScope.launch {
            val userId = repo.getActiveUserId()
            if (userId != null) {
                val user = repo.getUserById(userId)
                if (user != null) {
                    transitionToUserSelected(user)
                } else {
                    _uiState.value = HomeUiState.NoUser()
                }
            } else {
                _uiState.value = HomeUiState.NoUser()
            }
        }
    }

    fun selectExistingUser(user: User) {
        viewModelScope.launch {
            repo.setActiveUserId(user.id)
            transitionToUserSelected(user)
        }
    }

    fun createAndSelectUser(name: String) {
        if (name.isBlank()) {
            _uiState.value = HomeUiState.NoUser("Name cannot be empty")
            return
        }
        viewModelScope.launch {
            val existing = repo.getUserByName(name.trim())
            if (existing != null) {
                val parts = name.trim().split(" ", limit = 2)
                _uiState.value = HomeUiState.DuplicateName(
                    firstName = parts.getOrElse(0) { "" },
                    lastName = parts.getOrElse(1) { "" },
                    collidingUser = existing
                )
                return@launch
            }
            val id = repo.createUser(name)
            repo.setActiveUserId(id)
            val user = repo.getUserById(id)!!
            val groups = repo.getAllGroups().first()
            if (groups.isNotEmpty()) {
                _uiState.value = HomeUiState.PickGroups(user, groups)
            } else {
                transitionToUserSelected(user)
            }
        }
    }

    fun cancelDuplicate(firstName: String, lastName: String) {
        _uiState.value = HomeUiState.NoUser(prefillFirstName = firstName, prefillLastName = lastName)
    }

    fun finishGroupSelection(user: User, selectedGroupIds: Set<Long>) {
        viewModelScope.launch {
            selectedGroupIds.forEach { groupId -> repo.joinGroup(user.id, groupId) }
            transitionToUserSelected(user)
        }
    }

    private fun transitionToUserSelected(user: User) {
        answerCountJob?.cancel()
        answerCountJob = viewModelScope.launch {
            repo.getAnswerCountForUser(user.id).collect { c ->
                val readyCount = repo.getReadyUserCount(user.id)
                _uiState.value = HomeUiState.UserSelected(user, c, readyCount)
            }
        }
        viewModelScope.launch {
            _readyUsersByGroup.value = repo.getReadyUsersByGroup(user.id, _groups.value)
        }
    }

    fun renameUser(userId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val state = _uiState.value as? HomeUiState.UserSelected ?: return@launch
            val existing = repo.getUserByName(newName.trim())
            if (existing != null && existing.id != userId) {
                _uiState.value = state.copy(renameError = "That name is already taken")
                return@launch
            }
            repo.renameUser(userId, newName)
            val updatedUser = repo.getUserById(userId) ?: return@launch
            transitionToUserSelected(updatedUser)
        }
    }

    fun clearRenameError() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(renameError = null)
    }

    fun signOut() {
        viewModelScope.launch {
            repo.clearActiveUser()
            answerCountJob?.cancel()
            _uiState.value = HomeUiState.NoUser()
        }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
