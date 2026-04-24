# GTKY — Fix 28: Welcome screen rework + similar-name detection

Single-fix pack. Builds on Fix 27 (name normalization) which should already be landed.

## The problem

Two related failure modes observed at the first event:

1. **Returning users re-entered their names.** The welcome screen's form was the hero; the "I'm already here — pick my name" button sat below as a small secondary action. Users at the event, especially returning ones, missed it and typed their name a second time, creating a duplicate account.

2. **The existing duplicate check only catches exact matches.** After Fix 27's normalization, `"Alex S"` and `"alex s"` collide correctly. But `"Alex S"` and `"Alex Smith"` do not — so a user who entered `"Alex Smith"` earlier and comes back typing `"Alex S"` creates a second account with no warning.

## What we're building

1. A restructured welcome screen with two equal-weight entry points: **"I'm new here"** and **"I'm already here"**. First-time users see both, pick one, and only then see the form or the picker. No more form-first bias.

2. A **similar-name check** on submit that catches the common event re-entry patterns: same first name + prefix relationship on the last name (e.g., `"Alex S"` matches `"Alex Smith"`), and same first name + same last-initial.

3. A **candidate-picker popup** that shows all matching users when a submit would likely collide. If there's one match it looks like the existing DuplicateName dialog. If there are multiple, they're listed as tappable rows.

Ground rules unchanged: bilingual strings via `t()`, no new dependencies, one fix = one branch = one commit (`fix/28-welcome-rework`), add a bullet to `CHANGELOG.md`.

## Implementation

### 28.1 — Welcome screen: landing → new-user form, landing → returning-user picker

Rework `HomeScreen.WelcomeScreen` into a three-state local UI: `LANDING` (default), `NEW_USER_FORM`, or `PICKER_INLINE`. Only one is visible at a time. The existing `NoUser` / `DuplicateName` sealed states in `HomeViewModel` do not change.

Replace the current `WelcomeScreen` body with:

```kotlin
@Composable
private fun WelcomeScreen(
    hasExistingUsers: Boolean,
    error: String?,
    onNewUser: (String) -> Unit,
    onPickUser: () -> Unit,
    prefillFirstName: String = "",
    prefillLastName: String = ""
) {
    // If we arrive with prefill (from DuplicateName/SimilarName "No, I'm different" path),
    // skip the landing and go straight to the form.
    val initialMode = if (prefillFirstName.isNotEmpty() || prefillLastName.isNotEmpty())
        WelcomeMode.NEW_USER_FORM else WelcomeMode.LANDING
    var mode by remember { mutableStateOf(initialMode) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LanguageToggle()
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("GTKY", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold,
                 color = MaterialTheme.colorScheme.primary)
            Text("Get To Know Ya", fontSize = 18.sp,
                 color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                 modifier = Modifier.padding(bottom = 48.dp))

            when (mode) {
                WelcomeMode.LANDING -> LandingChoice(
                    hasExistingUsers = hasExistingUsers,
                    onNewHere = { mode = WelcomeMode.NEW_USER_FORM },
                    onAlreadyHere = onPickUser
                )
                WelcomeMode.NEW_USER_FORM -> NewUserForm(
                    error = error,
                    prefillFirstName = prefillFirstName,
                    prefillLastName = prefillLastName,
                    hasExistingUsers = hasExistingUsers,
                    onSubmit = onNewUser,
                    onBackToLanding = { mode = WelcomeMode.LANDING },
                    onAlreadyHere = onPickUser
                )
                else -> { /* unused; picker is its own route */ }
            }
        }
    }
}

private enum class WelcomeMode { LANDING, NEW_USER_FORM, PICKER_INLINE }
```

**`LandingChoice`** — the new two-button hero:

```kotlin
@Composable
private fun LandingChoice(
    hasExistingUsers: Boolean,
    onNewHere: () -> Unit,
    onAlreadyHere: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            t("Welcome! Are you new here or already signed up?",
              "¡Bienvenido! ¿Eres nuevo o ya te registraste?"),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onNewHere,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(t("I'm new here", "Soy nuevo aquí"), fontSize = 18.sp)
        }

        if (hasExistingUsers) {
            OutlinedButton(
                onClick = onAlreadyHere,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(t("I'm already here", "Ya estoy aquí"), fontSize = 18.sp)
            }
        }
    }
}
```

