package dev.dracu.taptl.pipeline

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.dracu.taptl.Lang
import dev.dracu.taptl.TAG
import java.util.concurrent.TimeUnit

/** Glossary (Chinese only), then ML Kit on-device translation to English (engine results are cached). Blocking; call only from the pipeline worker. */
class Translator(context: Context, private val lang: Lang) {

    // The word list is Valorant Mobile CN only.
    private val glossary = if (lang == Lang.CHINESE) Glossary(context) else null
    private val cache = TranslationCache(context)
    private val engine = Translation.getClient(options(lang))
    private val model = model(lang)
    // Cache rows from before other languages existed are Chinese, keyed by the bare text.
    private val keyPrefix = if (lang == Lang.CHINESE) "" else "${lang.code}:"
    private var modelReady = false
    private var modelCheckedAt = 0L

    // Counters for the timing log.
    var glossaryHits = 0; private set
    var cacheHits = 0; private set
    var engineCalls = 0; private set

    /** Returns English for one OCR line, or null when nothing could be translated. */
    fun translate(raw: String): String? {
        val line = Glossary.normalize(raw, simplify = lang == Lang.CHINESE)
        if (!lang.hasOwnLetters(line)) return null
        val glossary = glossary
        if (glossary != null) {
            glossary.exact(line)?.let { glossaryHits++; return it }
            glossary.near(line)?.let { glossaryHits++; return it }
            // OCR often reads an icon next to a label as one more character (⌂万象地带 → 合万象地带).
            if (line.length >= 3) {
                (glossary.exact(line.substring(1)) ?: glossary.exact(line.dropLast(1)))?.let { glossaryHits++; return it }
            }
        }
        // A lone character is usually an icon OCR mistook for one (☰ → 三); trust only known ones.
        if (line.count { lang.isOwnLetter(it) } < 2) return null

        val pieces = glossary?.split(line)
        val en = if (pieces != null) {
            glossaryHits++
            pieces.joinToString(" ") { p ->
                if (p.translated || !lang.hasOwnLetters(p.text)) p.text.trim() else engine(p.text.trim()) ?: p.text.trim()
            }.replace(Regex("\\s+"), " ").trim()
        } else {
            engine(line) ?: return null
        }
        // Latin-script languages: text already in English comes back unchanged; leave it alone.
        if (en.equals(line, ignoreCase = true)) return null
        Log.v(TAG, "\"$line\" → \"$en\"")
        return en
    }

    private fun engine(text: String): String? {
        if (!isModelReady()) return null
        cache.get(keyPrefix + text)?.let { cacheHits++; return it }
        return try {
            engineCalls++
            await(engine.translate(text)).also { cache.put(keyPrefix + text, it) }
        } catch (e: Exception) {
            Log.w(TAG, "translate failed for \"$text\"", e)
            null
        }
    }

    private fun isModelReady(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (modelReady || now - modelCheckedAt < 10_000) return modelReady
        modelCheckedAt = now
        val ready = try {
            await(RemoteModelManager.getInstance().isModelDownloaded(model))
        } catch (e: Exception) {
            false
        }
        if (!ready) Log.w(TAG, "${lang.code}→en model not downloaded; only the glossary works")
        modelReady = ready
        return ready
    }

    fun close() {
        engine.close()
        cache.close()
    }

    companion object {
        private fun options(lang: Lang) = TranslatorOptions.Builder()
            .setSourceLanguage(lang.code)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()

        private fun model(lang: Lang) = TranslateRemoteModel.Builder(lang.code).build()

        /** The model download the home screen starts (~40 MB per language). */
        fun downloadModel(lang: Lang): Task<Void> = Translation.getClient(options(lang)).let { client ->
            client.downloadModelIfNeeded().addOnCompleteListener { client.close() }
        }

        fun isModelDownloaded(lang: Lang): Task<Boolean> =
            RemoteModelManager.getInstance().isModelDownloaded(model(lang))

        fun <T> await(task: Task<T>): T = Tasks.await(task, 10, TimeUnit.SECONDS)
    }
}
