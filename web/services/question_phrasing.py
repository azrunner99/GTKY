"""
Convert [NAME] survey question templates to second-person ("you") form
for the survey screen, mirroring the Android QuestionUtils logic.
"""
import re


def for_survey_en(template: str) -> str:
    r = template
    replacements = [
        (r"How does \[NAME\] ", "How do you "),
        (r"What does \[NAME\] ", "What do you "),
        (r"When does \[NAME\] ", "When do you "),
        (r"Where does \[NAME\] ", "Where do you "),
        (r"does \[NAME\] ", "do you "),
        (r"Is \[NAME\]'s ", "is your "),
        (r"is \[NAME\] ", "are you "),
        (r"was \[NAME\] ", "were you "),
        (r"has \[NAME\] ", "have you "),
        (r"Would \[NAME\]'s ", "Would your "),
        (r"would \[NAME\] ", "would you "),
        (r"if \[NAME\] ", "if you "),
    ]
    for pattern, repl in replacements:
        r = re.sub(pattern, repl, r, flags=re.IGNORECASE)
    r = r.replace("[NAME]'s", "your")
    r = r.replace(" their ", " your ")
    r = r.replace(" they're ", " you're ")
    r = r.replace(" they ", " you ")
    r = r.replace(" them ", " you ")
    r = r.replace("themselves", "yourself")
    r = r.replace("[NAME]", "you")
    return r[0].upper() + r[1:] if r else r


def for_survey_es(template: str) -> str:
    r = template

    def conj(verb3: str) -> str:
        irregular = {
            "es": "eres", "era": "eras", "fue": "fuiste", "va": "vas",
            "está": "estás", "ha": "has", "tiene": "tienes", "hace": "haces",
            "cree": "crees", "trae": "traes", "viene": "vienes", "pone": "pones",
            "sabe": "sabes", "puede": "puedes", "quiere": "quieres",
            "prefiere": "prefieres", "preferiría": "preferirías",
            "mantiene": "mantienes", "tiende": "tiendes", "invierte": "inviertes",
            "creció": "creciste", "tuvo": "tuviste", "querría": "querrías",
            "podría": "podrías", "llevaría": "llevarías", "haría": "harías",
            "iría": "irías", "sería": "serías", "tendría": "tendrías",
            "vería": "verías", "daría": "darías", "diría": "dirías",
        }
        if verb3 in irregular:
            return irregular[verb3]
        if verb3.endswith("ió"):
            return verb3[:-2] + "iste"
        if verb3.endswith("ó"):
            return verb3[:-1] + "aste"
        if verb3.endswith("aba") or verb3.endswith("ía"):
            return verb3 + "s"
        if verb3.endswith("a") or verb3.endswith("e"):
            return verb3 + "s"
        return verb3

    # ¿[NAME] se [verb] → ¿Te [conj]
    r = re.sub(
        r"¿\[NAME\] se ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+)",
        lambda m: f"¿Te {conj(m.group(1))}",
        r,
    )
    # ¿[NAME] [verb] → ¿[Conj]
    r = re.sub(
        r"¿\[NAME\] ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+)",
        lambda m: f"¿{conj(m.group(1)).capitalize()}",
        r,
    )
    # ¿Cuál/Cómo es/era la/el → ¿Cuál/Cómo es/era tu
    r = re.sub(r"(¿(?:Cuál|Cómo) (?:es|era|son|eran) )(?:la |el |los |las )", r"\1tu ", r)
    # remove "de [NAME]"
    r = re.sub(r" de \[NAME\]", "", r)
    # remove "a [NAME]"
    r = re.sub(r" a \[NAME\]", "", r)
    # se [verb] [NAME]
    r = re.sub(
        r"se ([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) \[NAME\]",
        lambda m: f"te {conj(m.group(1))}",
        r,
    )
    # [verb] más [NAME]
    r = re.sub(
        r"([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) más \[NAME\]",
        lambda m: f"{conj(m.group(1))} más",
        r,
    )
    # [verb] [NAME]
    r = re.sub(
        r"([a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+) \[NAME\]",
        lambda m: conj(m.group(1)),
        r,
    )
    r = r.replace(" su ", " tu ")
    r = r.replace(" sus ", " tus ")
    r = r.replace(" le ", " te ")
    r = r.replace("sí mismo", "ti mismo")
    r = r.replace("sí misma", "ti misma")
    r = r.replace("[NAME]", "tú")
    return r


def category_label(category: str, lang: str) -> str:
    if lang != "es":
        return category
    mapping = {
        "Food": "Comida", "Travel": "Viajes", "Entertainment": "Entretenimiento",
        "Lifestyle": "Estilo de vida", "Career": "Carrera", "Social": "Social",
        "Fashion": "Moda", "Health": "Salud", "Humor": "Humor", "Money": "Dinero",
        "Movies": "Películas", "Music": "Música", "Sports": "Deportes",
        "Style": "Estilo", "Tech": "Tecnología", "Would You Rather": "¿Qué prefieres?",
        "Nostalgia": "Infancia", "Relationships": "Relaciones", "Work": "Trabajo",
        "Quirky": "Curiosidades", "Deep": "Profundo", "Home": "Hogar",
    }
    return mapping.get(category, category)
