# GTKY — v3.1 Cleanup Pack

Small pack. Four issues found in a follow-up review of the v3 code. None of these block shipping — they're correctness and consistency gaps left over from Fixes 12–22.

Fix 15 (forgot-PIN recovery) was intentionally skipped and is not part of this pack.

**Apply in order.** Fixes 23 and 24 are real correctness bugs and should land first. 25 is an inconsistency that affects UX. 26 is light housekeeping.

**Ground rules (unchanged):**

- Every new user-facing string needs both English and Spanish via the existing `t("en", "es")` helper.
- No new dependencies.
- Keep the Compose + StateFlow + ViewModel architecture.
- No Room schema changes needed for this pack.
- One fix per branch, one commit per fix: `fix/NN-slug`. After each: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` must pass. Add a bullet to `CHANGELOG.md` under `## UX Fix Pack`.

---

## Fix 23 — Connections profile gate uses a stale `myAnswerCount`

**Problem.** `ConnectionsViewModel.loadConnections` captures the current user's answer count once with `.first()` at load time:

```kotlin
val myCount = if (activeUserId != null) repo.getAnswerCountForUser(activeUserId).first() else 0
```

Then stuffs it into `ConnectionsUiState.myAnswerCount`. `ConnectionsScreen` uses that value to gate profile navigation ("Answer 8 questions first…"). But the count never updates — if a user opens Connections with 6 answers, goes to Survey, answers 3 more, then returns to Connections, the screen still thinks they have 6 and keeps gating them.

**Fix.** Replace the one-shot `.first()` read with a live `collect` on the same flow, combined with the existing `getAllUsers()` flow so both update the state.

1. In `ConnectionsViewModel.loadConnections`, restructure to combine the three sources of truth:
   ```kotlin
   fun loadConnections() {
       viewModelScope.launch {
           val activeUserId = repo.getActiveUserId()
           _uiState.update { it.copy(activeUserId = activeUserId) }

           val answerCountFlow = if (activeUserId != null)
               repo.getAnswerCountForUser(activeUserId)
           else
               flowOf(0)

           combine(
               repo.getAllUsers(),
               answerCountFlow
           ) { users, myCount -> users to myCount }
               .collect { (users, myCount) ->
                   val entries = repo.getAllConnectionEntries(users)
                   _uiState.update {
                       it.copy(
                           isLoading = false,
                           connections = entries,
                           myAnswerCount = myCount
                       )
                   }
               }
       }
   }
   ```
   Import `kotlinx.coroutines.flow.combine` and `kotlinx.coroutines.flow.flowOf`.

2. That's the whole fix. `ConnectionsScreen` already reads `state.myAnswerCount` for the gate check and will re-render when it changes.

**Manual test.** As a user with 6 survey answers, open Connections. Tap a mutual connection row — confirm the gate snackbar appears. Navigate back to home, answer 3 more survey questions, then re-open Connections. Tap the same row — the profile should now open without a snackbar.

Bonus: if you can test it, also verify that someone else taking a quiz about you while you're sitting on the Connections screen updates the displayed connections live (that was already supposed to work via `getAllUsers` but the combined flow now makes it more obviously correct).

---

## Fix 24 — One-way connection rows aren't tappable

**Problem.** Fix 22 made `MutualConnectionRow` clickable — tapping opens the other user's profile. But `OneWayConnectionRow` got no equivalent treatment. A user looking at the "One-Way" view sees rows like `"You know Jamie — 80%"` and `"Alex knows you — 45%"` and naturally tries to tap one. Nothing happens. The "tap a person to see their profile" model breaks halfway.

**Fix.** Add the same click behavior to `OneWayConnectionRow`, routed through the same gate.

1. In `ConnectionsScreen.kt`, extend `OneWayConnectionRow`'s signature with an `onClick: (() -> Unit)? = null` parameter (same pattern as `MutualConnectionRow`):
   ```kotlin
   @Composable
   private fun OneWayConnectionRow(
       rank: Int,
       label: String,
       score: Double,
       onClick: (() -> Unit)? = null
   ) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
               .padding(horizontal = 16.dp, vertical = 12.dp),
           // ... rest unchanged
       )
   }
   ```

