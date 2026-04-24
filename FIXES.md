# GTKY — UX Fix Pack

This document describes a set of user-experience fixes for the GTKY Android app. Each fix is self-contained and can be shipped independently, but they are ordered roughly by impact. Work through them top to bottom. For each one: make the change, build the app (`./gradlew :app:assembleDebug`), and do a quick manual test of the flow before moving on.

**Project facts you should know before starting:**
- Single-module Android app, Kotlin + Jetpack Compose + Room.
- Package root: `com.gtky.app`. Source under `app/src/main/java/com/gtky/app/`.
- UI is Compose-only. Screens live in `ui/screens/`, state in `viewmodel/`, data in `data/`.
- This is a **shared-tablet kiosk app** for in-person events. `MainActivity` hides system bars and `IdleTimeout.kt` resets navigation after 3 minutes of inactivity. Treat "user" as "whoever is currently holding the device," not "a persistent account owner." Several of the fixes below hinge on this.
- The app has a bilingual toggle (en/es). Every user-visible string must be added to both languages using the existing `t("English", "Español")` helper from `ui/LocalAppLanguage.kt`.
- Default admin PIN is `"1234"`, seeded in `DataSeeder.seedIfNeeded`.

**Ground rules for all fixes:**
- Do not introduce new architectural patterns. Keep Compose + StateFlow + ViewModel as used today.
- Do not add new dependencies without justifying it in the PR description.
- Keep every user-facing string bilingual via `t(...)`.
- After each fix, leave a short note in a new `CHANGELOG.md` under a `## UX Fix Pack` section.
- Do not break existing migrations. If a change requires a Room schema change, bump the DB version and write a migration; do not wipe user data.

---

## Fix 1 — Quiz unlock is too high and too opaque

**Problem.** A new user cannot use the Quiz feature until they have answered 15 survey questions *and* at least one other user has also answered 15. The home screen only tells them about the first condition ("Answer N more survey questions to unlock"), not the second. Users who grind through 15 questions often still hit the `NoEligibleUsersContent` screen and have no idea why. This is the single biggest drop-off point in the current app.

**Goal.** Lower the threshold, make progress visible during the survey, and make the "others also need to finish" precondition explicit on the home screen.

**Changes.**

1. **Introduce a single source of truth for the threshold.** Create a file `app/src/main/java/com/gtky/app/Constants.kt` with:
   ```kotlin
   package com.gtky.app
   object Constants {
       const val QUIZ_UNLOCK_THRESHOLD = 8
       const val QUIZ_MIN_QUESTIONS_BEFORE_FINISH = 5
   }
   ```
   Replace every hardcoded `15` that refers to the quiz-unlock threshold with `Constants.QUIZ_UNLOCK_THRESHOLD`. This appears in at least: `HomeScreen.kt`, `SurveyScreen.kt`, `SurveyViewModel.kt`, `ActiveUsersViewModel.kt` (`isEligible` computation), `GTKYRepository.kt` (`getAllUsersWithMinAnswers`), and the `NoEligibleUsersContent` copy in `QuizScreen.kt`. Do a project-wide search for `15` and audit each occurrence; only change the ones related to the unlock threshold. Do **not** change the `5` in `QuizViewModel` that controls minimum-before-finish; move that to `QUIZ_MIN_QUESTIONS_BEFORE_FINISH` as well for consistency.

2. **Add a `readyCount` to the home screen state** so the home screen can tell the user "N of your groupmates are ready to be quizzed about." In `HomeViewModel.kt`, extend `HomeUiState.UserSelected` with `val readyCount: Int` (count of users other than the current user who have `answerCount >= QUIZ_UNLOCK_THRESHOLD`). Compute it in `transitionToUserSelected` by combining `repo.getAllUsers()` and a per-user count query. Add a repository function:
   ```kotlin
   // GTKYRepository.kt
   suspend fun getReadyUserCount(excludingUserId: Long): Int {
       val users = db.userDao().getAllUsers().first()
       return users.count { it.id != excludingUserId &&
           db.surveyAnswerDao().getAnswerCountForUserSync(it.id) >= Constants.QUIZ_UNLOCK_THRESHOLD }
   }
   ```
   Note: `getAnswerCountForUserSync` already exists (used by `getAllUsersWithMinAnswers`). Use it.

