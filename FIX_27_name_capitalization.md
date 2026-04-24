# GTKY — Fix 27: Normalize name capitalization

Single-fix pack.

## The problem

At the first event test, users entered names inconsistently — some lowercase (`"alex smith"`), some all caps (`"ALEX SMITH"`), some with caps-lock stuck on the last initial (`"Alex S"` vs `"alex s"`). The app stores whatever the user typed, so the picker, quiz prompts ("Does alex smith prefer…"), and Connections ("YOU & ALEX SMITH") look messy.

## What we're fixing

Normalize every display name to strict title case on write: first letter of each word uppercase, everything else lowercase. `"alex SMITH"` → `"Alex Smith"`. `"mary-jane"` → `"Mary-Jane"`. The rule is applied both to new sign-ups/renames *and* to every existing user already in the DB via a one-time migration on app launch.

Ground rules unchanged: bilingual strings via `t()`, no new dependencies, one fix = one branch = one commit (`fix/27-name-capitalization`), add a bullet to `CHANGELOG.md`.

## Implementation

### 27.1 — Shared normalization helper

Create `app/src/main/java/com/gtky/app/util/NameFormat.kt`:

```kotlin
package com.gtky.app.util

/**
 * Normalizes a display name to strict title case:
 * - Collapses runs of whitespace to single spaces, trims ends.
 * - For each whitespace-delimited word, uppercases the first letter and lowercases the rest.
 * - Hyphen-joined segments inside a word are each title-cased ("mary-jane" -> "Mary-Jane").
 * - Apostrophes do not trigger new segments ("o'brien" -> "O'brien", "mcdonald" -> "Mcdonald").
 *   This is an intentional trade-off: minimal rules, predictable behavior. Users can self-correct
 *   via the rename dialog if they need a specific mixed case.
 *
 * Examples:
 *   "alex smith"      -> "Alex Smith"
 *   "ALEX SMITH"      -> "Alex Smith"
 *   "alex S"          -> "Alex S"
 *   "  alex   s  "    -> "Alex S"
 *   "mary-jane LEE"   -> "Mary-Jane Lee"
 *   ""                -> ""
 */
fun normalizeName(raw: String): String {
    val collapsed = raw.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isEmpty()) return ""
    return collapsed.split(" ").joinToString(" ") { word ->
        word.split("-").joinToString("-") { segment -> titleCaseSegment(segment) }
    }
}

private fun titleCaseSegment(segment: String): String {
    if (segment.isEmpty()) return segment
    val first = segment[0].uppercaseChar()
    val rest = segment.substring(1).lowercase()
    return "$first$rest"
}
```

Add a matching unit test file `app/src/test/java/com/gtky/app/util/NameFormatTest.kt`:

```kotlin
package com.gtky.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameFormatTest {
    @Test fun lowercase() = assertEquals("Alex Smith", normalizeName("alex smith"))
    @Test fun uppercase() = assertEquals("Alex Smith", normalizeName("ALEX SMITH"))
    @Test fun mixedCase() = assertEquals("Alex Smith", normalizeName("aLeX sMiTh"))
    @Test fun singleInitial() = assertEquals("Alex S", normalizeName("alex s"))
    @Test fun extraWhitespace() = assertEquals("Alex S", normalizeName("  alex   s  "))
    @Test fun hyphenated() = assertEquals("Mary-Jane Lee", normalizeName("mary-jane LEE"))
    @Test fun apostrophe() = assertEquals("O'brien", normalizeName("o'brien"))
    @Test fun empty() = assertEquals("", normalizeName(""))
    @Test fun whitespaceOnly() = assertEquals("", normalizeName("   "))
    @Test fun singleLetter() = assertEquals("A", normalizeName("a"))
    @Test fun alreadyCorrect() = assertEquals("Alex Smith", normalizeName("Alex Smith"))
    @Test fun threeWords() = assertEquals("Mary Ann Lee", normalizeName("mary ann lee"))
}
```

All 12 tests must pass before moving on.

### 27.2 — Apply normalization at every write path

Two places write names to the DB: create and rename. Both live in `GTKYRepository.kt`.

Update both to normalize before handing to the DAO:

```kotlin
// GTKYRepository.kt
import com.gtky.app.util.normalizeName

suspend fun createUser(name: String): Long =
    db.userDao().insertUser(User(name = normalizeName(name)))

suspend fun renameUser(userId: Long, newName: String) =
    db.userDao().updateName(userId, normalizeName(newName))
```

The existing `.trim()` calls inside these functions are now redundant (`normalizeName` trims). Remove them.

**Also update the duplicate-name check path** in `HomeViewModel.createAndSelectUser`. Today it does:

```kotlin
val existing = repo.getUserByName(name.trim())
```

That's comparing the raw input against stored names. After normalization, the comparison needs to happen against the normalized form, or two users typing `"alex s"` and `"Alex S"` won't collide:

```kotlin
val normalizedName = normalizeName(name)
val existing = repo.getUserByName(normalizedName)
if (existing != null) {
    val parts = normalizedName.split(" ", limit = 2)
    _uiState.value = HomeUiState.DuplicateName(
        firstName = parts.getOrElse(0) { "" },
        lastName = parts.getOrElse(1) { "" },
        collidingUser = existing
    )
    return@launch
}
// ... rest unchanged, but use `normalizedName` when creating
```

Do the same normalization in `HomeViewModel.renameUser` before the uniqueness check:

```kotlin
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
        repo.renameUser(userId, normalized)
        val updatedUser = repo.getUserById(userId) ?: return@launch
        _uiState.value = cur.copy(user = updatedUser, renameError = null)
    }
}
```

