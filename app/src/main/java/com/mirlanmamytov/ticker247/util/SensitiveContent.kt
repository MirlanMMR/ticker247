package com.mirlanmamytov.ticker247.util

/**
 * Честное предупреждение вместо фильтрации: новости на деликатные темы
 * (насилие, эксплицитный контент, суицид) не скрываются из ленты,
 * а получают размытую обложку и предупреждение — тап снимает размытие.
 */
object SensitiveContent {

    private val regex = Regex(
        "изнасилован|секс с незнаком|принуждал.{0,15}секс|сексуальн.{0,15}насил|" +
        "жертв.{0,15}насил|домогательств|педофил|порнограф|" +
        "самоубийств|суицид|покончил.{0,10}с собой|" +
        "расчленён|расчленил|обезглавлен|пытк|истязал|" +
        "rape|sexual assault|sexually assault|molest|pedophil|" +
        "suicide|self-harm|self harm|" +
        "violación|abuso sexual|suicidio|" +
        "estupro|abuso sexual|suicídio",
        RegexOption.IGNORE_CASE
    )

    fun isSensitive(title: String, summary: String): Boolean =
        regex.containsMatchIn(title) || regex.containsMatchIn(summary.take(300))
}
