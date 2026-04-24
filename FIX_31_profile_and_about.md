# GTKY — Fix 31: Public profile (no answer reveal) + About screen

This fix bundles two changes that are entwined: a privacy redesign of the user-tappable profile screen, and a new About screen that the GTKY wordmark opens. Together because the About screen's FAQ copy depends on the new privacy model.

Depends on Fix 29 (avatars) and Fix 30 (quiz hero) being landed.

## Part A — The privacy fix

### The problem

After Fix 22, tapping a Connections row or a per-subject row on the quiz results screen opens `ProfileScreen`, which displays every survey answer the user has given as scrollable (question, answer) pairs. This is a leak of the icebreaker game itself: a curious user can learn everything about another user without taking a single quiz. That defeats the loop GTKY is built around — quizzes are the mechanism, profile-browsing shouldn't be a shortcut.

### What we're building

`ProfileScreen` becomes a public stats card. It still answers "who is this person?" with their avatar and name, but instead of an answer list it shows aggregate stats: how many questions they've answered, your mutual quiz scores (you about them, them about you). No (question, answer) pairs anywhere.

The admin-side `UserDetailScreen` is unchanged — admins keep the full answer view.

### Implementation

#### 31.1 — Strip the answer list from `ProfileScreen` and `ProfileViewModel`

In `ProfileViewModel.kt`, replace the `ProfileUiState` and `init` block.

New state shape:

```kotlin
data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val photoPath: String? = null,
    val answerCount: Int = 0,                    // how many survey questions they've answered
    val youAboutThemCorrect: Int = 0,            // your correct answers about them
    val youAboutThemTotal: Int = 0,              // your total quiz attempts about them
    val themAboutYouCorrect: Int = 0,            // their correct answers about you
    val themAboutYouTotal: Int = 0               // their total quiz attempts about you
)
```

Drop the `answers: List<Pair<String, String>>` field entirely.

Replace the `init` block:

```kotlin
init {
    viewModelScope.launch {
        val user = repo.getUserById(userId) ?: return@launch
        val activeUserId = repo.getActiveUserId()
        val answerCount = repo.db.surveyAnswerDao().getAnswerCountForUserSync(userId)

        // Quiz scores. If there's no active user (edge case), zero everything out.
        val (youCorrect, youTotal, themCorrect, themTotal) = if (activeUserId != null) {
            val youResults = repo.db.quizResultDao()
                .getResultsBetween(quizTakerId = activeUserId, subjectUserId = userId)
            val themResults = repo.db.quizResultDao()
                .getResultsBetween(quizTakerId = userId, subjectUserId = activeUserId)
            listOf(
                youResults.count { it.isCorrect },
                youResults.size,
                themResults.count { it.isCorrect },
                themResults.size
            )
        } else listOf(0, 0, 0, 0)

        _uiState.value = ProfileUiState(
            isLoading = false,
            name = user.name,
            photoPath = user.photoPath,
            answerCount = answerCount,
            youAboutThemCorrect = youCorrect,
            youAboutThemTotal = youTotal,
            themAboutYouCorrect = themCorrect,
            themAboutYouTotal = themTotal
        )
    }
}
```

Drop the `language` constructor parameter from `ProfileViewModel` (Fix 25). It was only used for translating the answer list, which is gone. Update the `Factory` and the `NavGraph` call site to drop the `language` arg too. The `key = "profile-$userId-$language"` re-key trick can be simplified to `key = "profile-$userId"` since the screen no longer renders any language-dependent content beyond the static UI labels.

Add the new DAO query needed:

```kotlin
// QuizResultDao.kt
@Query("SELECT * FROM quiz_results WHERE quizTakerId = :quizTakerId AND subjectUserId = :subjectUserId")
suspend fun getResultsBetween(quizTakerId: Long, subjectUserId: Long): List<QuizResult>
```

#### 31.2 — Rewrite `ProfileScreen` as a stats card

