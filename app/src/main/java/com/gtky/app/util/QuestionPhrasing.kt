package com.gtky.app.util

import com.gtky.app.ui.QuestionUtils

fun forSurvey(template: String, lang: String): String =
    if (lang == "es") QuestionUtils.toSelfEs(template) else QuestionUtils.toSelfEn(template)

fun forQuiz(template: String, subjectName: String): String =
    template.replace("[NAME]", subjectName)
