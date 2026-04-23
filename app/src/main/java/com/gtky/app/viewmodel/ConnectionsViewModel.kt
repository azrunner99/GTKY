package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.repository.ConnectionEntry
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionScope { MINE, EVERYONE }
enum class ConnectionDirection { MUTUAL, ONE_WAY }

data class ConnectionsUiState(
    val isLoading: Boolean = true,
    val connections: List<ConnectionEntry> = emptyList(),
    val scope: ConnectionScope = ConnectionScope.MINE,
    val direction: ConnectionDirection = ConnectionDirection.MUTUAL,
    val activeUserId: Long? = null
)

class ConnectionsViewModel(private val repo: GTKYRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionsUiState())
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
    }

    fun loadConnections() {
        viewModelScope.launch {
            val activeUserId = repo.getActiveUserId()
            repo.getAllUsers().collect { users ->
                val entries = repo.getAllConnectionEntries(users)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        connections = entries,
                        activeUserId = activeUserId
                    )
                }
            }
        }
    }

    fun setScope(scope: ConnectionScope) {
        _uiState.update { it.copy(scope = scope) }
    }

    fun setDirection(direction: ConnectionDirection) {
        _uiState.update { it.copy(direction = direction) }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionsViewModel(repo) as T
    }
}