2. In the `oneWayList` rendering block, wire up the click with the same gate-and-navigate logic as `MutualConnectionRow`. For one-way rows, the target user is always unambiguous:
   - In MINE scope: the target is the "other" user — if `from.id == activeUserId` then target is `to`; otherwise target is `from`.
   - In EVERYONE scope: two users are named in the row (`"Alex knows Jamie"`). Show the same disambiguation dialog `MutualConnectionRow` uses via `pendingProfileEntry`. To reuse that code path, synthesize a `ConnectionEntry` for the dialog: `ConnectionEntry(userA = from, userB = to, scoreAtoB = score, scoreBtoA = 0.0, mutualScore = score)` — the dialog only reads `userA` and `userB`, so the score fields are cosmetic.

   ```kotlin
   itemsIndexed(oneWayList) { index, (from, to, score) ->
       val label = ...  // existing logic
       OneWayConnectionRow(
           rank = index + 1,
           label = label,
           score = score,
           onClick = {
               if (state.myAnswerCount < Constants.QUIZ_UNLOCK_THRESHOLD) {
                   coroutineScope.launch {
                       snackbarHostState.showSnackbar(profileGateMessage)
                   }
               } else if (state.scope == ConnectionScope.MINE && state.activeUserId != null) {
                   val targetId = if (from.id == state.activeUserId) to.id else from.id
                   onGoToProfile(targetId)
               } else {
                   pendingProfileEntry = ConnectionEntry(from, to, score, 0.0, score)
               }
           }
       )
       HorizontalDivider()
   }
   ```

3. No changes needed to `pendingProfileEntry` or the dialog rendering — it already handles a `ConnectionEntry`.

**Manual test.** Switch Connections to "One-Way" mode. In MINE scope, tap a `"You know Jamie"` row — Jamie's profile should open. Tap an `"Alex knows you"` row — Alex's profile should open. Switch to Everyone scope. Tap a one-way row — the disambiguation dialog should appear with both names. Pick one; confirm their profile opens.

Also confirm the 8-answer gate still fires for a user below threshold.

---

## Fix 25 — `ProfileScreen` shows English question templates in Spanish mode

**Problem.** `ProfileViewModel` always uses the English question template:

```kotlin
val pairs = rawAnswers.mapNotNull { answer ->
    val q = repo.db.surveyQuestionDao().getQuestionById(answer.questionId)
    if (q != null) forQuiz(q.questionTemplate, user.name) to answer.answer else null
}
```

A Spanish-language user viewing someone's profile sees questions like `"Does Jamie prefer coffee or tea?"` next to an answer. The Spanish template (`q.questionTemplateEs`) is stored in the DB but ignored.

The answer text itself is also language-dependent — survey options are stored in English in `survey_answers.answer`, and the quiz screen does option translation by index lookup. For consistency with `QuizScreen.kt`'s existing pattern, we need to do the same lookup in `ProfileViewModel`.

**Fix.** Thread the current language into `ProfileViewModel` and branch both the template and the answer display.

1. Extend `ProfileViewModel` to take a `language: String` parameter in its constructor and factory. The value is read once at construction time — it won't update live if the user toggles language while on the profile screen, but that's an edge case and the ProfileScreen's own `LanguageToggle` in the TopAppBar will force a reload via navigation if we want (see step 4).

   ```kotlin
   class ProfileViewModel(
       private val repo: GTKYRepository,
       private val userId: Long,
       private val language: String
   ) : ViewModel() { ... }

   class Factory(
       private val repo: GTKYRepository,
       private val userId: Long,
       private val language: String
   ) : ViewModelProvider.Factory { ... }
   ```

2. Rewrite the `init` block to pick the right template and translate the answer:
   ```kotlin
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
           _uiState.value = ProfileUiState(isLoading = false, name = user.name, answers = pairs)
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
   ```
   Import the necessary Kotlinx serialization classes (already used elsewhere in the project — see `GTKYRepository.parseOptions` for the same pattern).

3. In `NavGraph.kt`, read the current language from `GTKYApplication` and pass it into the factory:
   ```kotlin
   composable(
       Routes.PROFILE,
       arguments = listOf(navArgument("userId") { type = NavType.LongType })
   ) { backStack ->
       val userId = backStack.arguments!!.getLong("userId")
       val app = context.applicationContext as GTKYApplication
       val language by app.language.collectAsState()
       val vm: ProfileViewModel = viewModel(
           key = "profile-$userId-$language",  // force recreation on language change
           factory = ProfileViewModel.Factory(repo, userId, language)
       )
       ProfileScreen(viewModel = vm, onBack = { navController.popBackStack() })
   }
   ```
   The `key` parameter forces the ViewModel to be recreated when the language changes, which is the simplest way to avoid an observer pattern for a read-once construction parameter.

