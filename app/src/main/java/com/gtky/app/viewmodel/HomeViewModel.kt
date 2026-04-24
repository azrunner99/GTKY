package com.gtky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gtky.app.data.entity.Group
import com.gtky.app.data.entity.User
import com.gtky.app.data.repository.GTKYRepository
import com.gtky.app.data.repository.IcebreakerData
import com.gtky.app.data.repository.MatchKind
import com.gtky.app.data.repository.SimilarNameMatch
import com.gtky.app.data.repository.SubjectPool
import com.gtky.app.util.normalizeName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FilterPreview(val availableQuestions: Int = -1)

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class NoUser(
        val error: String? = null,
        val prefillFirstName: String = "",
        val prefillLastName: String = "",
        val skipSimilarForName: String? = null
    ) : HomeUiState()
    data class DuplicateName(
        val firstName: String,
        val lastName: String,
        val collidingUser: User
    ) : HomeUiState()
    data class SimilarName(
        val typedFirstName: String,
        val typedLastName: String,
        val matches: List<SimilarNameMatch>
    ) : HomeUiState()
    data class PickGroups(val user: User, val groups: List<Group>) : HomeUiState()
    data class UserSelected(
        val user: User,
        val answerCount: Int,
        val readyCount: Int = 0,
        val renameError: String? = null,
        val showPhotoPrompt: Boolean = false,
        val showPhotoReplacement: Boolean = false,
        val showLanguagePrompt: Boolean = false
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

    private val _quizzableUsers = MutableStateFlow<List<User>>(emptyList())
    val quizzableUsers: StateFlow<List<User>> = _quizzableUsers.asStateFlow()

    private val _filterPreview = MutableStateFlow(FilterPreview())
    val filterPreview: StateFlow<FilterPreview> = _filterPreview.asStateFlow()

    private val _pendingQuizSubjectId = MutableStateFlow<Long?>(null)
    val pendingQuizSubjectId: StateFlow<Long?> = _pendingQuizSubjectId.asStateFlow()

    private val _pendingOpenQuizDialog = MutableStateFlow(false)
    val pendingOpenQuizDialog: StateFlow<Boolean> = _pendingOpenQuizDialog.asStateFlow()

    private val _icebreaker = MutableStateFlow<IcebreakerData?>(null)
    val icebreaker: StateFlow<IcebreakerData?> = _icebreaker.asStateFlow()

    private val _icebreakerAnswered = MutableStateFlow(false)
    val icebreakerAnswered: StateFlow<Boolean> = _icebreakerAnswered.asStateFlow()

    private var icebreakerSlotOffset = 0
    private var pendingIcebreakerAnswer: Pair<Long, String>? = null

    private val _allSubjectPools = MutableStateFlow<List<SubjectPool>>(emptyList())
    private val _groupMembersMap = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    private var answerCountJob: Job? = null
    private var readyUsersJob: Job? = null
    private var subjectPoolsJob: Job? = null
    private var quizzableUsersJob: Job? = null
    private var filterPreviewJob: Job? = null

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
        viewModelScope.launch {
            repo.observeTotalAnswers().collect {
                refreshReadyUsersByGroup()
                refreshSubjectPools()
            }
        }
        viewModelScope.launch {
            while (true) {
                icebreakerSlotOffset = 0
                _icebreakerAnswered.value = false
                _icebreaker.value = repo.getDailyIcebreakerQuestion(slotOffset = 0)
                delay(5 * 60 * 1000L)
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

    private fun refreshSubjectPools() {
        val activeUserId = (_uiState.value as? HomeUiState.UserSelected)?.user?.id ?: return
        viewModelScope.launch {
            _allSubjectPools.value = repo.loadAllSubjectPools(activeUserId)
            val groupIds = _groups.value.map { it.id }
            _groupMembersMap.value = repo.getGroupMembersMap(groupIds)
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
            val skipName = (_uiState.value as? HomeUiState.NoUser)?.skipSimilarForName
            val normalized = normalizeName(name)
            val shouldSkipSimilar = skipName != null && skipName == normalized

            val matches = if (shouldSkipSimilar) {
                // Bypass fuzzy matches but still enforce hard exact-match block.
                val exact = repo.getUserByName(normalized)
                if (exact != null) listOf(SimilarNameMatch(exact, MatchKind.EXACT)) else emptyList()
            } else {
                repo.findSimilarNames(normalized)
            }

            if (matches.isNotEmpty()) {
                val parts = normalized.split(" ", limit = 2)
                // Single exact match → existing DuplicateName UX (users already know it).
                if (matches.size == 1 && matches[0].matchKind == MatchKind.EXACT) {
                    _uiState.value = HomeUiState.DuplicateName(
                        firstName = parts.getOrElse(0) { "" },
                        lastName = parts.getOrElse(1) { "" },
                        collidingUser = matches[0].user
                    )
                    return@launch
                }
                // Any other match combination → new candidate-picker popup.
                _uiState.value = HomeUiState.SimilarName(
                    typedFirstName = parts.getOrElse(0) { "" },
                    typedLastName = parts.getOrElse(1) { "" },
                    matches = matches
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

    fun cancelSimilar(firstName: String, lastName: String) {
        _uiState.value = HomeUiState.NoUser(
            prefillFirstName = firstName,
            prefillLastName = lastName,
            skipSimilarForName = normalizeName("$firstName $lastName")
        )
    }

    fun finishGroupSelection(user: User, selectedGroupIds: Set<Long>) {
        viewModelScope.launch {
            selectedGroupIds.forEach { groupId -> repo.joinGroup(user.id, groupId) }
            transitionToUserSelected(user)
        }
    }

    private fun transitionToUserSelected(user: User) {
        pendingIcebreakerAnswer?.let { (questionId, answer) ->
            viewModelScope.launch {
                if (repo.getAnswerForUserQuestion(user.id, questionId) == null) {
                    repo.saveSurveyAnswer(user.id, questionId, answer)
                }
            }
            pendingIcebreakerAnswer = null
        }
        answerCountJob?.cancel()
        readyUsersJob?.cancel()
        subjectPoolsJob?.cancel()
        quizzableUsersJob?.cancel()

        if (user.preferredLanguage == null) {
            viewModelScope.launch {
                delay(400)
                val state = _uiState.value as? HomeUiState.UserSelected ?: return@launch
                _uiState.value = state.copy(showLanguagePrompt = true)
            }
        }

        val shouldPrompt = user.photoPath == null &&
                !user.photoPromptOptOut &&
                user.photoPromptCount < 3
        if (shouldPrompt) {
            viewModelScope.launch {
                repo.incrementPhotoPromptCount(user.id)
                kotlinx.coroutines.delay(1000)
                val state = _uiState.value as? HomeUiState.UserSelected ?: return@launch
                _uiState.value = state.copy(showPhotoPrompt = true)
            }
        }

        answerCountJob = viewModelScope.launch {
            combine(
                repo.getAnswerCountForUser(user.id),
                repo.observeTotalAnswers()
            ) { myCount, _ -> myCount }
                .collect { myCount ->
                    val readyCount = repo.getReadyUserCount(user.id)
                    _uiState.value = HomeUiState.UserSelected(user, myCount, readyCount)
                }
        }
        readyUsersJob = viewModelScope.launch {
            _readyUsersByGroup.value = repo.getReadyUsersByGroup(user.id, _groups.value)
        }
        subjectPoolsJob = viewModelScope.launch {
            _allSubjectPools.value = repo.loadAllSubjectPools(user.id)
            val groupIds = _groups.value.map { it.id }
            _groupMembersMap.value = repo.getGroupMembersMap(groupIds)
        }
        quizzableUsersJob = viewModelScope.launch {
            repo.getQuizzableUsers(user.id).collect { users ->
                _quizzableUsers.value = users
            }
        }
    }

    fun requestQuizWithSubject(userId: Long) {
        _pendingQuizSubjectId.value = userId
    }

    fun clearPendingQuizSubject() {
        _pendingQuizSubjectId.value = null
    }

    fun requestOpenQuizDialog() {
        _pendingOpenQuizDialog.value = true
    }

    fun clearPendingOpenQuizDialog() {
        _pendingOpenQuizDialog.value = false
    }

    fun updateFilterPreview(groupIds: List<Long>, personIds: Set<Long>) {
        filterPreviewJob?.cancel()
        filterPreviewJob = viewModelScope.launch {
            delay(200)
            val pools = _allSubjectPools.value
            val filtered = repo.filterSubjectPools(pools, groupIds, personIds.toList(), _groupMembersMap.value)
            val available = filtered.sumOf { it.availableCount }.coerceAtMost(30)
            _filterPreview.value = FilterPreview(available)
        }
    }

    fun renameUser(userId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val cur = _uiState.value as? HomeUiState.UserSelected ?: return@launch
            val normalized = normalizeName(newName)
            if (normalized.isBlank()) {
                _uiState.value = cur.copy(renameError = "Name cannot be empty")
                return@launch
            }
            val existing = repo.getUserByName(normalized)
            if (existing != null && existing.id != userId) {
                _uiState.value = cur.copy(renameError = "That name is already taken")
                return@launch
            }
            repo.renameUser(userId, newName)
            val updatedUser = repo.getUserById(userId) ?: return@launch
            _uiState.value = cur.copy(user = updatedUser, renameError = null)
        }
    }

    fun clearRenameError() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(renameError = null)
    }

    fun requestPhotoReplacement() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(showPhotoReplacement = true)
    }

    fun replacePhoto(context: android.content.Context, bitmap: android.graphics.Bitmap) {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        viewModelScope.launch {
            val path = com.gtky.app.util.PhotoStorage.saveAvatar(context, state.user.id, bitmap)
            repo.setUserPhotoPath(state.user.id, path)
            val updatedUser = repo.getUserById(state.user.id) ?: return@launch
            _uiState.value = state.copy(user = updatedUser, showPhotoReplacement = false)
        }
    }

    fun cancelPhotoReplacement() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(showPhotoReplacement = false)
    }

    fun dismissPhotoPrompt() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(showPhotoPrompt = false)
    }

    fun setPreferredLanguage(lang: String) {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        viewModelScope.launch {
            repo.setUserPreferredLanguage(state.user.id, lang)
            val updatedUser = repo.getUserById(state.user.id) ?: return@launch
            _uiState.value = state.copy(user = updatedUser, showLanguagePrompt = false)
        }
    }

    fun dismissLanguagePrompt() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        _uiState.value = state.copy(showLanguagePrompt = false)
    }

    fun savePhoto(context: android.content.Context, bitmap: android.graphics.Bitmap) {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        viewModelScope.launch {
            val path = com.gtky.app.util.PhotoStorage.saveAvatar(context, state.user.id, bitmap)
            repo.setUserPhotoPath(state.user.id, path)
            val updatedUser = repo.getUserById(state.user.id) ?: return@launch
            _uiState.value = state.copy(user = updatedUser, showPhotoPrompt = false)
        }
    }

    fun optOutOfPhotoPrompts() {
        val state = _uiState.value as? HomeUiState.UserSelected ?: return
        viewModelScope.launch {
            repo.markPhotoPromptOptOut(state.user.id)
            val updatedUser = repo.getUserById(state.user.id) ?: return@launch
            _uiState.value = state.copy(user = updatedUser, showPhotoPrompt = false)
        }
    }

    fun skipIcebreaker() {
        icebreakerSlotOffset++
        viewModelScope.launch {
            _icebreaker.value = repo.getDailyIcebreakerQuestion(slotOffset = icebreakerSlotOffset)
        }
    }

    fun recordIcebreakerAnswer(questionId: Long, answer: String) {
        pendingIcebreakerAnswer = Pair(questionId, answer)
        _icebreakerAnswered.value = true
    }

    fun signOut() {
        viewModelScope.launch {
            repo.clearActiveUser()
            answerCountJob?.cancel()
            readyUsersJob?.cancel()
            subjectPoolsJob?.cancel()
            quizzableUsersJob?.cancel()
            _icebreakerAnswered.value = false
            pendingIcebreakerAnswer = null
            _uiState.value = HomeUiState.NoUser()
        }
    }

    class Factory(private val repo: GTKYRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