Replace the body of `ProfileScreen.kt` with a new layout:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("Profile", "Perfil")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // Build a synthetic User just for the Avatar composable.
        val displayUser = User(id = -1, name = state.name, photoPath = state.photoPath)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(user = displayUser, size = 160.dp)

            Text(
                text = state.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = t(
                    "${state.name} has answered ${state.answerCount} survey question" +
                        if (state.answerCount == 1) "" else "s",
                    "${state.name} ha respondido ${state.answerCount} pregunta" +
                        if (state.answerCount == 1) "" else "s"
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScoreCard(
                label = t("How well you know ${state.name}",
                          "Qué tanto conoces a ${state.name}"),
                correct = state.youAboutThemCorrect,
                total = state.youAboutThemTotal,
                emptyHint = t(
                    "Take a quiz about ${state.name} to find out!",
                    "¡Toma un quiz sobre ${state.name} para descubrirlo!"
                )
            )

            ScoreCard(
                label = t("How well ${state.name} knows you",
                          "Qué tanto te conoce ${state.name}"),
                correct = state.themAboutYouCorrect,
                total = state.themAboutYouTotal,
                emptyHint = t(
                    "${state.name} hasn't quizzed about you yet.",
                    "${state.name} aún no ha tomado un quiz sobre ti."
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = t(
                    "Want to learn more about ${state.name}? Take a quiz.",
                    "¿Quieres saber más sobre ${state.name}? Toma un quiz."
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun ScoreCard(
    label: String,
    correct: Int,
    total: Int,
    emptyHint: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            if (total == 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    emptyHint,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            } else {
                val pct = (correct * 100) / total
                Spacer(Modifier.height(4.dp))
                Text("$correct / $total", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("$pct%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
    }
}
```

Imports the screen needs (some may already be present): `androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`, `androidx.compose.runtime.*`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.text.style.TextAlign`, `androidx.compose.ui.unit.dp`, `androidx.compose.ui.unit.sp`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.automirrored.filled.ArrowBack`, `com.gtky.app.data.entity.User`, `com.gtky.app.ui.components.Avatar`, `com.gtky.app.ui.LanguageToggle`, `com.gtky.app.ui.t`.

Notes:
- The `displayUser` synthesized with `id = -1` is fine because `Avatar` only reads `name` and `photoPath`. The `id` isn't used for anything visible.
- Two `ScoreCard`s express the bidirectional knowledge — "you know them" / "they know you" — without spilling per-question detail.
- Empty states give a clear CTA ("take a quiz to find out") rather than a blank or dash.
- The 8-answer profile gate from Fix 22 is unrelated to this change and stays — a user under threshold still sees the snackbar before navigating to a profile, because the gate was about earning the right to look at others before the app shows you anything beyond their face. That logic stays.

Actually — reconsider the gate. The original gate logic was "you must answer 8 questions before you can see another user's profile (which contained their answers)." Now that the profile contains no answers, the gate is gating the wrong thing. It's now gating "see your mutual quiz stats" which is your own data anyway.

**Decision: drop the 8-answer gate on the profile navigation.** A user with zero answers can tap a Connections row or a quiz-results subject row and land on the new public profile. There's nothing leaking. They'll see name, avatar, and "Take a quiz about Jamie to find out!" in both score cards — which is itself the right invitation.

In `ConnectionsScreen.kt`, remove the `state.myAnswerCount < Constants.QUIZ_UNLOCK_THRESHOLD` check and the snackbar message around it. Both `MutualConnectionRow` and `OneWayConnectionRow` taps go straight to the profile. Remove the now-unused `profileGateMessage` and `coroutineScope`/`snackbarHostState` plumbing if nothing else uses them — but check: the snackbar may also be used by other paths. If it is, leave the host in place but stop calling it from the row taps.

#### 31.3 — Delete the Spanish answer-translation code that's no longer needed

`ProfileViewModel.translateAnswer` and `parseOptions` from Fix 25 only existed to render answer text in Spanish. With the answer list gone, these are dead. Delete them and the `kotlinx.serialization` imports they introduced if nothing else in `ProfileViewModel.kt` uses them.

#### 31.4 — Verify admin-side `UserDetailScreen` is untouched

`UserDetailScreen` (in `AdminScreen.kt`) shows the full answer list to admins. It does not share code with `ProfileScreen` — they're separate. Confirm by grepping: `getAllAnswersForUser` should still be called from `AdminViewModel`, not from `ProfileViewModel`. If somehow the two share a helper, leave the admin path intact.

### Testing — Part A

**Privacy regression check (the important one):**
1. Create users Alex and Jamie. Have Jamie answer 10 survey questions.
2. Sign in as Alex. Open Connections (or take a quiz that lands on the results screen with Jamie as a subject).
3. Tap Jamie's row to open her profile.
4. **Confirm: no question/answer pairs appear anywhere on the screen.** Just her avatar, her name, "Jamie has answered 10 survey questions," and two score cards (likely both showing the empty-hint state if no quizzes have happened yet).

**Stats accuracy:**
1. Take 5 quizzes about Jamie, getting 3 right.
2. Re-open Jamie's profile. The "How well you know Jamie" card should show "3 / 5 — 60%."
3. As Jamie, take 4 quizzes about Alex, getting 1 right.
4. Re-open Alex's profile from Jamie's account. "How well you know Alex" → "1 / 4 — 25%." From Alex's account, Jamie's profile "How well Jamie knows you" → "1 / 4 — 25%."

**Empty states:**
1. With a fresh user pair (no quizzes between them), open the profile. Both cards show the empty-hint copy.

**Admin still works:**
1. Sign out, enter admin, tap Jamie's row. Confirm `UserDetailScreen` still scrolls through every survey answer Jamie has given.

**Gate removal:**
1. Create a fresh user with zero answers. Sign in. Open Connections. Tap a row. Confirm the profile opens (no snackbar about needing 8 answers).

---

## Part B — The About screen

### What we're building

A new full-screen route `Routes.ABOUT` reachable by tapping the GTKY wordmark on either the welcome screen or the user home screen. Four sections (What is GTKY? / Who's it for? / How it works / Tips & FAQ), each with an icon-anchored header. Playful conversational tone with a touch of aspirational.

### 31.5 — Add the route

In `NavGraph.kt`:

```kotlin
object Routes {
    // ... existing routes ...
    const val ABOUT = "about"
}
```

```kotlin
composable(Routes.ABOUT) {
    AboutScreen(onBack = { navController.popBackStack() })
}
```

The route doesn't need any arguments. It's pure static content (with bilingual variants).

### 31.6 — Build the screen

New file `app/src/main/java/com/gtky/app/ui/screens/AboutScreen.kt`:

```kotlin
package com.gtky.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.ui.LanguageToggle
import com.gtky.app.ui.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("About GTKY", "Acerca de GTKY")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { LanguageToggle() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Hero
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GTKY",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    t("Get To Know Ya", "Conócete"),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Section(
                icon = Icons.Filled.EmojiPeople,
                title = t("What is GTKY?", "¿Qué es GTKY?"),
                body = t(
                    "GTKY (Get To Know Ya) is a get-to-know-you game for groups. " +
                    "Everyone answers a bunch of questions about themselves, then quizzes " +
                    "each other to see how well they actually know the people in the room. " +
                    "It's the part of the icebreaker that usually doesn't happen — turned " +
                    "into something you might actually want to do.",
                    "GTKY (Get To Know Ya, \"conócete\") es un juego para que un grupo se conozca. " +
                    "Cada quien responde preguntas sobre sí mismo, y después se hacen quizzes " +
                    "para ver qué tanto se conocen. Es la parte del rompehielos que normalmente " +
                    "no pasa — convertida en algo que sí te dan ganas de hacer."
                )
            )

            Section(
                icon = Icons.Filled.Groups,
                title = t("Who's it for?", "¿Para quién es?"),
                body = t(
                    "GTKY works best for groups of 5 to 50 people who'll be spending time " +
                    "together — team offsites, retreats, classrooms, conference tables, " +
                    "family reunions, new-hire onboarding, anywhere you want people to skip " +
                    "the small talk and actually learn about each other. Pass a tablet around " +
                    "the room and let people drift in and out as they have a few minutes.",
                    "GTKY funciona mejor con grupos de 5 a 50 personas que pasarán tiempo " +
                    "juntas — retiros de equipo, salones de clase, mesas de conferencias, " +
                    "reuniones familiares, onboarding de nuevos empleados, donde quieras que " +
                    "la gente se salte la plática trivial y se conozca de verdad. Pasa una " +
                    "tableta por la sala y deja que la gente entre y salga conforme tenga unos minutos."
                )
            )

            Section(
                icon = Icons.Filled.PlayArrow,
                title = t("How it works", "Cómo funciona"),
                body = t(
                    "1. Sign in with your name. If you've used GTKY before, tap \"I'm already here\" instead.\n\n" +
                    "2. Answer survey questions about yourself — preferences, opinions, would-you-rathers. " +
                    "The more you answer, the more material there is for others.\n\n" +
                    "3. Once you've answered eight or so, you can take quizzes. The app picks someone " +
                    "in the group and asks questions about them; you guess what they answered.\n\n" +
                    "4. Check the Connections screen to see who knows you best, and whose answers you've nailed.\n\n" +
                    "5. Tap any face to see how well you know that person and how well they know you.",
                    "1. Inicia sesión con tu nombre. Si ya usaste GTKY antes, toca \"Ya estoy aquí\".\n\n" +
                    "2. Responde preguntas sobre ti — preferencias, opiniones, qué prefieres. " +
                    "Mientras más respondas, más material hay para los demás.\n\n" +
                    "3. Cuando hayas respondido unas ocho, puedes empezar los quizzes. La app elige " +
                    "a alguien del grupo y te hace preguntas sobre esa persona; tú adivinas qué respondió.\n\n" +
                    "4. Revisa la pantalla de Conexiones para ver quién te conoce mejor y a quién has acertado más.\n\n" +
                    "5. Toca cualquier cara para ver qué tanto conoces a esa persona y qué tanto te conoce."
                )
            )

            Section(
                icon = Icons.Filled.Lightbulb,
                title = t("Tips & FAQ", "Tips y preguntas"),
                body = null,
                richBody = {
                    FaqItem(
                        q = t("Don't overthink the survey.",
                              "No le pienses tanto a las preguntas."),
                        a = t("Quick gut-feel answers make for better quizzes.",
                              "Las respuestas instintivas hacen mejores quizzes.")
                    )
                    FaqItem(
                        q = t("Photos help.", "Las fotos ayudan."),
                        a = t("A face on your profile makes you findable in the picker and recognizable in the quiz. " +
                              "The \"Smile!\" prompt asks once or twice — you can skip it any time.",
                              "Una foto en tu perfil hace que te encuentren más fácil y te reconozcan en los quizzes. " +
                              "El mensaje \"¡Sonríe!\" aparece una o dos veces — puedes saltarlo cuando quieras.")
                    )
                    FaqItem(
                        q = t("Who sees my photo?", "¿Quién ve mi foto?"),
                        a = t("Other people on this device only. Photos are stored locally and never uploaded anywhere.",
                              "Solo las otras personas en esta tableta. Las fotos se guardan localmente y no se suben a ningún lado.")
                    )
                    FaqItem(
                        q = t("Who sees my answers?", "¿Quién ve mis respuestas?"),
                        a = t("No one sees your full list of answers — not even you, after you submit them. " +
                              "Other players only learn your answers one at a time, by taking quizzes about you. " +
                              "Only the admin can see anyone's full answer list.",
                              "Nadie ve tu lista completa de respuestas — ni siquiera tú, después de enviarlas. " +
                              "Los demás jugadores solo se enteran de tus respuestas una por una, tomando quizzes sobre ti. " +
                              "Solo el admin puede ver la lista completa de respuestas de alguien.")
                    )
                    FaqItem(
                        q = t("Can I take quizzes about specific people?",
                              "¿Puedo tomar quizzes sobre personas específicas?"),
                        a = t("Yes — when you start a quiz, expand \"Pick specific people\" to choose who.",
                              "Sí — cuando empieces un quiz, expande \"Elegir personas específicas\" para escoger.")
                    )
                    FaqItem(
                        q = t("I keep getting questions about the same person.",
                              "Me siguen tocando preguntas sobre la misma persona."),
                        a = t("The app weights subjects so people you've quizzed less come up more often. " +
                              "With a small group you'll see repeats — that's normal.",
                              "La app pondera los temas para que la gente sobre la que has tomado menos quizzes salga más seguido. " +
                              "En grupos pequeños vas a ver repeticiones — es normal.")
                    )
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(
    icon: ImageVector,
    title: String,
    body: String?,
    richBody: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (body != null) {
            Text(
                body,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
        }
        if (richBody != null) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                richBody()
            }
        }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            a,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
    }
}
```

Notes:
- Icons: `EmojiPeople`, `Groups`, `PlayArrow`, `Lightbulb` are all in `material-icons-extended` which the project already uses (Fix 29's components rely on it).
- The `Section` helper supports either a `body` string or a `richBody` slot for the FAQ items, so the same composable handles both prose sections and the structured FAQ list.
- The "How it works" body uses `\n\n` between numbered steps. `Text` honors that as paragraph breaks. If the rendering doesn't visually separate them enough, replace with a `Column` of separate `Text` items.
- Bilingual content is inline via `t()` per usual. The Spanish translations are written naturally, not literal — review them if you have a native speaker handy. The translation of "Get To Know Ya" → "Conócete" is a creative translation; if you'd rather keep the English subtitle in both languages, replace with `t("Get To Know Ya", "Get To Know Ya")`.

### 31.7 — Wire up the wordmark taps

In `HomeScreen.kt`, the GTKY wordmark appears in two places: `WelcomeScreen` (the big 64sp title with subtitle) and `UserHomeScreen` (a smaller mark, may not exist as a separate composable — check the current code).

For the welcome screen wordmark, wrap it in a clickable Column:

```kotlin
Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onAboutTap
        )
        .padding(8.dp)   // padding for tap-target padding, not visual
) {
    Text("GTKY", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold,
         color = MaterialTheme.colorScheme.primary)
    Text("Get To Know Ya", fontSize = 18.sp,
         color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    Text(
        text = t("tap to learn more", "toca para más info"),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

Use `indication = null` because the GTKY wordmark looks weird with a Material ripple over it. The hint label is the discoverability signal.

For the user home screen, locate the GTKY mark (or add one if it isn't there — the spec referenced "tapping on the GTKY icon on the home screen" so confirm it exists; if not, add a smaller version, maybe 32sp ExtraBold at the very top, also clickable). Same `clickable` wrapper pattern, **no** "tap to learn more" hint here:

```kotlin
Text(
    "GTKY",
    fontSize = 32.sp,
    fontWeight = FontWeight.ExtraBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onAboutTap
        )
        .padding(8.dp)
)
```

Plumb `onAboutTap: () -> Unit` through `WelcomeScreen` and `UserHomeScreen`. In `HomeScreen` (the parent), wire it to `navController.navigate(Routes.ABOUT)`. The simplest approach is to thread `onAboutTap` through `HomeScreen`'s parameter list, set in `NavGraph.kt`:

```kotlin
composable(Routes.HOME) {
    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repo))
    HomeScreen(
        viewModel = homeVm,
        // ... existing callbacks ...
        onAboutTap = { navController.navigate(Routes.ABOUT) }
    )
}
```

### Testing — Part B

1. From the welcome screen (no user signed in), tap the big GTKY wordmark. About screen opens. Tap back. Returns to welcome screen, no state lost.
2. Sign in. From the user home screen, tap the GTKY mark. About screen opens. Back. Returns to home with the user still signed in.
3. Switch to Spanish via the toggle in the About TopAppBar. All four sections re-render in Spanish.
4. Scroll the About screen. Confirm the FAQ items wrap cleanly on a tablet width.
5. Confirm the "tap to learn more" hint appears under the welcome wordmark but NOT under the user home wordmark.
6. Tap the wordmark rapidly. The About screen should open once, not stack multiple instances. (The default nav behavior usually handles this; if you see double-stacking, add `launchSingleTop = true` to the navigate call.)

---

## Combined changelog entries

Add to `CHANGELOG.md` under the existing `## UX Fix Pack` section:

- **Fix 31a — Public profile (no answer reveal)** — `ProfileScreen` no longer displays a user's full answer list. Replaced with a public stats card: large avatar, name, total answers count, and two score cards showing your bidirectional quiz knowledge ("how well you know them" / "how well they know you"). The 8-answer profile-view gate is removed since there's nothing private to gate. Admin-side `UserDetailScreen` is unchanged — admins keep the full answer view. Dropped the now-unused Spanish answer-translation logic from `ProfileViewModel`.
- **Fix 31b — About screen** — New `Routes.ABOUT` reachable by tapping the GTKY wordmark on either the welcome screen or the user home screen. Four sections (What is GTKY? / Who's it for? / How it works / Tips & FAQ), each with an icon-anchored header. Bilingual content. Welcome-screen wordmark also gains a small "tap to learn more" hint to surface the affordance for first-time users.

## Branch and commit

Two commits, two branches: `fix/31a-public-profile`, `fix/31b-about-screen`. Either order works; I'd ship 31a first because it's the privacy-correctness one.