3. **Update the home screen's "Take a Quiz" button subtitle** (`UserHomeScreen` in `HomeScreen.kt`) with a three-state message:
   - If `answerCount < threshold`: `"Answer ${threshold - answerCount} more to unlock"` (as today).
   - Else if `readyCount == 0`: `"Quiz unlocks when 1+ other person finishes their intro"`.
   - Else: no subtitle (button is enabled).
   Enable the button only when `answerCount >= threshold && readyCount >= 1`. Provide Spanish translations for both new strings.

4. **Add a progress bar + category hint to the survey.** In `SurveyScreen.kt`, under the top app bar, add a `LinearProgressIndicator` that shows `totalAnswered / QUIZ_UNLOCK_THRESHOLD` capped at 1f until the threshold is reached, then switches to an indeterminate/full state with the "Keep Going!" label. Below it, show the current question's `category` (from the `SurveyQuestion`) as a small label, e.g., "FOOD". Translate category labels via a new `categoryLabel(category: String, lang: String)` helper — it's fine to map only the distinct categories that actually appear in `DataSeeder` (Food, Travel, Personality, Entertainment, Lifestyle, etc.).

5. **Add a milestone toast at the threshold.** In `SurveyViewModel.submitAnswer`, after saving, if `totalAnswered + 1 == QUIZ_UNLOCK_THRESHOLD`, set a transient `justUnlockedQuiz: Boolean` flag in state. In `SurveyScreen`, when that flag flips true, show a small centered `Snackbar`/`Card` overlay for ~2.5s with copy like `"Quiz unlocked! Keep going or tap Finish."` Then clear the flag. Don't block interaction — the next question should still be visible behind it.

**Manual test.** Fresh install (clear app data). Create a user. Confirm the home screen shows `"Answer 8 more to unlock"`. Answer 8 questions; confirm the unlock toast appears, and confirm the home screen now says `"Quiz unlocks when 1+ other person finishes their intro"` because no other user has finished. Create a second user on another device or via the user picker, finish 8 questions for them, switch back; confirm the Quiz button is now fully enabled.

---

## Fix 2 — "Connections" shows a global leaderboard instead of the user's own connections

**Problem.** `ConnectionsScreen` shows pairs of *any* two users in the app, ranked by mutual knowledge. The active user is not the anchor. `ConnectionsViewModel` even fetches `activeUserId` but never uses it. For an icebreaker app, a user opening "Connections" expects to see *their own* connections to others.

**Goal.** Default the screen to "My Connections" (pairs involving the active user). Keep the current global view as an opt-in toggle.

**Changes.**

1. **Extend `ConnectionMode`** in `ConnectionsViewModel.kt` to add a third value, making it: `enum class ConnectionScope { MINE, EVERYONE }`. Keep the existing `MUTUAL` / `ONE_WAY` enum but rename it to `ConnectionDirection` for clarity. `ConnectionsUiState` should now carry both a `scope: ConnectionScope = ConnectionScope.MINE` and a `direction: ConnectionDirection = ConnectionDirection.MUTUAL`.

2. **Filter entries by scope in the ViewModel.** In `loadConnections`, after `repo.getAllConnectionEntries(users)`, if `scope == MINE` and `activeUserId != null`, keep only entries where `entry.userA.id == activeUserId || entry.userB.id == activeUserId`. Expose `setScope(scope: ConnectionScope)` and `setDirection(direction: ConnectionDirection)`.

3. **Update `ConnectionsScreen.kt`** to show two controls stacked in the filter row:
   - Top row: scope toggle — segmented button with two options, `"My Connections"` (default, selected) and `"Everyone"`.
   - Bottom row: the existing direction toggle (Mutual / One-Way), unchanged.
   - When scope is `MINE` and the active user's name is known, render rows from the active user's perspective. For mutual rows, rewrite the primary label to `"You & $otherName"` and the secondary `"you → $otherName: X%"` / `"$otherName → you: X%"`. For one-way rows in `MINE` scope, show only rows where `from == activeUser` or `to == activeUser`, labeled `"You know $name"` or `"$name knows you"` accordingly.
   - Empty state when `scope == MINE` and the list is empty: `"You haven't been in any quizzes yet. Take a quiz, or wait for someone to take a quiz about you."` Replace today's generic empty state copy.

4. **Do not remove the "Everyone" view.** Admins and curious users will still want it, and it's already built. Just make it the non-default.

5. Provide Spanish translations for all new strings.

