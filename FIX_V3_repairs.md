# GTKY — v3 Repair Pack

This document is an addendum to `FIXES.md` and `FIX_11_quiz_distribution.md`. Those fixes landed, but a second pass surfaced real bugs, a few regressions, and some engagement-level gaps. This pack fixes them.

**Apply these fixes in order — do not skip around.** Fixes 12, 13, and 14 are correctness bugs that silently affect quiz eligibility and dialog freshness; land those first and confirm the app still builds and runs before moving on. Fixes 15–18 are UX/regression polish. Fixes 19–22 are engagement-level changes that build on the existing architecture without adding new features.

**Ground rules (unchanged from earlier packs):**

- Every new user-facing string needs both English and Spanish via the existing `t("en", "es")` helper.
- No new dependencies.
- Keep the Compose + StateFlow + ViewModel architecture.
- If a change requires a Room schema change, bump the DB version and write a migration. Do not wipe user data.
- Work one fix per commit, one branch per fix: `fix/NN-slug`. Add a matching bullet to `CHANGELOG.md` under the `## UX Fix Pack` section.
- After each fix: run `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`. Both must pass before committing.

---

## Fix 12 — `getQuizEligibleUsersInGroup` still hardcodes 15

**Problem.** `UserDao.getQuizEligibleUsersInGroup` uses `HAVING COUNT(sa.id) >= 15` in a Room SQL query. Kotlin `const val` can't be interpolated into `@Query` strings, so when the Fix 1 threshold dropped from 15 to 8, this one query didn't follow. The result: a user is "quiz eligible" if they've answered 8+ questions *unless* someone filters the quiz by a specific group, in which case they need 15+. Same user, two different eligibility answers depending on the filter — silent correctness bug.

**Fix.** Remove `getQuizEligibleUsersInGroup` entirely. It's no longer needed: `resolveEligibleSubjects` already handles the group-filter branch correctly, using `answerCounts` keyed off `Constants.QUIZ_UNLOCK_THRESHOLD`. Audit the codebase for any remaining callers of `getQuizEligibleUsersInGroup` and route them through the repository's `getEligibleSubjects` path or a new public wrapper, not the DAO method.

Implementation:

1. Delete the `@Query` annotated `getQuizEligibleUsersInGroup` function from `UserDao.kt`.
2. Grep for all call sites. Based on the current state, the only caller should be internal to the repository's older quiz path (already replaced in Fix 11). Confirm that Fix 11's `buildQuizSession` and `countAvailableQuestions` paths don't reference it. If any caller remains, replace it with: `getEligibleSubjects(quizTakerId, groupIds = listOf(groupId), subjectUserIds = emptyList())`.
3. If the deletion causes compilation errors in other places you don't expect, stop and ask before patching — the reasonable assumption is that the DAO method is now orphaned.

No schema migration needed.

**Manual test.** With three test users each at exactly 10 answers (above 8, below 15), create a group and add them all. Take a quiz filtered to that group. Before this fix they'd be excluded (15 threshold); after, they should all be quizzable. Without the fix, the dialog's pool-size indicator shows "0 questions available." After, it shows a real number.

---

## Fix 13 — `getQuizzableUsers` flow never re-emits when people answer questions

**Problem.** In `GTKYRepository`:

```kotlin
fun getQuizzableUsers(excludeUserId: Long): Flow<List<User>> =
    combine(getAllUsers(), db.surveyAnswerDao().getAnswerCountForUser(0L)) { users, _ -> users }
        .map { users -> users.filter { ... getAnswerCountForUserSync(user.id) >= threshold } }
```

The second flow is `getAnswerCountForUser(0L)` — hardcoded user id `0L`. No user has id 0 (autogen IDs start at 1), so that flow emits once with a count of 0 and never again. The combined flow therefore only re-fires when the user roster changes, not when anyone answers survey questions. At an event, three people are mid-survey; one of them crosses the 8-answer threshold; the quiz filter dialog's person list does not pick them up until a user signs in or out.

**Fix.** Add a DAO query that flows off the whole `survey_answers` table, and use it as the trigger for `getQuizzableUsers`.