Spanish translation for the new "Name cannot be empty" string: use the existing `t("Name cannot be empty", "El nombre no puede estar vacío")` if that translation isn't already available — if the existing `"Name cannot be empty"` error in `HomeViewModel.createAndSelectUser` is a raw string, extract both call sites to use `t()`. Actually — `HomeUiState.NoUser.error` and `HomeUiState.UserSelected.renameError` are plain strings set from the ViewModel, not run through `t()` at render time. Leave them as English error strings for now (this matches the existing pattern) and don't introduce bilingual error plumbing in this fix.

### 27.3 — One-time migration: normalize existing users

Add a migration that runs once on app launch and rewrites every existing name. Use the same `seeded_*` flag pattern `DataSeeder` already uses so it runs exactly once.

In `app/src/main/java/com/gtky/app/data/database/DataSeeder.kt`, extend `seedIfNeeded`:

```kotlin
// Add this import at top of file
import com.gtky.app.util.normalizeName

suspend fun seedIfNeeded(db: GTKYDatabase) = withContext(Dispatchers.IO) {
    // ... existing question seeding, admin PIN seeding, Spanish patch ...

    // One-time name normalization (v3.1)
    if (db.appConfigDao().getValue("normalized_names_v1") == null) {
        val allUsers = db.userDao().getAllUsers().first()
        for (user in allUsers) {
            val normalized = normalizeName(user.name)
            if (normalized != user.name && normalized.isNotEmpty()) {
                db.userDao().updateName(user.id, normalized)
            }
        }
        db.appConfigDao().setValue(AppConfig("normalized_names_v1", "true"))
    }
}
```

Import `kotlinx.coroutines.flow.first`. Do not bump the Room DB version — this is a data backfill, not a schema change, and it uses existing DAO methods.

**Handle the rare collision case.** If the migration would create a duplicate (e.g., DB has both `"alex s"` and `"Alex S"`, which normalize to the same `"Alex S"`), the second `updateName` would succeed (the name column doesn't have a UNIQUE constraint — confirmed by looking at `User.kt`), leaving two distinct users with identical display names. That's actually fine — the existing `PickUserScreen` `(1)` / `(2)` disambiguation logic from Fix 5 handles it at render time. No extra code needed, but note it in the commit message so the behavior is intentional and documented.

### 27.4 — UI normalization on input (nice-to-have)

The WelcomeScreen takes `firstName` and `lastInitial` as two fields. They feed the submit path which now normalizes. But the user also sees their raw input in the fields until submit — if they typed `"ALEX"`, it looks shouty until they tap the button.

Leave the text fields as raw input. Do **not** normalize live as the user types — that's confusing and fights the keyboard. Normalization happens at save time only.

Exception: there's value in showing the normalized preview when the form is filled. Below the `Last name or initial` field, if both fields are non-blank, add a small preview line:

```kotlin
// In HomeScreen.WelcomeScreen, just before the error block:
if (firstName.isNotBlank() && lastInitial.isNotBlank()) {
    val preview = normalizeName("${firstName.trim()} ${lastInitial.trim()}")
    val raw = "${firstName.trim()} ${lastInitial.trim()}"
    if (preview != raw) {
        Text(
            text = t("Will be saved as: $preview", "Se guardará como: $preview"),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

Import `normalizeName` in `HomeScreen.kt`. Only show the preview when it differs from the raw concatenation — if the user already typed it correctly, no preview appears.

Do the same inside `EditNameDialog`: if the typed value would normalize to something different, show a small hint below the field. This makes the rule discoverable without being in-your-face.

## Testing

### Unit tests

All 12 tests in `NameFormatTest.kt` pass.

### Manual test

**Migration path:**
1. Build on a device that already has users in the DB from the previous session.
2. Launch. Open Active Users. Confirm that names previously stored as `"alex s"` / `"ALEX S"` / etc. now display as `"Alex S"`.
3. Force-close and relaunch. Confirm names are still correct (the migration flag prevents re-running, but also confirms the rewrite persisted).

**New user path:**
1. Clear app data or create a fresh user. In the signup form, type `"alex"` / `"SMITH"`. The preview below the fields should show `"Will be saved as: Alex Smith"`.
2. Submit. Confirm the home screen greeting reads `"Hey, Alex Smith!"` and the pill reads `"Signed in as Alex Smith"`.
3. Sign out. Open the picker. Confirm the row reads `"Alex Smith"`.

**Rename path:**
1. As that user, tap the edit icon on the home pill. Enter `"alex s"` in the field. The preview hint should show `"Will be saved as: Alex S"`.
2. Save. Confirm the pill updates to `"Alex S"`.

**Collision test:**
1. Create a user `"alex s"`.
2. Try to create another user `"Alex S"`.
3. Confirm the "There's already an Alex S here. Are you them?" dialog appears (this only works if the duplicate check is using the normalized form — if it doesn't, the test fails).

**Display in quiz/connections:**
1. With a user named (post-normalization) `"Alex S"`, quiz about them. Confirm the quiz card reads `"About Alex S"` and the question reads `"Does Alex S prefer coffee or tea?"` — not `"Does alex s…"`.
2. Open Connections. Confirm the row reads `"You & Alex S"` with proper casing.

## Changelog entry

Add to `CHANGELOG.md`:

- **Fix 27 — Name capitalization normalization** — New `util/NameFormat.kt` helper `normalizeName()` applies strict title-case (`"alex SMITH"` → `"Alex Smith"`, `"mary-jane"` → `"Mary-Jane"`). Applied at every write path (`createUser`, `renameUser`) and at duplicate-name comparison. One-time migration in `DataSeeder` rewrites all existing users on app launch (gated by new `normalized_names_v1` AppConfig flag, no Room version bump). Sign-up form and rename dialog show a live preview ("Will be saved as: …") when the typed value differs from the normalized form. 12 unit tests in `NameFormatTest.kt`.
