package dev.dracu.taptl.pipeline

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.dracu.taptl.Lang
import dev.dracu.taptl.Script

/** ML Kit OCR for [lang]'s script (bundled models). Blocking; call only from the pipeline worker. */
class OcrEngine(private val lang: Lang) {

    private val recognizer = TextRecognition.getClient(
        when (lang.script) {
            Script.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            Script.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            Script.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
            Script.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
            Script.LATIN -> TextRecognizerOptions.DEFAULT_OPTIONS
        }
    )

    class Line(val text: String, val box: Rect)

    /** Lines that contain [lang]'s letters, with boxes in [bitmap] pixels. */
    fun read(bitmap: Bitmap): List<Line> {
        val text = Translator.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val out = ArrayList<Line>()
        for (block in text.textBlocks) for (line in block.lines) {
            val box = line.boundingBox ?: continue
            for (part in splitAtGaps(line.elements, box)) {
                if (lang.hasOwnLetters(part.text)) out += part
            }
        }
        return out
    }

    /**
     * ML Kit joins a row of separate buttons (商城 · 英雄 · 武器…) into one line, sometimes even into
     * one word. Walk the characters and cut wherever the gap to the next one is wider than [GAP] ×
     * the line height, so each button gets its own box. Separator dots and bars are dropped.
     */
    private fun splitAtGaps(elements: List<Text.Element>, lineBox: Rect): List<Line> {
        val chars = ArrayList<Pair<String, Rect>>()
        for (e in elements) {
            val syms = e.symbols.filter { it.boundingBox != null }
            if (syms.isNotEmpty()) syms.forEach { chars += it.text to it.boundingBox!! }
            else e.boundingBox?.let { chars += e.text to it }
        }
        chars.sortBy { it.second.left }
        val out = ArrayList<Line>()
        val text = StringBuilder()
        var box: Rect? = null
        fun flush() {
            box?.let { out += Line(text.toString(), it) }
            text.clear()
            box = null
        }
        for ((t, r) in chars) {
            if (t.none { it.isLetterOrDigit() }) continue // ·, |, commas between buttons
            // In a non-Latin language, Latin letters are icons misread or our own English read back.
            if (lang.script != Script.LATIN && t.all { it.isLetter() && !lang.isOwnLetter(it) }) continue
            val cur = box
            if (cur != null && r.left - cur.right > lineBox.height() * GAP) flush()
            val prev = box
            if (prev == null) {
                box = Rect(r)
            } else {
                // Chinese and Japanese have no spaces; other words keep one.
                val noSpace = lang.noSpaces && (lang.isOwnLetter(text.last()) || lang.isOwnLetter(t[0]))
                if (!noSpace && r.left - prev.right > lineBox.height() * 0.2f) text.append(' ')
                prev.union(r)
            }
            text.append(t)
        }
        flush()
        return out
    }

    fun close() = recognizer.close()

    private companion object {
        const val GAP = 0.6f
    }
}