1. In `SurveyAnswerDao`, add:
   ```kotlin
   @Query("SELECT COUNT(*) FROM survey_answers")
   fun getTotalAnswerCountFlow(): Flow<Int>
   ```
   This flow emits on every insert/delete anywhere in `survey_answers`, which is the signal we want.

2. In `GTKYRepository`, change `getQuizzableUsers` to use it:
   ```kotlin
   fun getQuizzableUsers(excludeUserId: Long): Flow<List<User>> =
       combine(getAllUsers(), db.surveyAnswerDao().getTotalAnswerCountFlow()) { users, _ -> users }
           .map { users ->
               users.filter { user ->
                   user.id != excludeUserId &&
                   db.surveyAnswerDao().getAnswerCountForUserSync(user.id) >= Constants.QUIZ_UNLOCK_THRESHOLD
               }
           }
   ```

3. **Also fix `HomeViewModel.transitionToUserSelected` — it has the same bug.** Right now:
   ```kotlin
   answerCountJob = viewModelScope.launch {
       repo.getAnswerCountForUser(user.id).collect { c ->
           val readyCount = repo.getReadyUserCount(user.id)
           _uiState.value = HomeUiState.UserSelected(user, c, readyCount)
       }
   }
   ```
   This collect only fires when the *current user's* answer count changes. If another user answers their 8th question, `readyCount` is stale on the home screen until the current user answers another question themselves.

   Rework `transitionToUserSelected` to listen to both flows and recompute on either:
   ```kotlin
   answerCountJob = viewModelScope.launch {
       combine(
           repo.getAnswerCountForUser(user.id),
           repo.db.surveyAnswerDao().getTotalAnswerCountFlow()
       ) { myCount, _ -> myCount }
           .collect { myCount ->
               val readyCount = repo.getReadyUserCount(user.id)
               _uiState.value = HomeUiState.UserSelected(user, myCount, readyCount)
           }
   }
   ```
   Accessing `repo.db` directly is a mild layering break; if you'd rather, expose a thin `fun observeTotalAnswers(): Flow<Int>` on the repository and use that instead.

4. While you're in `HomeViewModel`, also make `refreshReadyUsersByGroup` reactive — today it's only called when users or groups change. Hook it into the same total-answer-count flow so the "who's ready in each group" map is fresh.

**Manual test.** With Users A, B, C all signed up, where B has 7 answers and C has 4 answers:

1. Sign in as A on one device (or session). Open the quiz filter dialog, expand the person picker. B should not be in the list.
2. Have B answer one more question (become eligible).
3. Without doing anything else on Device A, reopen the filter dialog. B should now appear in the list.

For the home screen: as User A, the "Quiz unlocks when 1+ other person finishes" subtitle should disappear as soon as B becomes eligible, without A having to interact with the home screen or navigate away.

---

## Fix 14 — Two empty-state "Take a Quiz" buttons that don't open the quiz

**Problem.** Two screens have a primary "Take a Quiz" button whose `onClick` handler is `onBack` — it just pops back to the home screen, it does not open the quiz filter dialog. Users read the button, tap it, and nothing visible happens except landing on a different screen.

- `SurveyScreen.AllAnsweredContent`
- `ConnectionsScreen` — MINE empty state

The Fix 4 spec intended for these to pop back to home *and* auto-open the quiz dialog with no subject pre-selected.

**Fix.**

1. Add a parameterless `requestOpenQuizDialog()` function to `HomeViewModel`. Mirror the existing `requestQuizWithSubject(userId: Long)` / `clearPendingQuizSubject()` pair: use a new `MutableStateFlow<Boolean>` (`_pendingOpenQuizDialog`) so the auto-open is unambiguous and doesn't require a special sentinel on `_pendingQuizSubjectId`.

   ```kotlin
   private val _pendingOpenQuizDialog = MutableStateFlow(false)
   val pendingOpenQuizDialog: StateFlow<Boolean> = _pendingOpenQuizDialog.asStateFlow()

   fun requestOpenQuizDialog() { _pendingOpenQuizDialog.value = true }
   fun clearPendingOpenQuizDialog() { _pendingOpenQuizDialog.value = false }
   ```

