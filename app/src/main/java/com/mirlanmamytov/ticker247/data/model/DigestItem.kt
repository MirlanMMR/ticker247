package com.mirlanmamytov.ticker247.data.model

/** Один пункт дайджеста — облегчённая версия NewsItem для хранения в SharedPreferences */
data class DigestItem(
    val title: String,
    val url: String,
    val source: String
)