**Manual test.** As a user who has just taken one quiz about one other person, confirm the Connections screen now shows exactly one row with you as the anchor ("You & Jamie"). Switch to "Everyone" and confirm the global view still works. Switch to "One-Way" within "My Connections" and confirm you see a directional row labeled "You know Jamie" (and if Jamie has quizzed about you, a second row labeled "Jamie knows you").

---

## Fix 3 — Idle timeout keeps the previous user signed in

**Problem.** `IdleTimeout` navigates back to the home route after 3 minutes of inactivity, but `MainActivity` does not call `signOut()`. On a shared event tablet, the next person who picks up the device is still authenticated as the previous user. New answers and quizzes are silently attributed to the wrong person. There's also no persistent indicator of who is signed in.

**Goal.** On idle, return to the Welcome screen with no active user. And regardless of idle, make the signed-in identity extremely obvious.

**Changes.**

1. **Sign out on idle.** In `MainActivity.kt`, the `onIdle` callback currently only navigates. Make it also clear the active user. You can't call the repository from a Composable directly, so expose a top-level coroutine on the `GTKYApplication` (e.g., `fun handleIdleTimeout()` that launches in `applicationScope` and calls `repository.clearActiveUser()` then re-emits the language state if needed). Call that from `onIdle` *before* the navigation. The existing `HomeViewModel` already reacts to active-user changes on load; confirm that a fresh navigation to `Routes.HOME` re-runs `loadActiveUser()` and drops into `HomeUiState.NoUser`. If it does not (because of ViewModel caching), add a `refresh()` function to `HomeViewModel` and call it from the `composable(Routes.HOME)` block via a `LaunchedEffect(navController.currentBackStackEntry)`.

2. **Shorten the idle timeout to 90 seconds.** Change the `timeoutMs` default in `IdleTimeout` from `3 * 60 * 1000L` to `90_000L`. Three minutes is too long for a shared device at a busy event — if someone walks away mid-survey, the next person could tap the screen within that window and answer the first person's survey.

3. **Add a persistent "Signed in as" banner on the home screen.** In `UserHomeScreen` (`HomeScreen.kt`), replace the large centered `"Hey, ${user.name}!"` with a small pill at the very top of the screen (above the buttons) reading `"Signed in as ${user.name}"` with a small `"Not you?"` text button to the right that calls `onSignOut`. Keep a smaller centered greeting below if you like the warmth, but the pill is the primary identity affordance. This makes it unmissable when a new person picks up the device.

4. **Add a confirmation dialog on Switch User.** The current `TextButton(onClick = onSignOut)` at the bottom-left of the home screen is a one-tap footgun. Wrap the sign-out action in an `AlertDialog` that asks `"Switch user? You can sign back in from the picker."` with `Switch` and `Cancel` buttons. Apply the same treatment to the "Not you?" button from change 3.

5. **Reset quiz/survey sessions on idle.** If idle fires while a user is mid-quiz or mid-survey, the `QuizViewModel.pendingResults` or `SurveyViewModel.pendingQuestions` may still be in memory. The navigation pop should already tear them down, but verify: after an idle reset and a new user signing in, starting a new quiz should produce a fresh session. If not, ensure the `composable(Routes.QUIZ)` and `composable(Routes.SURVEY)` blocks use a distinct key per userId so the ViewModel is recreated.

**Manual test.** Sign in as User A, answer one survey question, then leave the device alone for 95 seconds. The screen should return to the Welcome screen with the name fields empty. Sign in as User B, tap "Answer Survey Questions," and confirm the first question shown is a fresh one (not the next-in-queue for User A) and that submitting it saves under User B. Check the "Signed in as" banner is present throughout.

---

## Fix 4 — Dead-end empty states

**Problem.** Several screens drop users into blank states with no suggested next action. This includes Connections with no data, Quiz with no eligible users, Groups with no groups, and Survey "all answered." Each should propose the next best thing.

**Goal.** Every empty state gets a primary action button and copy that explains *why* it's empty.

**Changes.** Edit each empty state below. All new strings must be bilingual.

1. **Connections empty (MINE scope) — `ConnectionsScreen.kt`.** Replace the generic "No connections yet" box with:
   - Headline: `"No connections yet"`
   - Body: `"Connections appear when you take a quiz about someone, or when they take one about you."`
   - Primary button: `"Take a Quiz"` → pops back to home, then programmatically triggers the quiz group picker. Implement by adding an `onGoToQuiz: () -> Unit` param to `ConnectionsScreen`, wiring it in `NavGraph.kt` to pop back to home, and having the home screen honor a one-shot `shouldOpenQuizPicker` flag. (Simpler alternative: just pop back to home and show a snackbar "Tap Take a Quiz." Pick whichever is less code.)
   - Secondary text: `"Answer more survey questions"` — also pops to home.