Visual weight: both buttons full-width, both 64dp tall, both the same size. The only visual difference is `Button` (filled) for new vs `OutlinedButton` for returning — a subtle priority nod toward onboarding but not a miscasting of intent. If `hasExistingUsers` is false (first user of the whole app), skip the "I'm already here" button entirely.

**`NewUserForm`** — the existing form, extracted into its own composable with a back affordance. All existing behavior (first/last field, preview line from Fix 27, error display, `Let's Go` button) carries over. Add a small text button above the form:

```kotlin
TextButton(
    onClick = onBackToLanding,
    modifier = Modifier.align(Alignment.Start)
) {
    Text(t("← Back", "← Atrás"), fontSize = 13.sp)
}
```

…and keep a parallel "I'm already here — pick my name" text button below the `Let's Go` button, but make it less prominent than in the old UI — it's a safety net, not the primary path, now that the landing screen offers the picker as a co-equal action. A single `TextButton` with small font works:

```kotlin
if (hasExistingUsers) {
    TextButton(
        onClick = onAlreadyHere,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(t("Actually, I'm already here — pick my name",
               "En realidad, ya estoy aquí — elige mi nombre"),
             fontSize = 13.sp)
    }
}
```

Preserve the existing preview-line logic (`"Will be saved as: …"`) from Fix 27 inside `NewUserForm`.

### 28.2 — Similar-name detection logic

Add a new function to `GTKYRepository.kt`:

```kotlin
data class SimilarNameMatch(val user: User, val matchKind: MatchKind)

enum class MatchKind {
    EXACT,              // normalized names are identical
    PREFIX_LONGER,      // existing user's last name is a prefix of what was typed
                        // e.g., existing "Alex S", typed "Alex Smith"
    PREFIX_SHORTER,     // typed last name is a prefix of existing user's
                        // e.g., existing "Alex Smith", typed "Alex S"
    SAME_INITIAL        // same first name, different last names, same leading letter
                        // e.g., existing "Alex Smith", typed "Alex Smyth" — both start with S
}

suspend fun findSimilarNames(typedName: String): List<SimilarNameMatch> {
    val typed = normalizeName(typedName)
    if (typed.isBlank()) return emptyList()
    val typedParts = typed.split(" ", limit = 2)
    val typedFirst = typedParts.getOrNull(0)?.lowercase() ?: return emptyList()
    val typedLast = typedParts.getOrNull(1)?.lowercase() ?: ""

    val allUsers = db.userDao().getAllUsers().first()
    val matches = mutableListOf<SimilarNameMatch>()

    for (user in allUsers) {
        val existingParts = user.name.split(" ", limit = 2)
        val existingFirst = existingParts.getOrNull(0)?.lowercase() ?: continue
        val existingLast = existingParts.getOrNull(1)?.lowercase() ?: ""

        if (existingFirst != typedFirst) continue

        val kind = when {
            existingLast == typedLast -> MatchKind.EXACT
            typedLast.isNotEmpty() && existingLast.isNotEmpty() &&
                existingLast.startsWith(typedLast) -> MatchKind.PREFIX_LONGER
            typedLast.isNotEmpty() && existingLast.isNotEmpty() &&
                typedLast.startsWith(existingLast) -> MatchKind.PREFIX_SHORTER
            typedLast.isNotEmpty() && existingLast.isNotEmpty() &&
                typedLast[0] == existingLast[0] -> MatchKind.SAME_INITIAL
            else -> null
        }
        if (kind != null) matches.add(SimilarNameMatch(user, kind))
    }

    // Sort: EXACT first, then PREFIX_* (either direction), then SAME_INITIAL.
    return matches.sortedBy { match ->
        when (match.matchKind) {
            MatchKind.EXACT -> 0
            MatchKind.PREFIX_LONGER, MatchKind.PREFIX_SHORTER -> 1
            MatchKind.SAME_INITIAL -> 2
        }
    }
}
```

