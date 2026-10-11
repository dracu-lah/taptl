package dev.dracu.taptl

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage

/** The OCR model a language needs. Each one is bundled in the app. */
enum class Script { CHINESE, JAPANESE, KOREAN, DEVANAGARI, LATIN }

/** A language TapTL can read and turn into English. */
enum class Lang(val code: String, val label: String, val native: String, val script: Script) {
    CHINESE(TranslateLanguage.CHINESE, "Chinese", "中文", Script.CHINESE),
    JAPANESE(TranslateLanguage.JAPANESE, "Japanese", "日本語", Script.JAPANESE),
    KOREAN(TranslateLanguage.KOREAN, "Korean", "한국어", Script.KOREAN),
    HINDI(TranslateLanguage.HINDI, "Hindi", "हिन्दी", Script.DEVANAGARI),
    SPANISH(TranslateLanguage.SPANISH, "Spanish", "Español", Script.LATIN),
    PORTUGUESE(TranslateLanguage.PORTUGUESE, "Portuguese", "Português", Script.LATIN),
    FRENCH(TranslateLanguage.FRENCH, "French", "Français", Script.LATIN),
    GERMAN(TranslateLanguage.GERMAN, "German", "Deutsch", Script.LATIN),
    ITALIAN(TranslateLanguage.ITALIAN, "Italian", "Italiano", Script.LATIN),
    VIETNAMESE(TranslateLanguage.VIETNAMESE, "Vietnamese", "Tiếng Việt", Script.LATIN),
    INDONESIAN(TranslateLanguage.INDONESIAN, "Indonesian", "Bahasa Indonesia", Script.LATIN),
    TURKISH(TranslateLanguage.TURKISH, "Turkish", "Türkçe", Script.LATIN);

    /** Is [c] a letter of this language's script (so a line is worth translating)? */
    fun isOwnLetter(c: Char): Boolean {
        val s = Character.UnicodeScript.of(c.code)
        return when (script) {
            Script.CHINESE -> s == Character.UnicodeScript.HAN
            Script.JAPANESE -> s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA ||
                s == Character.UnicodeScript.KATAKANA
            Script.KOREAN -> s == Character.UnicodeScript.HANGUL
            Script.DEVANAGARI -> s == Character.UnicodeScript.DEVANAGARI
            Script.LATIN -> s == Character.UnicodeScript.LATIN
        }
    }

    fun hasOwnLetters(s: String) = s.any { isOwnLetter(it) }

    /** Scripts without spaces between words (a gap between characters is not a word break). */
    val noSpaces get() = script == Script.CHINESE || script == Script.JAPANESE

    companion object {
        private const val PREFS = "taptl"
        private const val KEY = "source_lang"

        fun get(context: Context): Lang {
            val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.code == code } ?: CHINESE
        }

        fun set(context: Context, lang: Lang) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang.code).apply()
        }
    }
}