2. In `HomeScreen.UserHomeScreen`, add a `LaunchedEffect(pendingOpenQuizDialog)` next to the existing one for `pendingQuizSubjectId`. When it flips true, set `showQuizFilterDialog = true` and call `onClearPendingOpenQuizDialog()`.

3. In `NavGraph.kt`, share the Home ViewModel into `Routes.CONNECTIONS` and `Routes.SURVEY` the same way it's shared into `Routes.ACTIVE_USERS`:
   ```kotlin
   composable(Routes.SURVEY, ...) { backStack ->
       val userId = backStack.arguments!!.getLong("userId")
       val homeVm: HomeViewModel = viewModel(
           viewModelStoreOwner = navController.getBackStackEntry(Routes.HOME),
           factory = HomeViewModel.Factory(repo)
       )
       val vm: SurveyViewModel = viewModel(factory = SurveyViewModel.Factory(repo, userId))
       SurveyScreen(
           viewModel = vm,
           onBack = { navController.popBackStack() },
           onGoToQuiz = {
               homeVm.requestOpenQuizDialog()
               navController.popBackStack(Routes.HOME, inclusive = false)
           }
       )
   }
   ```
   Do the same for `Routes.CONNECTIONS`.

4. Add an `onGoToQuiz: () -> Unit = {}` parameter to `SurveyScreen` and `ConnectionsScreen`. Wire it to the "Take a Quiz" button in both empty states. Keep `onBack` wired to the "Back Home" button so they do different things.

**Manual test.** Fresh user. Answer all survey questions (ask an admin to delete a bunch of questions to make this fast, or add a debug shortcut). Confirm the "Take a Quiz" button on the all-answered screen actually opens the quiz filter dialog on return to home. Same for the Connections MINE empty state: tap "Take a Quiz" and confirm the dialog opens.

---

## Fix 15 — No "forgot PIN" recovery path

**Problem.** Fix 9 forces the admin to change the default PIN on first access. Good. But if the new PIN is forgotten, there is no recovery path inside the app. The only way out is clearing app data via Android system settings, which wipes users and answers too — unacceptable at a recurring event.

**Fix.** Add a "Reset PIN" button on the PIN entry screen that requires a confirmation with a typed acknowledgment, then resets the PIN to `"1234"` and re-enables the forced-change flag.

1. In `AdminViewModel`, add:
   ```kotlin
   fun resetPinToDefault() {
       viewModelScope.launch {
           repo.setAdminPin("1234")
           repo.db.appConfigDao().setValue(AppConfig("admin_pin_is_default", "true"))
           _uiState.update { it.copy(isPinDefault = true, pinError = null) }
       }
   }
   ```
   Note: `setAdminPin` currently clears the `admin_pin_is_default` flag. We need to re-set it after the reset, which is why we're bypassing by going to the DAO directly here. Alternatively, extend `setAdminPin` to take a `isDefault: Boolean = false` parameter; cleaner.

2. In `AdminScreen.PinEntryScreen`, below the existing "Enter" button add a small `TextButton` reading "Forgot PIN? Reset to default." It opens a confirmation `AlertDialog` with copy like:
   - Title: "Reset admin PIN?"
   - Body: "This resets the admin PIN to 1234. Users and survey answers are kept. You'll be prompted to change the PIN on next admin entry."
   - Confirm button: "Reset to 1234" (in error color)
   - Dismiss: "Cancel"

3. On confirm, call `resetPinToDefault()` and show a brief inline success message on the PIN entry screen ("PIN reset — enter 1234 to continue").

4. **Do not gate this behind any other auth.** The scenario is "admin forgot the PIN" — there's no other credential available. That does mean any user can reset the PIN, which is a trust-vs-recoverability trade-off. For a kiosk at a trusted event, recoverability wins. Document this in the CHANGELOG entry.

Translate all new strings.

**Manual test.** Fresh install. Enter admin with `1234`, change PIN to `5678`. Back out. Re-enter admin, tap "Forgot PIN? Reset to default." Confirm. PIN resets to `1234`; next entry triggers the forced-change flow from Fix 9.

---

## Fix 16 — `countAvailableQuestions` thrashes the DB on every chip tap

