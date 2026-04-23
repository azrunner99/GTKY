package com.gtky.app.ui

object QuestionUtils {

    fun toSelfEn(template: String): String {
        var r = template
        r = r.replace("How does [NAME] ", "How do you ", ignoreCase = true)
        r = r.replace("What does [NAME] ", "What do you ", ignoreCase = true)
        r = r.replace("When does [NAME] ", "When do you ", ignoreCase = true)
        r = r.replace("Where does [NAME] ", "Where do you ", ignoreCase = true)
        r = r.replace("does [NAME] ", "do you ", ignoreCase = true)
        r = r.replace("Is [NAME]'s ", "Is your ", ignoreCase = true)
        r = r.replace("is [NAME] ", "are you ", ignoreCase = true)
        r = r.replace("was [NAME] ", "were you ", ignoreCase = true)
        r = r.replace("has [NAME] ", "have you ", ignoreCase = true)
        r = r.replace("Would [NAME]'s ", "Would your ", ignoreCase = true)
        r = r.replace("would [NAME] ", "would you ", ignoreCase = true)
        r = r.replace("if [NAME] ", "if you ", ignoreCase = true)
        r = r.replace("[NAME]'s", "your")
        r = r.replace(" their ", " your ")
        r = r.replace(" they're ", " you're ")
        r = r.replace(" they ", " you ")
        r = r.replace(" them ", " you ")
        r = r.replace("themselves", "yourself")
        r = r.replace("[NAME]", "you")
        return r.replaceFirstChar { it.uppercase() }
    }

    fun toSelfEs(template: String): String {
        var r = template

        // Reflexive at start: ¿[NAME] se [verb]
        r = Regex("¿\\[NAME\\] se ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+)").replace(r) { m ->
            "¿Te ${conj(m.groupValues[1])}"
        }

        // Subject at start: ¿[NAME] [verb]
        r = Regex("¿\\[NAME\\] ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+)").replace(r) { m ->
            "¿${conj(m.groupValues[1]).replaceFirstChar { it.uppercase() }}"
        }

        // ¿Cuál/Cómo es/era (la|el|los|las) → ¿Cuál/Cómo es/era tu
        r = Regex("(¿(?:Cuál|Cómo) (?:es|era|son|eran) )(?:la |el |los |las )").replace(r, "$1tu ")

        // Remove possessive " de [NAME]"
        r = r.replace(Regex(" de \\[NAME\\]"), "")

        // Remove indirect object " a [NAME]"
        r = r.replace(Regex(" a \\[NAME\\]"), "")

        // Reflexive where [NAME] follows verb: se [verb] [NAME]
        r = Regex("se ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) \\[NAME\\]").replace(r) { m ->
            "te ${conj(m.groupValues[1])}"
        }

        // Verb + adverb + [NAME]: e.g. "usa más [NAME]" → "usas más"
        r = Regex("([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) más \\[NAME\\]").replace(r) { m ->
            "${conj(m.groupValues[1])} más"
        }

        // Verb directly before [NAME]: [verb] [NAME]
        r = Regex("([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) \\[NAME\\]").replace(r) { m ->
            conj(m.groupValues[1])
        }

        // Possessive pronouns
        r = r.replace(" su ", " tu ")
        r = r.replace(" sus ", " tus ")

        // Indirect object pronoun
        r = r.replace(" le ", " te ")

        // Reflexive pronoun (sí mismo)
        r = r.replace("sí mismo", "ti mismo")
        r = r.replace("sí misma", "ti misma")

        // Fallback: remaining [NAME] → tú
        r = r.replace("[NAME]", "tú")

        return r
    }

    private fun conj(verb3: String): String {
        val irregular = mapOf(
            "es" to "eres",
            "era" to "eras",
            "fue" to "fuiste",
            "va" to "vas",
            "está" to "estás",
            "ha" to "has",
            "tiene" to "tienes",
            "hace" to "haces",
            "cree" to "crees",
            "trae" to "traes",
            "viene" to "vienes",
            "pone" to "pones",
            "sabe" to "sabes",
            "puede" to "puedes",
            "quiere" to "quieres",
            "prefiere" to "prefieres",
            "preferiría" to "preferirías",
            "mantiene" to "mantienes",
            "tiende" to "tiendes",
            "invierte" to "inviertes",
            "creció" to "creciste",
            "tuvo" to "tuviste",
            "querría" to "querrías",
            "podría" to "podrías",
            "llevaría" to "llevarías",
            "haría" to "harías",
            "iría" to "irías",
            "sería" to "serías",
            "tendría" to "tendrías",
            "vería" to "verías",
            "daría" to "darías",
            "diría" to "dirías"
        )
        irregular[verb3]?.let { return it }

        return when {
            verb3.endsWith("ió") -> verb3.dropLast(2) + "iste"
            verb3.endsWith("ó") -> verb3.dropLast(1) + "aste"
            verb3.endsWith("aba") -> verb3 + "s"
            verb3.endsWith("ía") -> verb3 + "s"
            verb3.endsWith("a") -> verb3 + "s"
            verb3.endsWith("e") -> verb3 + "s"
            else -> verb3
        }
    }
}