Keep the existing `getUserByName` for the exact-match fast path — don't remove it.

Add unit tests in `app/src/test/java/com/gtky/app/util/SimilarNameTest.kt`. We need to test the logic without a real DB. Refactor the match-kind decision into a pure, testable function:

```kotlin
// In GTKYRepository.kt, pull out the pure logic as a top-level internal function:

internal fun classifyNameMatch(
    typedFirst: String,
    typedLast: String,
    existingFirst: String,
    existingLast: String
): MatchKind? {
    val tf = typedFirst.lowercase()
    val tl = typedLast.lowercase()
    val ef = existingFirst.lowercase()
    val el = existingLast.lowercase()
    if (tf != ef) return null
    return when {
        el == tl -> MatchKind.EXACT
        tl.isNotEmpty() && el.isNotEmpty() && el.startsWith(tl) -> MatchKind.PREFIX_LONGER
        tl.isNotEmpty() && el.isNotEmpty() && tl.startsWith(el) -> MatchKind.PREFIX_SHORTER
        tl.isNotEmpty() && el.isNotEmpty() && tl[0] == el[0] -> MatchKind.SAME_INITIAL
        else -> null
    }
}
```

Then `findSimilarNames` delegates to it. Tests:

```kotlin
class SimilarNameTest {
    @Test fun exactMatch() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("Alex", "Smith", "Alex", "Smith"))
    @Test fun exactMatchCaseInsensitive() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("alex", "smith", "Alex", "Smith"))
    @Test fun prefixLongerTyped() =
        assertEquals(MatchKind.PREFIX_LONGER, classifyNameMatch("Alex", "S", "Alex", "Smith"))
    @Test fun prefixShorterTyped() =
        assertEquals(MatchKind.PREFIX_SHORTER, classifyNameMatch("Alex", "Smith", "Alex", "S"))
    @Test fun sameInitialDifferentName() =
        assertEquals(MatchKind.SAME_INITIAL, classifyNameMatch("Alex", "Smith", "Alex", "Smyth"))
    @Test fun differentFirstName() =
        assertEquals(null, classifyNameMatch("Alex", "S", "Alan", "Smith"))
    @Test fun differentInitial() =
        assertEquals(null, classifyNameMatch("Alex", "Jones", "Alex", "Smith"))
    @Test fun bothLastEmpty() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("Alex", "", "Alex", ""))
    @Test fun typedLastEmptyExistingHasLast() =
        assertEquals(null, classifyNameMatch("Alex", "", "Alex", "Smith"))
    @Test fun typedHasLastExistingEmpty() =
        assertEquals(null, classifyNameMatch("Alex", "Smith", "Alex", ""))
}
```

All 10 tests must pass.

### 28.3 — Wiring: new `HomeUiState.SimilarName` and updated submit flow

Extend the sealed class:

```kotlin
// HomeViewModel.kt
sealed class HomeUiState {
    // ... existing states ...
    data class SimilarName(
        val typedFirstName: String,
        val typedLastName: String,
        val matches: List<SimilarNameMatch>
    ) : HomeUiState()
}
```

Update `createAndSelectUser`:

```kotlin
fun createAndSelectUser(name: String) {
    if (name.isBlank()) {
        _uiState.value = HomeUiState.NoUser("Name cannot be empty")
        return
    }
    viewModelScope.launch {
        val normalized = normalizeName(name)
        val matches = repo.findSimilarNames(normalized)

        if (matches.isNotEmpty()) {
            val parts = normalized.split(" ", limit = 2)
            // If there's exactly one EXACT match, keep the existing DuplicateName UX —
            // it's a single-user "are you them?" and users are already used to it.
            if (matches.size == 1 && matches[0].matchKind == MatchKind.EXACT) {
                _uiState.value = HomeUiState.DuplicateName(
                    firstName = parts.getOrElse(0) { "" },
                    lastName = parts.getOrElse(1) { "" },
                    collidingUser = matches[0].user
                )
                return@launch
            }
            // Otherwise show the new candidate-picker popup.
            _uiState.value = HomeUiState.SimilarName(
                typedFirstName = parts.getOrElse(0) { "" },
                typedLastName = parts.getOrElse(1) { "" },
                matches = matches
            )
            return@launch
        }

        // No matches — proceed with creation (existing path, unchanged).
        val id = repo.createUser(normalized)
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
```

