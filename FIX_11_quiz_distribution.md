# Fix 11 — Quiz subject distribution and multi-select

This is an addendum to `FIXES.md`. Apply it **after** Fixes 1–10 are merged. Several of the fixes below touch code that those earlier fixes also touch (especially Fixes 1, 6, and 8), so the diffs are simpler if this one comes last.

## The problem

Users report that when they take a quiz, they get a streak of questions about the same one or two people instead of a balanced mix across the whole group. The cause is in `GTKYRepository.buildQuizSession`: it iterates `shuffledUsers` and, for each subject, appends *all* of that subject's eligible questions (up to 50) before moving to the next subject. The outer `break` only fires once the total hits `count` (30). So if the first shuffled subject has 25 eligible questions, they fill the session before any other subject shows up.

Separately, users want to narrow a quiz to specific people — not just filter by group. Today the only filter axis is group.

## What we're building

1. A new **weighted random** question builder that interleaves subjects across the session, weighted so users who've been quizzed about less come up more often.
2. A **unified quiz filter dialog** that combines group filtering and multi-select person filtering in one place, with a search bar on the person list.
3. Rule: **specific people selected → group filter is ignored** for that session.
4. **Hide** users who haven't hit the survey-answered threshold from the person picker entirely.
5. **Route the Active Users tap-to-quiz (from Fix 8) through the new dialog** with that person pre-selected, instead of starting the quiz directly.
6. **Pre-start pool-size warning** on the dialog so users know how many questions they'll get.

## Implementation

### 11.1 — Weighted random question builder

Replace the body of `buildQuizSession` in `GTKYRepository.kt`. Keep the function signature and the eligibility-filtering logic at the top (the `eligibleUsers` computation from `groupIds`, and the existing `if (eligibleUsers.isEmpty()) return emptyList()` guard). Everything from `val questions = mutableListOf<QuizQuestion>()` downward gets replaced.

The new body works like this:

1. **Build a per-subject pool.** For each user in `eligibleUsers`, gather the list of their answered questions that the current `quizTakerId` has not yet attempted. Use the existing `getAlreadyAttemptedQuestionIds` and `getAnsweredQuestionsForUser(subject.id, 50)` calls. Drop subjects whose pool is empty. The result is `Map<User, MutableList<SurveyQuestion>>` where each list is shuffled up front.

2. **Fetch each subject's "times quizzed" count.** Add a new query to `QuizResultDao`:
   ```kotlin
   @Query("SELECT COUNT(*) FROM quiz_results WHERE quizTakerId = :takerId AND subjectUserId = :subjectId")
   suspend fun getTimesTakerQuizzedSubject(takerId: Long, subjectId: Long): Int
   ```
   Call it once per subject and cache the result in a `Map<Long, Int>` (`subjectId` → count).

3. **Weighted random pick loop.** Run until either `questions.size >= count` or every subject's pool is empty:
   - For each subject still in the pool map with a non-empty list, compute its weight: `weight = 1.0 / (1.0 + timesQuizzed[subjectId])`. A subject the taker has never been quizzed on has weight 1.0; one they've been quizzed on three times has weight 0.25. This produces "fair-share drift" — behind-schedule subjects bubble up without crowding out anyone entirely.
   - Sum the weights; pick a random subject proportional to its weight. (Standard reservoir/cumulative-weight approach: `pick = Random.nextDouble() * totalWeight`, then walk the list subtracting each weight until `pick <= 0`.)
   - Pop one question from that subject's shuffled list. Build and append the `QuizQuestion`.
   - After appending, **do not** increment `timesQuizzed[subjectId]` within the same session. We want the distribution for this session to be based on *historical* quiz load, not self-reinforcing inside one session. Inside-session balance is already handled by the fact that we remove each used question from the pool.
   - If a subject's list becomes empty, remove them from the pool map.

4. **Do not shuffle the final list.** The old code ended with `return questions.shuffled()`. Don't do that here — the weighted-random order *is* the intended order. Shuffling destroys the balancing work.

5. **Preserve the 30-question cap** by using the existing `count` parameter as the loop upper bound.

Also add a companion function `countAvailableQuestions(quizTakerId, groupIds, subjectUserIds)` on the repository. It returns the total size of the pool (sum of all per-subject pool sizes) without actually building the questions. The dialog uses this for the "Only N questions available" warning (section 11.6). Accept the same filter args the full builder does and short-circuit if the pool is zero.

### 11.2 — Extend filter parameters

`buildQuizSession` currently takes `(quizTakerId, groupIds, count)`. Extend it to `(quizTakerId, groupIds, subjectUserIds, count)` where `subjectUserIds: List<Long>` defaults to `emptyList()`.

Eligibility rule at the top of the function, applied in order:

