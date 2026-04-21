package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class NoUser(val error: String? = null) : HomeUiState()
    data class PickGroups(val user: User, val groups: List<Group>) : HomeUiState()
    data class UserSelected(val user: User, val answerCount: Int) : HomeUiState()
}

class HomeViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllUsers().collect { users -> _allUsers.value = users }
        }
        viewModelScope.launch {
            repo.getAllGroups().collect { g -> _groups.value = g }
        }
        loadActiveUser()
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
                _uiState.value = HomeUiState.NoUser("That name is already taken")
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

    fun finishGroupSelection(user: User, selectedGroupIds: Set<Long>) {
        viewModelScope.launch {
            selectedGroupIds.forEach { groupId -> repo.joinGroup(user.id, groupId) }
            transitionToUserSelected(user)
        }
    }

    private fun transitionToUserSelected(user: User) {
        viewModelScope.launch {
            repo.getAnswerCountForUser(user.id).collect { c ->
                _uiState.value = HomeUiState.UserSelected(user, c)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.clearActiveUser()
            _uiState.value = HomeUiState.NoUser()
        }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