**Problem.** The filter dialog's pool-size indicator calls `repo.countAvailableQuestions(...)` (debounced 200ms) on every selection change. Each call reads all users, then for each eligible subject runs `getAlreadyAttemptedQuestionIds` + `getAnsweredQuestionsForUser(50)`. With 20 eligible users that's ~40 queries per tap. On a warm tablet it's jittery and battery-hostile.

**Fix.** Compute the full per-subject pool once when the dialog opens, then filter in memory.

1. Add a new function to `GTKYRepository`:
   ```kotlin
   data class SubjectPool(val user: User, val availableCount: Int)

   suspend fun loadAllSubjectPools(quizTakerId: Long): List<SubjectPool> {
       val users = db.userDao().getAllUsers().first()
       val counts = users.associate { it.id to db.surveyAnswerDao().getAnswerCountForUserSync(it.id) }
       val eligible = users.filter { it.id != quizTakerId && (counts[it.id] ?: 0) >= Constants.QUIZ_UNLOCK_THRESHOLD }
       return eligible.map { subject ->
           val alreadyAttempted = db.quizResultDao()
               .getAlreadyAttemptedQuestionIds(quizTakerId, subject.id).toSet()
           val available = db.surveyQuestionDao()
               .getAnsweredQuestionsForUser(subject.id, 50)
               .count { it.id !in alreadyAttempted }
           SubjectPool(subject, available)
       }
   }
   ```

2. Add a matching function that filters an already-loaded pool list in memory:
   ```kotlin
   suspend fun getGroupMembersMap(groupIds: List<Long>): Map<Long, Set<Long>> { /* thin DAO call, returns groupId -> user ids */ }

   fun filterSubjectPools(
       pools: List<SubjectPool>,
       groupIds: List<Long>,
       subjectUserIds: List<Long>,
       groupMembers: Map<Long, Set<Long>>
   ): List<SubjectPool> { /* same logic as resolveEligibleSubjects but on SubjectPool list */ }
   ```

3. In `HomeViewModel`:
   - Add `_allSubjectPools: MutableStateFlow<List<SubjectPool>>` and `_groupMembersMap: MutableStateFlow<Map<Long, Set<Long>>>`.
   - When the home state becomes `UserSelected`, load both (once). Also refresh them on the `getTotalAnswerCountFlow()` signal from Fix 13.
   - Rewrite `updateFilterPreview` to do the filtering in memory:
     ```kotlin
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
     ```

4. Keep the 200ms debounce. Chip taps now produce pure in-memory ops.

**Manual test.** With 15 users each at 10+ answers, open the filter dialog and rapidly toggle group chips. The pool-size indicator should update smoothly and the app should stay responsive. Enable the Android Studio database profiler if you want to verify the reduction in DB access.

---

## Fix 17 — Idle timeout is uniform across screens (bad for surveys and admin)

**Problem.** `IdleTimeout` uses a flat 90s timeout regardless of route. That's fine on the home screen, but:

- Mid-survey: 90s is enough to get torn down while reading a question or chatting. Users lose their place and have to re-enter.
- Admin PIN change (forced on first entry): 90s to silently navigate home during a multi-field form is user-hostile.

**Fix.** Make the timeout route-aware.

1. Extend `IdleTimeout` to accept a dynamic timeout:
   ```kotlin
   @Composable
   fun IdleTimeout(
       timeoutMs: () -> Long = { 90_000L },
       onIdle: () -> Unit,
       content: @Composable () -> Unit
   )
   ```
   Inside the `LaunchedEffect`, call `timeoutMs()` on each check so it picks up the current value.

2. In `MainActivity`, derive the timeout from the current route:
   ```kotlin
   val currentRoute by navController.currentBackStackEntryAsState()
   val route = currentRoute?.destination?.route
   val timeout: () -> Long = {
       when (route) {
           Routes.SURVEY, Routes.QUIZ -> 180_000L   // 3 min while actively playing
           Routes.ADMIN -> 300_000L                 // 5 min in admin
           else -> 90_000L                          // 1.5 min on home and lists
       }
   }
   IdleTimeout(timeoutMs = timeout, onIdle = { app.handleIdleTimeout { ... } }) { ... }
   ```
   Route matching has to handle the route templates (e.g., `"survey/{userId}"`). Match with `startsWith("survey/")` or extract the prefix cleanly.