1. If `subjectUserIds` is non-empty, the eligible subject set is exactly those users (filtered by `id != quizTakerId` and the existing ≥threshold answer-count requirement). **Group filter is ignored.**
2. Else, if `groupIds` is empty or contains `0L` (the "all groups" sentinel), use all users meeting the threshold (same as today's `getAllUsersWithMinAnswers` path).
3. Else, use users in the selected groups (today's existing path).

Remove the `buildQuizSessionForSubject` function added in Fix 8 — its job is now done by `buildQuizSession` with `subjectUserIds = listOf(subjectId)`. Update all callers.

### 11.3 — Extend the quiz route

The route from Fix 8 is currently `"quiz/{userId}/{groupIds}?subjectId={subjectId}"`. Change the optional parameter to accept multiple ids:

- Route: `"quiz/{userId}/{groupIds}?subjectIds={subjectIds}"`
- `subjectIds` is a comma-separated string of user ids. Empty or unset means "no person filter."
- In `NavGraph.kt`, register the nav arg with `defaultValue = ""`.
- `QuizViewModel.Factory` takes `subjectIds: List<Long>` (parsed from the comma-separated string). Pass it to `buildQuizSession`.
- Update `Routes.quiz(...)` helper to accept and encode the list.

### 11.4 — Unified filter dialog

Rewrite `QuizGroupPickerDialog` in `HomeScreen.kt` and rename it to `QuizFilterDialog`. Structure:

```
┌─ Quiz — who to quiz about? ────────────┐
│                                        │
│  Filter by group:                      │
│  [ All Groups ] [ Engineering ] [ ... ]│
│                                        │
│  ─────────────────────────────────     │
│                                        │
│  ▸ Pick specific people (0 selected)   │  ← collapsed by default
│                                        │
│  ─────────────────────────────────     │
│                                        │
│  📊 30 questions available             │  ← live-updating
│                                        │
│  [ Cancel ]         [ Start Quiz ]     │
└────────────────────────────────────────┘
```

When the "Pick specific people" section is expanded, it reveals:

```
│  ▾ Pick specific people (2 selected)   │
│  ┌──────────────────────────────────┐  │
│  │ 🔍 Search names...               │  │
│  └──────────────────────────────────┘  │
│  ☑ Alex S                              │
│  ☑ Jamie R                             │
│  ☐ Taylor K                            │
│  ☐ Morgan L                            │
│  ...                                   │
│                                        │
│  Group filter ignored while people     │
│  are selected.                         │  ← only shown when both are set
```

Implementation notes:

- The section expand/collapse state is local to the composable. Default collapsed.
- The person list is **only users with `answerCount >= QUIZ_UNLOCK_THRESHOLD`** and `id != quizTakerId`. Below-threshold users are **hidden entirely** from this list. Not grayed out, not shown with a subtitle — gone.
- If the person list is empty (nobody else is ready), collapse is disabled and the section header reads `"Pick specific people (nobody ready yet)"` in a muted color. The dialog should still be usable in group-only mode in this case.
- Search filters the person list case-insensitively on `user.name.contains(query)`. Same pattern as `PickUserScreen`.
- The dialog state needs: `selectedGroupIds: Set<Long>`, `selectedPersonIds: Set<Long>`, `expanded: Boolean`, `searchQuery: String`.
- The "Group filter ignored" hint shows only when `selectedPersonIds.isNotEmpty() && !allGroupsSelected` — i.e., both filters are "engaged" in the user's mind.
- The "Start Quiz" button emits the two sets to the caller. Encode subject ids as comma-separated string for the route. Pass `"0"` for group ids when person-select is non-empty (since groups are ignored in that case), to keep the existing group-encoding convention simple.

Pass the eligible-user list into the dialog from `UserHomeScreen`. `HomeViewModel` already exposes `allUsers`; add a `quizzableUsers` derived flow that filters it:

```kotlin
// HomeViewModel.kt
val quizzableUsers: StateFlow<List<User>> = combine(allUsers, ...) { users, ... ->
    users.filter { it.id != currentUserId && answerCount(it.id) >= Constants.QUIZ_UNLOCK_THRESHOLD }
}
```

You'll need to expose per-user answer counts. Simplest: add `getQuizzableUsers(excludeUserId: Long): Flow<List<User>>` to the repository that does the filtering server-side and re-emits when survey answers change. Implement it as a `combine` of `getAllUsers()` and a flow that re-queries counts when the `survey_answers` table changes.

### 11.5 — Route Active Users tap through the new dialog

Fix 8 had the Active Users row tap open a confirmation dialog (`"Quiz yourself about ${name}?"`) and then navigate straight to the quiz. Change this:

- Remove the confirmation `AlertDialog` in `ActiveUsersScreen`.
- On tap of a quiz-eligible user, navigate back to home **and** open `QuizFilterDialog` with that user pre-selected in `selectedPersonIds`.

Implementation: add a one-shot "pre-selected subject" flag to `HomeViewModel`. In `NavGraph.kt`'s Active Users block, the tap handler calls `navController.popBackStack(Routes.HOME, inclusive = false)` and then (via a shared ViewModel scope or a simple `HomeViewModel.requestQuizWithSubject(userId: Long)` call) sets the flag. `UserHomeScreen` watches for the flag and auto-opens the dialog with the user pre-checked, then clears the flag. The person-select section should be expanded by default in this case so the user sees what's pre-selected.

### 11.6 — Live pool-size warning

At the bottom of `QuizFilterDialog`, above the action buttons, render a live-updating count line.

- Compute the count by calling `repo.countAvailableQuestions(...)` whenever the dialog's selection state changes. Debounce by ~200ms to avoid thrashing on rapid chip taps.
- The view-model-less way to do this: lift the computation into `HomeViewModel` with a `filterPreviewState: StateFlow<FilterPreview>` and call `homeViewModel.updateFilterPreview(groupIds, personIds)` from the dialog. `FilterPreview` carries `availableQuestions: Int`.
- Copy variants:
  - `availableQuestions == 0` → `"No questions available for this selection"` in error color. Disable the Start button.
  - `availableQuestions in 1..9` → `"⚠ Only $availableQuestions questions available"` in a warning color.
  - `availableQuestions in 10..29` → `"$availableQuestions questions available"` in the default color.
  - `availableQuestions >= 30` → `"30 questions in this session"` (since 30 is the cap, there's no point teasing "52 available").
- All four copies get Spanish translations.

### 11.7 — Remove the old group-picker footgun behaviors

While we're in this dialog:

- Remove the silent "if you deselect everything it flips back to All Groups" behavior that Fix 10.2 flagged. In this dialog, if no groups are selected and no people are selected, the effective scope is "all eligible users." That's fine — just disable the Start button if the resulting pool is 0 (handled by 11.6).
- The old "All Groups" chip is redundant in the new dialog — if the user has nothing selected and no people picked, they're already "all groups" by default. Replace the "All Groups" chip with a small "Clear" text button next to the group chip row that clears group selection. Skip this change if it makes the diff too large — leaving the "All Groups" chip in place is acceptable, just make sure its behavior is consistent with the new logic.

## Testing

### Unit tests

Add `app/src/test/java/com/gtky/app/data/repository/QuizSessionTest.kt` with these cases. Use fake/in-memory DAO implementations or mocks.

1. **Distribution test.** 5 eligible subjects, each with 20 questions in their pool, zero prior quiz history for any of them. Build a 30-question session. Assert that every subject appears at least 4 times and no subject appears more than 8 times. (Weighted random with equal weights should cluster around 6, so 4–8 is a generous bound.)

2. **Underdog weighting.** Subject A has been quizzed 10 times by this taker; subject B has been quizzed 0 times. Both have 20 questions in their pools. Build a 20-question session. Assert subject B appears at least 2x more often than subject A. (With weights `1/11` and `1/1`, B is 11x more likely — so 2x is a very conservative floor even with small sample size.)

3. **Pool exhaustion.** 2 subjects, each with 3 questions in their pool. Build a 30-question session. Assert the result has exactly 6 questions and that each subject appears 3 times.

4. **Subject override ignores groups.** Pass `groupIds = listOf(1L)` and `subjectUserIds = listOf(42L)` where user 42 is not in group 1 but is otherwise eligible. Assert user 42 is the only subject in the session.

5. **Count parity.** `countAvailableQuestions` should return the same number that `buildQuizSession(..., count = Int.MAX_VALUE)` produces, for the same filters. Test with a mix of filter configurations.

### Manual test

Fresh install, seeded with 4+ users who each answered 10+ survey questions:

1. Take a quiz with no filters. Count how many distinct subjects appear in the first 10 questions — should be at least 3. Run it twice; confirm it doesn't produce the same sequence.
2. Take a quiz about one specific person. Confirm every question is about that person.
3. Take a quiz with group X selected AND one specific person (not in group X) selected. Confirm the session includes only that one person. Confirm the "Group filter ignored" hint shows in the dialog.
4. Pick a filter that produces fewer than 10 results. Confirm the warning copy appears.
5. Pick a filter that produces 0 results. Confirm the Start button is disabled.
6. Tap a user row on Active Users. Confirm you return to home with the filter dialog open and that user checked in the person list.
7. Confirm below-threshold users never appear in the person list.
8. Go to a user who has taken several quizzes about person A but none about person B. Take a no-filter quiz. Confirm person B appears more often than person A. (This is a soft check — single-session variance is high.)

## Changelog entry

Add to `CHANGELOG.md`:

- Quiz sessions now interleave subjects using weighted random, biased toward people you've been quizzed on less. Fixes clustering where one or two people dominated a whole session.
- New unified quiz filter dialog: filter by group, by specific people (with search), or both. Selecting specific people overrides the group filter.
- Active Users tap now opens the filter dialog with the person pre-selected, so you can add more people before starting.
- Pre-start pool-size warning tells you how many questions are available for your filter.
