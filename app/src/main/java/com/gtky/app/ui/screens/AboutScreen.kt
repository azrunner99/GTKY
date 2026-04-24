@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        q = t("Don't overthink the survey.", "No le pienses tanto a las preguntas."),
                        a = t("Quick gut-feel answers make for better quizzes.", "Las respuestas instintivas hacen mejores quizzes.")
                    )
                    FaqItem(
                        q = t("Photos help.", "Las fotos ayudan."),
                        a = t(
                            "A face on your profile makes you findable in the picker and recognizable in the quiz. " +
                            "The \"Smile!\" prompt asks once or twice — you can skip it any time.",
                            "Una foto en tu perfil hace que te encuentren más fácil y te reconozcan en los quizzes. " +
                            "El mensaje \"¡Sonríe!\" aparece una o dos veces — puedes saltarlo cuando quieras."
                        )
                    )
                    FaqItem(
                        q = t("Who sees my photo?", "¿Quién ve mi foto?"),
                        a = t(
                            "Other people on this device only. Photos are stored locally and never uploaded anywhere.",
                            "Solo las otras personas en esta tableta. Las fotos se guardan localmente y no se suben a ningún lado."
                        )
                    )
                    FaqItem(
                        q = t("Who sees my answers?", "¿Quién ve mis respuestas?"),
                        a = t(
                            "No one sees your full list of answers — not even you, after you submit them. " +
                            "Other players only learn your answers one at a time, by taking quizzes about you. " +
                            "Only the admin can see anyone's full answer list.",
                            "Nadie ve tu lista completa de respuestas — ni siquiera tú, después de enviarlas. " +
                            "Los demás jugadores solo se enteran de tus respuestas una por una, tomando quizzes sobre ti. " +
                            "Solo el admin puede ver la lista completa de respuestas de alguien."
                        )
                    )
                    FaqItem(
                        q = t("Can I take quizzes about specific people?", "¿Puedo tomar quizzes sobre personas específicas?"),
                        a = t(
                            "Yes — when you start a quiz, expand \"Pick specific people\" to choose who.",
                            "Sí — cuando empieces un quiz, expande \"Elegir personas específicas\" para escoger."
                        )
                    )
                    FaqItem(
                        q = t("I keep getting questions about the same person.", "Me siguen tocando preguntas sobre la misma persona."),
                        a = t(
                            "The app weights subjects so people you've quizzed less come up more often. " +
                            "With a small group you'll see repeats — that's normal.",
                            "La app pondera los temas para que la gente sobre la que has tomado menos quizzes salga más seguido. " +
                            "En grupos pequeños vas a ver repeticiones — es normal."
                        )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (body != null) {
            Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
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
        Text(a, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }
}