2. **Quiz no eligible users — `QuizScreen.NoEligibleUsersContent`.** Replace current body with:
   - `"Nobody else is ready to be quizzed yet. Other players need to answer at least $QUIZ_UNLOCK_THRESHOLD questions first."`
   - Show the count of "players close to ready" if available: `"N players are almost there."` To compute this, pass a `closeCount` from `QuizViewModel` — when `buildQuizSession` returns empty, count users whose `answerCount` is between `threshold/2` and `threshold - 1`.
   - Two buttons: `"Answer more questions"` (navigates to survey with the current user id — requires passing `onGoToSurvey` into `QuizScreen` via `NavGraph`), and `"Back Home"`.

3. **Groups empty — `GroupsScreen.kt`.** Replace `"No groups yet. Ask an admin to create one."` with the same copy plus a visible hint line: `"The admin PIN unlocks group creation on the home screen → Admin."` Add a secondary `"Continue without groups"` text button that pops back to home. (Groups are optional; the app works fine without them.)

4. **Survey all-answered — `AllAnsweredContent` in `SurveyScreen.kt`.** Today this is a terminal "Check back later." Replace with:
   - Headline unchanged.
   - Body: `"You're fully set up. Now go learn about everyone else."`
   - Primary button: `"Take a Quiz"` → pops back and opens quiz picker (same pattern as Fix 4.1).
   - Secondary button: `"Back Home"`.

5. **Active Users empty — `ActiveUsersScreen.kt`.** Current copy is already acceptable ("No users yet. Add your name on the home screen!") since the active user themselves exists by the time they reach this screen. Leave as is.

**Manual test.** Walk each empty state manually (easiest is a fresh install for Quiz/Connections; delete all groups via admin for Groups). Confirm each now has a clear primary CTA and the copy explains the situation.

---

## Fix 5 — Name collisions and identity recovery

**Problem.** The first-run form asks for first name + last initial. In a group of 15+ people, "Alex S" collisions are near-certain. The current flow blocks exact duplicates with `"That name is already taken"` but gives no guidance. The "I'm already here — pick my name" list shows duplicate-looking rows with no disambiguator. There's no way to edit a name after creation except by admin delete + recreate.

**Goal.** Prevent duplicate confusion on entry, give the user a recovery path, and disambiguate the picker.

**Changes.**

1. **On duplicate name, suggest a disambiguator.** In `HomeViewModel.createAndSelectUser`, when `existing != null`, don't just set an error. Enter a new state `HomeUiState.DuplicateName(firstName: String, lastName: String, collidingUser: User)`. Render a new dialog/inline card on the Welcome screen that says:
   - `"There's already an ${collidingUser.name} here."`
   - `"Are you them?"` → button `"Yes, that's me"` → calls `selectExistingUser(collidingUser)`.
   - `"No, I'm different"` → button `"Add a middle initial or more of your last name"` → returns to the form with focus on the last-name field, pre-filled with the existing value, so the user types one more character.
   - Keep the bilingual strings.

2. **Disambiguate the picker.** In `PickUserScreen.kt`, when two or more users share the same visible name (case-insensitive), append their creation order suffix (`"(1)"`, `"(2)"`) next to the name. You don't need to persist this — compute it at render time by grouping `users` by `name.lowercase()` and tagging duplicates with their index-within-group. Only add the suffix when there's a collision; a unique name shows cleanly.