3. **One more edge case** — if the user sits on the admin PIN-entry screen (before auth) with the default PIN still active, keep it at the normal 90s. The generous timeout only kicks in after authentication on admin routes. You can check this via the AdminViewModel's `isAuthenticated` state via a shared ViewModel pattern, or simpler: apply the 5-minute timeout to the whole `Routes.ADMIN` route and accept that an attacker could stand on the PIN-entry screen for 5 minutes. Given this is a kiosk at an event, that's fine.

**Manual test.** Mid-survey, stop interacting for 2 minutes — the session should persist. After 3 minutes it should reset. On admin, after a successful PIN entry, stay on the screen for 4 minutes — should persist.

---

## Fix 18 — Sign-in flow regressions

Three small UX regressions introduced by Fix 5:

### 18a — Unneeded sign-in confirmation on unique names

**Problem.** `PickUserScreen` now confirms *every* tap with "Sign in as $name?" — good for disambiguation, friction for a unique name.

**Fix.** Compute whether a tapped user has a name collision using the existing `nameIndexMap`. If the user's display name has a `(N)` suffix, show the confirmation dialog. If they're uniquely named, sign in directly.

```kotlin
val hasCollision = nameIndexMap[user.id]?.contains("(") == true
if (hasCollision) {
    pendingUser = user
} else {
    onUserSelected(user)
}
```

### 18b — "Not you?" and "Switch User" share one dialog with the wrong copy

**Problem.** Both fire the same `showSignOutDialog` state and show "Switch user? You can sign back in from the picker." That copy fits "Switch User" but reads oddly for "Not you?" — the person tapping that button is saying "this isn't me; reset," not "I'm switching between two of my accounts."

**Fix.** Use two distinct dialog states with context-appropriate copy.

- "Not you?" dialog: title "Sign out ${user.name}?" / body "The next person can sign in from the picker." / confirm "Sign out" / cancel "Cancel"
- "Switch User" dialog: title "Switch user?" / body "You can sign back in from the picker." / confirm "Switch" / cancel "Cancel"

Both do the same thing on confirm (sign out), but the framing matches the trigger. Add a second `showNotYouDialog: Boolean` state or parameterize the shared dialog with a `reason: SignOutReason` enum.

### 18c — `DuplicateNameDialog` buttons are in confusing positions

**Problem.** Dialog's `confirmButton` is "Yes, that's me" and `dismissButton` is "Add a middle initial or more." That puts the "I want to edit" action in the left (dismiss) slot, which is where users expect "cancel." On scrim tap, the dialog also returns to the form — which is the same thing the dismiss button does. Users may tap scrim by accident and think they undid something.

**Fix.** Keep the actions correct but clarify the copy:
- `confirmButton` — "Yes, that's me" (unchanged; signs in as the existing user).
- `dismissButton` — change to "No, I'm different" (shorter, clearer).
- Add a small hint line in the dialog body, below the main text: "If you're different, you can add more to your last name to tell you apart."

That's a copy change only. No behavior change beyond the label.

**Manual test.** For 18a: create a user named "Sam Z" (unique). Sign out. Open the picker, tap "Sam Z" — should sign in immediately, no dialog. Create a second "Sam Z" (which forces disambiguation). Sign out. Open the picker, tap either "Sam Z (1)" or "Sam Z (2)" — the confirmation dialog should now appear.

For 18b: tap "Not you?" vs "Switch User" — both should work but the dialog copy should be different.

For 18c: trigger a name collision. Read the dialog. The "I'm different" option should be clearly on the left/dismiss side.

---

## Fix 19 — Filter dialog's person list is not a LazyColumn

**Problem.** `filteredPersonList.forEach { ... }` renders every match inside a `Column` inside a `Column(verticalScroll(...))`. At 50 users the dialog swells and nested scroll feels bad.

