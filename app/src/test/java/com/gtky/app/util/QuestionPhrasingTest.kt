package com.gtky.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionPhrasingTest {

    // ── English survey ────────────────────────────────────────────────────────

    @Test fun en_doesPrefer_coffee() = assertEquals(
        "Do you prefer coffee or tea?",
        forSurvey("Does [NAME] prefer coffee or tea?", "en")
    )

    @Test fun en_wouldRather_pizza() = assertEquals(
        "Would you rather eat pizza or tacos?",
        forSurvey("Would [NAME] rather eat pizza or tacos?", "en")
    )

    @Test fun en_doesPrefer_vanilla() = assertEquals(
        "Do you prefer vanilla or chocolate?",
        forSurvey("Does [NAME] prefer vanilla or chocolate?", "en")
    )

    @Test fun en_possessive_favorite_meal() = assertEquals(
        "What is your favorite meal of the day?",
        forSurvey("What is [NAME]'s favorite meal of the day?", "en")
    )

    @Test fun en_howDoes_steak_with_their() = assertEquals(
        "How do you like your steak?",
        forSurvey("How does [NAME] like their steak?", "en")
    )

    @Test fun en_possessive_comfort_food() = assertEquals(
        "What is your go-to comfort food?",
        forSurvey("What is [NAME]'s go-to comfort food?", "en")
    )

    @Test fun en_wouldRather_vacation() = assertEquals(
        "Would you rather vacation in California or New York?",
        forSurvey("Would [NAME] rather vacation in California or New York?", "en")
    )

    @Test fun en_doesPrefer_roadTrips() = assertEquals(
        "Do you prefer road trips or flying?",
        forSurvey("Does [NAME] prefer road trips or flying?", "en")
    )

    @Test fun en_doesPrefer_movies() = assertEquals(
        "Do you prefer movies or TV shows?",
        forSurvey("Does [NAME] prefer movies or TV shows?", "en")
    )

    @Test fun en_wouldRather_games() = assertEquals(
        "Would you rather play video games or board games?",
        forSurvey("Would [NAME] rather play video games or board games?", "en")
    )

    // ── Spanish survey ────────────────────────────────────────────────────────

    @Test fun es_prefiere_coffee() = assertEquals(
        "¿Prefieres café o té?",
        forSurvey("¿[NAME] prefiere café o té?", "es")
    )

    @Test fun es_preferiria_pizza() = assertEquals(
        "¿Preferirías comer pizza o tacos?",
        forSurvey("¿[NAME] preferiría comer pizza o tacos?", "es")
    )

    @Test fun es_prefiere_vanilla() = assertEquals(
        "¿Prefieres vainilla o chocolate?",
        forSurvey("¿[NAME] prefiere vainilla o chocolate?", "es")
    )

    @Test fun es_cual_es_la_comida() = assertEquals(
        "¿Cuál es tu comida favorita del día?",
        forSurvey("¿Cuál es la comida favorita del día de [NAME]?", "es")
    )

    @Test fun es_como_le_gusta_bistec() = assertEquals(
        "¿Cómo te gusta el bistec?",
        forSurvey("¿Cómo le gusta el bistec a [NAME]?", "es")
    )

    @Test fun es_prefiere_vacation_type() = assertEquals(
        "¿Qué tipo de vacación prefieres?",
        forSurvey("¿Qué tipo de vacación prefiere [NAME]?", "es")
    )

    @Test fun es_prefiere_movies() = assertEquals(
        "¿Prefieres películas o series de TV?",
        forSurvey("¿[NAME] prefiere películas o series de TV?", "es")
    )

    @Test fun es_preferiria_games() = assertEquals(
        "¿Preferirías jugar videojuegos o juegos de mesa?",
        forSurvey("¿[NAME] preferiría jugar videojuegos o juegos de mesa?", "es")
    )

    @Test fun es_prefiere_travel() = assertEquals(
        "¿Prefieres viajar dentro del país o al extranjero?",
        forSurvey("¿[NAME] prefiere viajar dentro del país o al extranjero?", "es")
    )

    @Test fun es_preferiria_camping() = assertEquals(
        "¿Preferirías ir de campamento o glamping?",
        forSurvey("¿[NAME] preferiría ir de campamento o glamping?", "es")
    )

    // ── forQuiz passthrough ───────────────────────────────────────────────────

    @Test fun quiz_substitutes_name() = assertEquals(
        "Does Alex prefer coffee or tea?",
        forQuiz("Does [NAME] prefer coffee or tea?", "Alex")
    )

    @Test fun quiz_does_not_rewrite_grammar() = assertEquals(
        "Does Jamie prefer movies or TV shows?",
        forQuiz("Does [NAME] prefer movies or TV shows?", "Jamie")
    )
}