3. **Add "Edit my name" to the home screen.** Put a small edit icon next to the "Signed in as ${user.name}" banner (from Fix 3.3). Tapping it opens an `AlertDialog` with a single `OutlinedTextField` prefilled with the current name and `Save` / `Cancel`. On save, validate uniqueness the same way create does. Add a `renameUser(userId: Long, newName: String)` function to the repository (just `userDao().updateName(...)` — you'll need to add `@Query("UPDATE users SET name = :name WHERE id = :id")` to `UserDao`).

4. **Protect against wrong-row taps on the picker.** When a user taps a row in `PickUserScreen`, before calling `selectExistingUser`, show an `AlertDialog`: `"Sign in as ${user.name}?"` with `Yes` / `Cancel`. This is a small friction, but on a shared device it prevents the "oh no, I tapped the wrong Alex S" class of bugs, which currently has no recovery short of admin intervention.

**Manual test.** Create a user "Alex S". Try to create another "Alex S" — confirm you see the "Are you them?" prompt. Choose "No, I'm different," add an "m" to make it "Alex Sm," submit, confirm both users now exist and appear distinguished in the picker ("Alex S" and "Alex Sm" — no suffix needed since they differ). Now create another "Alex S" (same as first). Confirm picker shows "Alex S (1)" and "Alex S (2)". Test the rename dialog from the home screen. Test the picker confirmation dialog.

---

## Fix 6 — Quiz flow polish

**Problem.** Several small frictions in the quiz experience compound: no question counter, silent person-skip with no feedback, two taps per question on the answer-reveal screen, and no preview of who you'll be quizzed about.

**Goal.** Make the quiz feel snappier and more transparent.

**Changes.**

1. **Show a real progress indicator.** In `QuizScreen.kt`, replace the current `"Remaining: N"` / `"Keep Going!"` text with a compact `"Question ${answeredCount + 1} of ${state.questions.size}"` label plus a thin `LinearProgressIndicator` under the top app bar driven by `state.progress`. Keep the `"Keep Going!"` coloring/label as a small badge only when `canFinish` is true.

2. **Confirm person-skip with a brief toast.** In `QuizViewModel.skipPerson`, expose a one-shot flag `justSkippedPerson: String?` in state carrying the skipped user's name. In `QuizScreen`, render a `Snackbar` (or simple animated `Card` that fades in/out over ~1.5s) with copy `"Skipped $name"` when the flag is non-null, then clear it. Do the same disappear-without-user-input pattern as Fix 1.5.

3. **Auto-advance after a correct answer.** When `isAnswerRevealed && selectedAnswer == correctAnswer`, start a 1400ms delay (a `LaunchedEffect(q, selectedAnswer)` that `delay(1400)` then calls `onNext`). Show a small "Next →" affordance during the delay so the user knows they can tap to skip the wait. On a wrong answer, do NOT auto-advance — users want time to read the correct answer. Leave the `"Next Question"` button for that case.

4. **Preview the subject pool on the quiz group picker.** In `HomeScreen.kt`, inside `QuizGroupPickerDialog`, below the filter chips, add a small line: `"$N people ready to quiz about"` where N is computed from the selected groups. This requires passing the ready-by-group map to the dialog — simplest approach: `HomeViewModel` already has `allUsers`; extend it to hold a `readyUserIdsByGroup: Map<Long, Set<Long>>`. Compute it when groups or users change. The dialog consumes it to show the count. Also show a single-line preview of the first 3 names: `"Alex, Jamie, Taylor, and 5 more"`.

5. **Make the subject name visible while reading the question.** The subject is already in a primary-container card at the top. Additionally, put the subject name in `TopAppBar.title`: `title = { Text(t("Quiz — about ${subjectName}", "Quiz — sobre ${subjectName}")) }`. This survives scroll and is the single most important piece of context.

**Manual test.** Take a quiz. Confirm the progress indicator fills smoothly. Get one correct and one wrong, confirm correct auto-advances and wrong waits for your tap. Tap the "haven't met" link and confirm you see a "Skipped Jamie" toast before the next question. Open the quiz group picker and confirm you can see the participant count and sample names.

---

## Fix 7 — Survey questions read ungrammatically in the first person

**Problem.** Seed questions use third-person templates like `"Does [NAME] prefer coffee or tea?"`. In the survey, `SurveyScreen` currently substitutes `[NAME]` with `"you"` and capitalizes the first letter, producing **`"Does you prefer coffee or tea?"`** — ungrammatical. Users see a stream of these during the most attention-demanding first-run session.

**Goal.** The survey reads naturally in the second person without authoring a parallel question set.

**Changes.**

1. **Write a template transformer for the survey's first-person view.** Create a new file `app/src/main/java/com/gtky/app/util/QuestionPhrasing.kt`. Export two functions:
   ```kotlin
   fun forSurvey(template: String, lang: String): String
   fun forQuiz(template: String, subjectName: String): String = template.replace("[NAME]", subjectName)
   ```
   For English, `forSurvey` should apply these ordered regex rewrites to the template, then return:
   - `^Does\s+\[NAME\]\s+` → `"Do you "`
   - `^Is\s+\[NAME\]\s+` → `"Are you "`
   - `^Has\s+\[NAME\]\s+` → `"Have you "`
   - `^Would\s+\[NAME\]\s+rather\s+` → `"Would you rather "`
   - `^Can\s+\[NAME\]\s+` → `"Can you "`
   - `^Will\s+\[NAME\]\s+` → `"Will you "`
   - `\[NAME\]'s\s+` → `"your "`
   - Any remaining `\[NAME\]` → `"you"`
   - Uppercase the first character.

   For Spanish (`lang == "es"`), apply parallel rules. The most common seed pattern is `"¿[NAME] prefiere ... o ...?"` and `"¿[NAME] preferiría ..."`. Rules:
   - `^¿\[NAME\]\s+prefiere\s+` → `"¿Prefieres "`
   - `^¿\[NAME\]\s+preferiría\s+` → `"¿Preferirías "`
   - `^¿\[NAME\]\s+` → `"¿"`
   - `\[NAME\]\s+es\s+` → `""`  (rare, handle if present)
   - Any remaining `\[NAME\]` → `"tú"`
   - Keep the leading `¿` and trailing `?`.

   Don't try to be clever. These rewrites cover the overwhelming majority of the seeded question set. Any edge case that still produces `[NAME]` should fall through to `"you"` / `"tú"`. Add a small unit test file in `app/src/test/` for this transformer — feed it 20 representative templates from `DataSeeder` and assert the outputs read naturally.

2. **Use the transformer in `SurveyScreen.kt`.** Replace the inline `template.replace("[NAME]", t("you", "ti")).replaceFirstChar { it.uppercase() }` block with a call to `forSurvey(template, language)`.

3. **Audit the quiz side.** `QuizScreen.kt` already does `template.replace("[NAME]", subjectName)` which reads fine ("Does Alex prefer..."). No changes needed there, but route through `forQuiz(...)` for symmetry.

**Manual test.** Run the survey in English and then Spanish. Read every question out loud for the first two categories. Any that reads oddly is a rule to add. Commit only after at least 20 consecutive questions read cleanly in each language.

---

## Fix 8 — Active Users screen is a dead leaderboard

**Problem.** `ActiveUsersScreen` shows each user's answer count and a quiz-eligible checkmark, but rows are not tappable. The most obvious gesture on any list of people in a get-to-know-you app is "tap to see them or quiz about them." Today nothing happens.

**Goal.** Tapping a quiz-eligible user starts a mini-quiz focused on that one person.

**Changes.**

1. **Add a `buildQuizSessionForSubject` in `GTKYRepository`** that takes `quizTakerId: Long, subjectUserId: Long, count: Int`. It's a stripped-down version of `buildQuizSession` that uses a single subject instead of iterating `shuffledUsers`. Reuse the same "already attempted questions" exclusion logic.

2. **Extend the quiz route.** Add an optional subject id to the quiz route: `"quiz/{userId}/{groupIds}?subjectId={subjectId}"`. In `NavGraph.kt`, register the optional argument (`navArgument("subjectId") { type = NavType.LongType; defaultValue = -1L }`). `QuizViewModel.Factory` gains a `subjectId: Long` parameter; when it's >= 0, call `buildQuizSessionForSubject` instead of `buildQuizSession`.

3. **Wire up tap handling in `ActiveUsersScreen`.** Make each row `clickable` when `userWithCount.isEligible` and the row is not the current user. On tap, show an `AlertDialog`: `"Quiz yourself about ${name}?"` with `Start` / `Cancel`. Tapping `Start` navigates to the quiz route with `subjectId` set. Non-eligible rows remain unclickable but get a subtle `"Still setting up…"` sub-label instead of today's blank secondary row (only if they are below threshold).

4. **Handle the edge case in `QuizScreen` empty state.** If `subjectId` was specified but `buildQuizSessionForSubject` returned no questions (e.g., the current user has already answered every question about that person), show a custom empty state: `"You've already aced every question about $name!"` with a `"Back"` button.

**Manual test.** With at least two users who have cleared the threshold, tap a row in Active Users. Confirm the subject-specific quiz launches. Take the quiz; confirm every question is about that subject. Confirm the "already aced" empty state by taking the same subject-quiz twice in a row.

---

## Fix 9 — Admin PIN and first-run safety

**Problem.** Default admin PIN is `"1234"` and is never forced to change. "Change PIN" is buried inside the Admin screen, which you can't reach without the PIN, so the default is sticky.

**Goal.** Force a PIN change on first admin access.

**Changes.**

1. **Track whether the default PIN is still in use.** In `DataSeeder.seedIfNeeded`, when setting the default `"1234"`, also set an `AppConfig("admin_pin_is_default", "true")`. Clear that flag whenever `setAdminPin` is called with a non-default value (add the logic in `GTKYRepository.setAdminPin`). The flag, not the literal string `"1234"`, is the source of truth — an admin may legitimately change back to `"1234"`, and we shouldn't keep nagging them once they've consciously set it.

2. **Force change on first authenticated entry.** In `AdminViewModel`, after `authenticate` succeeds, read `admin_pin_is_default`. If true, set a `mustChangePin: Boolean` flag in `AdminUiState`. In `AdminScreen.kt`, when `isAuthenticated && mustChangePin`, render the `ChangePinDialog` immediately and do not allow dismiss (hide the dismiss button and `onDismissRequest = {}`). Only clear the dialog after a successful change.

3. **Clarify copy in the default-PIN state.** On the PIN entry screen, if the default flag is still set, show a small hint: `"First time? Default PIN is 1234. You'll be asked to change it."` This is a security trade-off: showing the default makes it easier for anyone to enter admin on first run, but the forced change on entry protects against long-term misuse. Worth it for the kiosk context.

**Manual test.** Fresh install. Tap Admin → enter `1234` → confirm the change-PIN dialog appears immediately with no dismiss. Change to a new PIN. Log out, re-enter with the new PIN, confirm the forced dialog does not appear. Change back to `1234` manually; confirm the dialog does not appear (flag was cleared).

---

## Fix 10 — Small cuts

Group these into one commit.

1. **Pluralize members in Spanish correctly.** In `GroupsScreen.kt`, the Spanish member-count uses a ternary on the number. Confirm it matches Spanish rules (`1 miembro`, `2 miembros`) — the current code handles this. Apply the same treatment to `HomeScreen`'s "You've answered $answerCount question(s)" and `AdminScreen`'s `"$count answers"` which is currently English-only. Use a small helper `plural(count, singularEn, pluralEn, singularEs, pluralEs)`.

2. **Clean up the quiz group picker's "all vs empty" state.** In `HomeScreen.QuizGroupPickerDialog`, today's logic silently flips back to "All Groups" when the user deselects the last group. Instead, disable the `"Start Quiz"` button and show `"Pick at least one group"` in the error slot. Users should know their intent was ignored, not silently changed.

3. **Skip-on-signup footgun.** In the signup `GroupPickerDialog` in `HomeScreen.kt`, the "Skip" button passes `emptySet()`. That's fine, but add a one-line hint below the list: `"You can join groups later from the Groups screen."` so skipping doesn't feel like a dead end.

4. **Language toggle placement.** On `HomeScreen.WelcomeScreen`, the `LanguageToggle` sits in a `Box` layered behind the form. On small screens the tap target may overlap the first text field. Wrap the form `Column` in a `Column` where the `LanguageToggle` is the first child in a `Row` aligned to `End`, then the rest below — same visual, cleaner layering.

5. **`HomeScreen` duplicate suppression.** In `HomeScreen`, the `HomeUiState.PickGroups` branch renders `WelcomeScreen` *and* overlays `GroupPickerDialog`. This means the welcome form is momentarily visible behind the dialog after a successful signup. Hide the welcome content by returning early with just the dialog and a solid background, or by rendering a neutral `"Setting up..."` placeholder behind the dialog.

**Manual test.** Eyeball each screen. Change language mid-session a couple of times and confirm nothing jumps. Try the quiz picker with all groups deselected.

---

## Shipping checklist

- [ ] All new user-facing strings have Spanish translations.
- [ ] Default admin PIN still seeds to `"1234"` but `admin_pin_is_default` guards the forced-change flow.
- [ ] `./gradlew :app:assembleDebug` passes.
- [ ] `./gradlew :app:testDebugUnitTest` passes (including the new `QuestionPhrasing` test from Fix 7).
- [ ] Manual smoke test of the full happy path on a real device: create user → answer 8 questions (unlock toast fires) → take quiz (auto-advance works on correct answers) → view connections (defaults to "My Connections") → sign out and walk away for 95s → confirm idle sign-out returns to Welcome.
- [ ] `CHANGELOG.md` has one bullet per fix.
