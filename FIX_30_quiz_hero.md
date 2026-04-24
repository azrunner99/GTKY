# GTKY — Fix 30: Bigger quiz subject avatar and name

Small, focused fix. Depends on Fix 29 being landed (the `Avatar` composable and `User.photoPath` column must exist).

## The problem

After Fix 29, the quiz's "About $name" primary-container card got a 32dp avatar inline with 13sp text. That's fine as a data label, but it underuses the single most useful signal in the quiz: a face. The subject name is the answer to "who am I being quizzed on?" — if the user can't recognize the person from the avatar at a glance, they're guessing blind. On a tablet held at arm's length, a 32dp circle is about the size of a fingertip. Not enough.

## What we're building

Replace the inline "About $name" pill with a dominant subject block at the top of the question area: 96dp avatar centered, name centered below it at 22sp Medium, then the question text and answer options. Flow becomes: big face → big name → question → answers.

Ground rules unchanged: bilingual strings via `t()`, no new dependencies, one commit on branch `fix/30-quiz-subject-hero`, add a bullet to `CHANGELOG.md`.

## Implementation

In `QuizScreen.kt`, locate `QuizQuestionContent`. The current body opens with:

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(user = subject, size = 32.dp, modifier = Modifier.padding(start = 12.dp))
        Text(
            text = t("About $subjectName", "Sobre $subjectName"),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

(The exact shape may differ slightly depending on how Fix 29d wired the avatar into the card — adjust accordingly. The point is to replace the pill-with-small-avatar with a centered hero block.)

Replace with:

```kotlin
Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    Avatar(user = subject, size = 96.dp)
    Text(
        text = subjectName,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Text(
        text = t("Quiz about them", "Quiz sobre esta persona"),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
```

Notes on the changes:

- **96dp avatar** is big enough to recognize a face at arm's length on a 10" tablet, small enough to leave room for 4 answer options without scroll on a standard layout.
- **Name at 22sp Medium** is the new primary label. No more "About $name" prefix wrapping the name — the small "Quiz about them" subtitle underneath does that job without repeating the name.
- **Switching from `Card(primaryContainer)` to plain `Column`** removes the colored box. The subject block now reads as "this is who it's about" typographically rather than as a badge or tag.
- `subjectName` is already in scope in `QuizQuestionContent`. `subject` (the `User` object) was added as a parameter in Fix 29d; if it isn't there yet, add it to the function signature — the caller already passes `q.subjectUser`.

**Header title adjustment.** The TopAppBar currently reads `"Quiz — about $subjectName"` (Fix 6). With the subject hero now dominating the content area, the app bar title is redundant and uses valuable horizontal space. Simplify it to just:

```kotlin
val titleText = t("Quiz Time", "¡A Preguntar!")
```

Keep the Q X/Y counter in the `actions` slot unchanged.

**Question text spacing.** The question `Text` that follows the subject block currently sits 8dp below. That's probably tight against the new hero. Bump the top padding:

```kotlin
Text(
    text = questionText,
    fontSize = 20.sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    lineHeight = 28.sp,
    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)   // was vertical = 8.dp
)
```

## Testing

### Visual checks on a real device or emulator

1. **Subject with a photo.** Take a quiz about a user who has captured an avatar. Confirm the 96dp photo renders centered at the top of the question area, circular, with the name in 22sp below it. You should be able to recognize the person at a casual glance.

2. **Subject without a photo.** Take a quiz about a user who skipped the Smile prompt. Confirm the colored-initials fallback renders at 96dp with proportionally-scaled initials (the `Avatar` composable's `(size.value * 0.4f).sp` text size math from Fix 29 scales this up — should look clean, not pixelated).

3. **Four answer options still fit.** With the 96dp avatar, 22sp name, 12sp subtitle, 16dp top padding on the question, 20sp question text, and four 52dp answer buttons, confirm the whole thing renders without scrolling on a 10" tablet in landscape. If it doesn't, reduce the avatar to 80dp as a compromise — don't reduce the name size.

4. **Answer reveal still reads.** After tapping an answer, the "Correct!" / "Nope! $name chose ..." feedback appears below the options. Confirm nothing collides with the bigger hero.

5. **Language toggle mid-question.** Tap the Spanish toggle during a question. Confirm the subject block doesn't re-layout weirdly — avatar and name are language-agnostic, only the "Quiz about them" subtitle changes.

### Regression checks

- The quiz finish flow, auto-advance on correct, skip-person toast, "I haven't met this person yet" button, and per-subject breakdown on the results screen all still work.

## Changelog entry

- **Fix 30 — Quiz subject hero** — Replaced the inline `Card(primaryContainer)` "About $name" pill at the top of quiz questions with a centered 96dp avatar and 22sp Medium name label. Makes the quiz subject unmistakable at a glance. TopAppBar title simplified to "Quiz Time" since the subject is now displayed prominently in-content.