Add a handler for the "None of these, I'm different" action on the new dialog — it mirrors `cancelDuplicate`:

```kotlin
fun cancelSimilar(firstName: String, lastName: String) {
    _uiState.value = HomeUiState.NoUser(
        prefillFirstName = firstName,
        prefillLastName = lastName
    )
}
```

### 28.4 — The candidate picker popup

In `HomeScreen.kt`, handle the new state alongside `DuplicateName`:

```kotlin
is HomeUiState.SimilarName -> {
    WelcomeScreen(
        hasExistingUsers = allUsers.isNotEmpty(),
        error = null,
        onNewUser = {},
        onPickUser = onPickUser,
        prefillFirstName = state.typedFirstName,
        prefillLastName = state.typedLastName
    )
    SimilarNameDialog(
        matches = state.matches,
        onPickExisting = { user -> viewModel.selectExistingUser(user) },
        onImDifferent = { viewModel.cancelSimilar(state.typedFirstName, state.typedLastName) }
    )
}
```

And the dialog itself:

```kotlin
@Composable
private fun SimilarNameDialog(
    matches: List<SimilarNameMatch>,
    onPickExisting: (User) -> Unit,
    onImDifferent: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onImDifferent,
        title = { Text(t("Is this you?", "¿Eres tú?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("We found someone with a similar name. Tap your name if you're already here:",
                      "Encontramos a alguien con un nombre parecido. Toca tu nombre si ya estás aquí:"),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                matches.forEach { match ->
                    OutlinedButton(
                        onClick = { onPickExisting(match.user) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(match.user.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onImDifferent) {
                Text(t("None of these — I'm different", "Ninguno — soy diferente"))
            }
        }
    )
}
```

Two notes on the dialog shape:

- No `dismissButton` slot. The big "None of these" lives in `confirmButton` because it's the primary default action if the user doesn't recognize themselves. Candidate rows are inside the body. Tapping the scrim also triggers `onImDifferent` (via `onDismissRequest`), same as "None of these" — so a misplaced tap outside the dialog returns to the form with prefill intact, not a crash or surprise state.
- Each candidate row is a full-width `OutlinedButton` showing the normalized display name. No "match kind" label — users don't need to know whether they matched by prefix or initial; they just need to see their name and tap it.

### 28.5 — Minor follow-through

- **`cancelDuplicate` path already supports prefill** — confirm it still sets `prefillFirstName` / `prefillLastName` on the `NoUser` state so the form doesn't blank out on "No, I'm different." (No change needed; just verify.)
- **When `selectExistingUser` is called from the dialog**, it already handles `setActiveUserId` and transitions to `UserSelected`. No change.
- **When there are zero users in the DB** (first user ever), `findSimilarNames` returns empty and the flow short-circuits to creation. No dialog fires. Verified by the test of an empty user list — make sure the loop handles `allUsers.isEmpty()` without crashing (it does; `for (user in emptyList)` is a no-op).

## Testing

### Unit tests

All 10 tests in `SimilarNameTest.kt` pass.

### Manual test

**New landing screen:**
1. Clear app data. Launch. Confirm "I'm new here" and "I'm already here" are side-by-side (or stacked, equal weight). With zero users, "I'm already here" is hidden.
2. Create user "Alex Smith." Sign out. Relaunch. Confirm both buttons now appear.

**Similar-name detection — prefix longer (typed last name is shorter than existing):**
1. With "Alex Smith" in the DB, tap "I'm new here" and type "Alex S" + "Let's Go!"
2. Confirm the "Is this you?" popup fires with "Alex Smith" listed as a candidate. Tap it — confirm it signs in as the existing Alex Smith (not creating a new user).

**Similar-name detection — prefix shorter (typed last name is longer than existing):**
1. With "Alex S" in the DB, type "Alex Smith" + submit.
2. Confirm the popup lists "Alex S" as a candidate.