**Fix.** Swap the `forEach` for a `LazyColumn` with a constrained height.

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 280.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
) {
    items(filteredPersonList, key = { it.id }) { person ->
        // existing Row with checkbox
    }
}
```

Also remove the enclosing `Column(verticalScroll(rememberScrollState()))` if it was only there to accommodate the person list — AlertDialog's text slot handles its own scrolling for fixed-height children. If removing the outer scroll breaks the group-chip row (which may overflow horizontally on narrow screens), leave the scroll but confirm the LazyColumn renders correctly inside it (the `heightIn(max = 280.dp)` matters — without a bounded height, LazyColumn inside a verticalScroll crashes at runtime).

**Manual test.** With 30+ quizzable users, open the filter dialog and expand the person list. Scrolling should feel smooth; the dialog shouldn't resize as you type in the search field.

---

## Fix 20 — Quiz finish flow is opaque about "back = save"

**Problem.** In the quiz, the back-arrow icon in the TopAppBar appears only when `canFinish` is true, and tapping it calls `finishQuiz()` — which saves results and shows the results screen. That's not what "back" typically means. Users expect back = cancel. If they tap it after 5 questions thinking "let me bail out," they instead commit and see a score.

**Fix.** Add a confirmation dialog on the back-arrow tap, with clear "save and exit" vs "cancel" options.

1. In `QuizScreen`, replace the direct `onClick = { viewModel.finishQuiz() }` with `onClick = { showFinishDialog = true }` and render an `AlertDialog`:
   - Title: "Finish quiz?"
   - Body: "Your ${state.answeredCount} answers will be saved."
   - Confirm: "Finish"
   - Dismiss: "Keep quizzing"

2. Keep the in-line "Finished" button that appears after revealing an answer (when `canFinish`) — that one has clear intent, no dialog needed. The dialog only appears on the back-arrow path.

3. Do not add a "discard" option. The pendingResults list represents real data; silently dropping it is worse than saving it. The copy "Your N answers will be saved" makes this explicit.

**Manual test.** Take a quiz. After 5 questions, tap the back arrow. Confirm the dialog appears and says what it will do. Tap "Keep quizzing" — confirm the quiz continues. Tap back arrow again, tap "Finish" — confirm the results screen appears.

---

## Fix 21 — Quiz results screen is dead

**Problem.** After a quiz, users see `"X / Y"` and a percentage. That's it. All the per-subject and streak data is sitting in `sessionResults` and never surfaced. There's no "you got 3/3 on Jamie!" and no suggested next action.

**Fix.** Add a per-subject breakdown and a "next quiz" suggestion on the results screen.

1. Extend `QuizUiState.sessionResults` (already there) to be rendered into a grouped summary. In `QuizResultsScreen`, below the score:
   - For each subject in the session, compute `correct / total` about them.
   - Render a compact row: `"Jamie: 3/3"` (in success color if 100%), `"Alex: 1/4"` (in error color if <50%), `"Taylor: 2/3"` (neutral otherwise).
   - Sort subjects by the ratio descending, so the user sees their best match first.

2. Below the breakdown, if there's at least one subject with a <60% score, render a suggestion:
   - Copy: "Quiz yourself on $worstSubject next? You only got $X/$Y right."
   - Button: "Quiz about $worstSubject" — this pops back to home and opens the quiz filter dialog with that subject pre-selected (reuses the `requestQuizWithSubject` plumbing from Fix 11).

3. If every subject scored 100%, show a different congrats: "You nailed everyone this round!" with no suggestion button — just "Back Home."

4. Pass `sessionResults` through from the ViewModel to the results screen. `QuizUiState` already exposes it. You'll need the list of subjects in the session; derive them from `sessionResults` + `questions`.

5. Wire the "Quiz about $worstSubject" button through `NavGraph`:
   - Pop back to `Routes.HOME`
   - On HomeViewModel, call `requestQuizWithSubject(worstSubjectId)`
   - The existing auto-open logic in `UserHomeScreen` takes over.

Translate all new strings.

**Manual test.** Take a quiz with at least 3 subjects mixed in. On the results screen, confirm the per-subject breakdown appears in sorted order. If any subject has <60% correct, confirm the "Quiz about $name next?" suggestion appears with a working button. Tap it; confirm the filter dialog opens with that subject pre-selected.

---

## Fix 22 — "See their answers" — a profile view reusing admin code

**Problem.** The most natural gesture after quizzing about Jamie is "show me Jamie's answers." All the data and logic already exists in `AdminScreen.UserDetailScreen`, which renders a list of (question, answer) pairs per user. It's just locked behind the admin PIN.

**Fix.** Add a read-only "Profile" screen that shows a user's survey answers, reachable from (a) a row on the quiz results screen, and (b) a tap on the Connections row.

1. Create a new screen `ui/screens/ProfileScreen.kt`. It takes a `userId: Long` and shows:
   - Header: the user's name.
   - A list of the user's survey answers, formatted like `UserDetailScreen` already does. You can refactor that rendering out of `AdminScreen` into a shared composable `AnswerList(answers: List<Pair<String, String>>, subjectName: String)` and use it from both screens.
   - Back button.

2. Create a `ProfileViewModel` that calls `repo.getAllAnswersForUser(userId)` and exposes a `ProfileUiState(name, answers, isLoading)`.

3. Add a route `Routes.PROFILE = "profile/{userId}"` with a `profile(userId: Long)` helper. Wire it in `NavGraph.kt`.

4. Entry points:
   - **Quiz results per-subject row** (from Fix 21): make the subject row clickable; tap navigates to their profile.
   - **Connections row** (`MutualConnectionRow` in `ConnectionsScreen.kt`): make the row clickable. In MINE scope, tapping a row navigates to the *other* user's profile (not the current user's). In EVERYONE scope, tapping is ambiguous between two users — show a small dialog: "See whose profile? [Name A] [Name B] [Cancel]" and navigate to the chosen one.

5. Reuse `forQuiz(template, subjectName)` from `QuestionPhrasing.kt` to render each question with the subject's name inline (so it reads "Does Jamie prefer coffee or tea?" → "Coffee").

6. Do not expose this view before a user has answered at least 8 questions of their own — keep the light "put in before you take out" social contract. If the current user has <8 answers, profile navigation paths show a small snackbar: "Answer 8 questions first to see others' profiles." Cheap friction that keeps the app's loop intact.

Translate all strings.

**Manual test.** Sign in as a fresh user. Tap the Connections row for someone — confirm the "Answer 8 questions first" message appears. Answer 8 questions. Return to Connections, tap a row — confirm the profile screen opens and shows the other user's answers.

From the quiz results screen, tap the per-subject row for someone. Confirm the profile screen opens for that subject.

---

## Minor cleanups (roll into Fix 18's commit)

Not worth their own fix numbers but too small to leave:

- **`ActiveUsersScreen` line 58:** `Text("Filter by Group", ...)` is not routed through `t()`. Fix it.
- **Filter dialog section header "Pick specific people (N selected)":** fine in English, but in Spanish the pluralization of "seleccionada/seleccionadas" only handles 0 and ≥2 correctly; `1 seleccionada` should render as singular. Simple fix via the `plural` helper already in `LocalAppLanguage.kt`.
- **"Still setting up…" label on `ActiveUsersScreen`** is rendered as a third line under the stats, pushing the row height up inconsistently. Move it to replace the stats line (or hide the stats line) when the user isn't eligible — a not-yet-ready user doesn't have meaningful stats to show anyway.
- **In `HomeViewModel.renameUser`**, calling `transitionToUserSelected(updatedUser)` restarts all three collect jobs. Replace with a targeted update:
  ```kotlin
  val cur = _uiState.value as? HomeUiState.UserSelected ?: return@launch
  _uiState.value = cur.copy(user = updatedUser, renameError = null)
  ```
  Don't trigger the full transition.

---

## Shipping checklist

- [ ] All strings bilingual via `t()`.
- [ ] Room DB version unchanged (no schema changes required for this pack).
- [ ] `./gradlew :app:assembleDebug` passes after every fix.
- [ ] `./gradlew :app:testDebugUnitTest` passes after every fix. New tests added for Fix 13's `getTotalAnswerCountFlow` if practical.
- [ ] Manual smoke test on a real device: create 3 users, get two of them to 8 answers via the survey, confirm the third sees both in the filter dialog's person picker without restarting the app (Fix 13 verification). Take a quiz, back out mid-way (Fix 20 dialog), then take another quiz and see the per-subject breakdown + profile navigation (Fixes 21 + 22).
- [ ] `CHANGELOG.md` has one bullet per fix under the existing `## UX Fix Pack` section.
