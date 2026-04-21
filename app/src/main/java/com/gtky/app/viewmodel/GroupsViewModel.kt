package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GroupWithState(
    val group: Group,
    val memberCount: Int,
    val isMember: Boolean
)

data class GroupsUiState(
    val groups: List<GroupWithState> = emptyList(),
    val activeUserId: Long? = null,
    val activeUserName: String? = null,
    val isLoading: Boolean = true
)

class GroupsViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activeUserId = repo.getActiveUserId()
            val activeUser = activeUserId?.let { repo.getUserById(it) }
            _uiState.update { it.copy(activeUserId = activeUserId, activeUserName = activeUser?.name) }

            val membershipFlow = if (activeUserId != null)
                repo.getGroupIdsForUserFlow(activeUserId)
            else
                kotlinx.coroutines.flow.flowOf(emptyList())

            combine(repo.getAllGroups(), membershipFlow) { groups, myGroupIds ->
                val myGroupIdSet = myGroupIds.toSet()
                groups.map { group ->
                    val memberCount = repo.getUsersInGroup(group.id).first().size
                    GroupWithState(
                        group = group,
                        memberCount = memberCount,
                        isMember = group.id in myGroupIdSet
                    )
                }
            }.collect { withState ->
                _uiState.update { it.copy(groups = withState, isLoading = false) }
            }
        }
    }

    fun joinGroup(groupId: Long) {
        val userId = _uiState.value.activeUserId ?: return
        viewModelScope.launch { repo.joinGroup(userId, groupId) }
    }

    fun leaveGroup(groupId: Long) {
        val userId = _uiState.value.activeUserId ?: return
        viewModelScope.launch { repo.leaveGroup(userId, groupId) }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GroupsViewModel(repo) as T
    }
}