**Similar-name detection — same initial, different name:**
1. With "Alex Smith" in the DB, type "Alex Smyth" + submit.
2. Confirm the popup lists "Alex Smith."

**Multiple candidates:**
1. Seed the DB with "Alex Smith" and "Alex Smyth" (via two separate sign-ups with the "None of these — I'm different" path or force a migration).
2. Type "Alex S" + submit. Confirm the popup lists both "Alex Smith" and "Alex Smyth" as tappable rows.

**Genuinely different person ("None of these" path):**
1. With "Alex Smith" in the DB, type "Alex Smyth" + submit → popup appears.
2. Tap "None of these — I'm different." Confirm the form returns with "Alex" and "Smyth" pre-filled. Tap "Let's Go!" again. Confirm a new user is created this time (no popup, because there was only one `SAME_INITIAL` match and the user explicitly rejected it — but wait, on re-submit the match still applies…).

**Edge case — user intentionally creates a near-duplicate.** On re-submit after "None of these," the check fires again. The user is now stuck in a loop. Decision: the "None of these" action should also *skip the next similarity check* for that exact typed name. Implementation:

Add a `skipSimilarForName: String?` field to `HomeUiState.NoUser`:

```kotlin
data class NoUser(
    val error: String? = null,
    val prefillFirstName: String = "",
    val prefillLastName: String = "",
    val skipSimilarForName: String? = null   // if user just rejected similar matches, skip next check
) : HomeUiState()
```

In `cancelSimilar`:
```kotlin
fun cancelSimilar(firstName: String, lastName: String) {
    _uiState.value = HomeUiState.NoUser(
        prefillFirstName = firstName,
        prefillLastName = lastName,
        skipSimilarForName = normalizeName("$firstName $lastName")
    )
}
```

In `createAndSelectUser`, check the flag before running the similarity search:
```kotlin
val skipName = (_uiState.value as? HomeUiState.NoUser)?.skipSimilarForName
val normalized = normalizeName(name)
val shouldSkipSimilar = skipName != null && skipName == normalized

val matches = if (shouldSkipSimilar) emptyList() else repo.findSimilarNames(normalized)
// ... rest unchanged
```

Exact-match collisions (via `DuplicateName`) still fire even when `shouldSkipSimilar` is true — those are hard uniqueness blocks, not suggestions. But `findSimilarNames` returns an `EXACT` match only when the normalized strings are character-identical, which `getUserByName` would also catch. So confirm the flow:

Actually, `findSimilarNames` returns EXACT matches too, so skipping it would let a true duplicate through. Handle this by re-running just the exact-match check (via `getUserByName`) even when `shouldSkipSimilar` is true:

```kotlin
val matches = if (shouldSkipSimilar) {
    // Still enforce hard exact-match block, but skip fuzzy matches.
    val exact = repo.getUserByName(normalized)
    if (exact != null) listOf(SimilarNameMatch(exact, MatchKind.EXACT)) else emptyList()
} else {
    repo.findSimilarNames(normalized)
}
```

This keeps the contract: "None of these" only dismisses *fuzzy* matches, not a literal collision.

Re-run the manual test with this flag. Re-submit after "None of these" should create the new user without re-triggering the popup — but typing a truly colliding name still triggers the exact-match DuplicateName dialog.

## Changelog entry

Add to `CHANGELOG.md`:

- **Fix 28 — Welcome screen rework + similar-name detection** — `WelcomeScreen` now opens with an equal-weight "I'm new here" / "I'm already here" landing choice instead of form-first. Tapping either reveals the form or the picker. New `findSimilarNames()` in `GTKYRepository` catches same-first-name + prefix/initial matches (e.g., typing "Alex S" when "Alex Smith" exists). New `HomeUiState.SimilarName` state and `SimilarNameDialog` show all candidate users as tappable rows plus "None of these — I'm different." Once a user rejects a similar-name suggestion for a specific typed name, re-submitting the same name bypasses fuzzy matching but still enforces hard exact-name uniqueness. 10 unit tests in `SimilarNameTest.kt` cover the classification logic.