4. Leave `ProfileScreen` itself unchanged — it's already just rendering what the ViewModel provides.

**Manual test.** Sign in. Open any user's profile (via Connections or quiz results). Note the displayed questions and answers. Toggle the language via the toggle in the top app bar. Navigate away and back (or just re-open the profile). Confirm the questions now read in Spanish (e.g., `"¿Jamie prefiere café o té?"`) and the answer text is also translated (e.g., `"Café"` instead of `"Coffee"`). Toggle back to English. Re-open. Confirm it's English again.

---

## Fix 26 — Housekeeping

Roll these into a single commit titled `fix/26-housekeeping`.

### 26a — Delete orphaned `countAvailableQuestions`

Fix 16 moved all pool-size calculations to the cached in-memory path via `loadAllSubjectPools` + `filterSubjectPools`. The old `countAvailableQuestions` in `GTKYRepository` has no callers and can be removed.

Grep for callers one more time before deleting (`grep -rn "countAvailableQuestions" app/src/main`). If the grep shows only the definition, delete the function. If anything else references it, stop and flag it.

### 26b — Tighten `transitionToUserSelected` against stale writes

`HomeViewModel.transitionToUserSelected` launches three coroutines. One is tracked (`answerCountJob`, cancelable) but two are fire-and-forget:

```kotlin
viewModelScope.launch {
    _readyUsersByGroup.value = repo.getReadyUsersByGroup(user.id, _groups.value)
}
viewModelScope.launch {
    _allSubjectPools.value = repo.loadAllSubjectPools(user.id)
    val groupIds = _groups.value.map { it.id }
    _groupMembersMap.value = repo.getGroupMembersMap(groupIds)
}
```

If a user signs in as A, then quickly signs out and signs in as B, the still-in-flight "User A" coroutines can land after the "User B" ones and overwrite the StateFlows with stale data. Low probability on a kiosk but trivial to fix.

Make both jobs cancelable and cancel them at the top of `transitionToUserSelected`:

```kotlin
private var readyUsersJob: Job? = null
private var subjectPoolsJob: Job? = null

private fun transitionToUserSelected(user: User) {
    answerCountJob?.cancel()
    readyUsersJob?.cancel()
    subjectPoolsJob?.cancel()
    quizzableUsersJob?.cancel()

    answerCountJob = viewModelScope.launch { /* unchanged */ }
    readyUsersJob = viewModelScope.launch {
        _readyUsersByGroup.value = repo.getReadyUsersByGroup(user.id, _groups.value)
    }
    subjectPoolsJob = viewModelScope.launch {
        _allSubjectPools.value = repo.loadAllSubjectPools(user.id)
        val groupIds = _groups.value.map { it.id }
        _groupMembersMap.value = repo.getGroupMembersMap(groupIds)
    }
    quizzableUsersJob = viewModelScope.launch { /* unchanged */ }
}
```

Also cancel both new jobs in `signOut()`:

```kotlin
fun signOut() {
    viewModelScope.launch {
        repo.clearActiveUser()
        answerCountJob?.cancel()
        readyUsersJob?.cancel()
        subjectPoolsJob?.cancel()
        quizzableUsersJob?.cancel()
        _uiState.value = HomeUiState.NoUser()
    }
}
```

**Manual test.** On a device, rapidly sign out and sign back in as a different user five or six times in a row. The home screen should end up reflecting the currently signed-in user's state, not a blend of the two. Without this fix, there's a small window where the "ready count" or filter-preview subject list can briefly reflect the previous user.

---

## Shipping checklist

- [ ] All strings bilingual via `t()`.
- [ ] `./gradlew :app:assembleDebug` passes after every fix.
- [ ] `./gradlew :app:testDebugUnitTest` passes after every fix. No new tests required.
- [ ] Manual smoke test: (a) Fix 23 — gate updates without leaving Connections. (b) Fix 24 — tap a one-way row, profile opens. (c) Fix 25 — toggle language, profile answers render in the new language. (d) Fix 26b — fast sign-out/sign-in doesn't leave stale ready counts.
- [ ] `CHANGELOG.md` has four new bullets (23, 24, 25, 26) under the existing `## UX Fix Pack` section.
