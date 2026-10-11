package dev.dracu.taptl.pipeline

import android.content.Context
import org.json.JSONArray

/**
 * Hand-made settings terms first, then official Valorant names. Exact match on the whole line,
 * then longest-match replacement of known terms inside it. Single-character terms (低/中/高)
 * only ever match a whole line.
 */
class Glossary(context: Context) {

    private val exact = HashMap<String, String>()
    private val byFirstChar = HashMap<Char, MutableList<String>>() // terms ≥ 2 chars, longest first
    private val byLength = HashMap<Int, MutableList<String>>()

    init {
        // Settings first so its wording wins on overlaps.
        for (file in listOf("glossary/settings_zh_en.json", "glossary/valorant_zh_en.json")) {
            val json = context.assets.open(file).bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val zh = normalize(o.getString("zh"))
                if (zh.isEmpty() || zh in exact) continue
                exact[zh] = o.getString("en")
                if (zh.length >= 2) byFirstChar.getOrPut(zh[0]) { ArrayList() } += zh
                byLength.getOrPut(zh.length) { ArrayList() } += zh
            }
        }
        byFirstChar.values.forEach { list -> list.sortByDescending { it.length } }
    }

    val size get() = exact.size

    fun exact(line: String): String? = exact[line]

    /**
     * OCR often gets one character of a menu label wrong (辅助瞄准 → 辅助喵准). Accept a term of the
     * same length with 1 wrong character (4–6 chars) or 2 (7+), if exactly one term is that close.
     */
    fun near(line: String): String? {
        val allowed = when {
            line.length >= 7 -> 2
            line.length >= 4 -> 1
            else -> return null
        }
        var best: String? = null
        var bestDiff = Int.MAX_VALUE
        var tie = false
        for (term in byLength[line.length] ?: return null) {
            var diff = 0
            for (i in term.indices) if (term[i] != line[i] && ++diff > allowed) break
            if (diff > allowed) continue
            if (diff < bestDiff) { best = term; bestDiff = diff; tie = false } else if (diff == bestDiff) tie = true
        }
        return if (best != null && !tie) exact[best] else null
    }

    /**
     * Splits [line] into known terms (already English) and leftover pieces. Returns null when no
     * term matched, so the caller can send the whole line to the engine instead.
     */
    fun split(line: String): List<Piece>? {
        val out = ArrayList<Piece>()
        val rest = StringBuilder()
        var matched = false
        var i = 0
        while (i < line.length) {
            val term = byFirstChar[line[i]]?.firstOrNull { line.startsWith(it, i) }
            if (term != null) {
                if (rest.isNotEmpty()) { out += Piece(rest.toString(), false); rest.clear() }
                out += Piece(exact.getValue(term), true)
                i += term.length
                matched = true
            } else {
                rest.append(line[i++])
            }
        }
        if (rest.isNotEmpty()) out += Piece(rest.toString(), false)
        return if (matched) out else null
    }

    class Piece(val text: String, val translated: Boolean)

    companion object {
        // OCR sometimes returns the traditional form of a character; the game is in simplified.
        private const val TRAD = "級级賽赛輔辅隨随開开關关鏡镜動动設设顯显數数換换鍵键聲声畫画質质頻频選选擇择確确認认證证點点擊击戰战鬥斗準准槍枪彈弹頭头應应時时間间實实際际標标戶户號号碼码網网絡络線线進进階阶隊队員员從从這这個个們们樣样會会對对單单雙双專专業业東东車车門门長长風风聯联視视覺觉觸触盤盘據据為为與与無无優优處处機机錄录說说話话讀读語语輕轻載载獲获獎奖勵励寶宝歷历紀纪記记錶表體体驗验總总結结稱称圖图團团藝艺術术競竞爭争廣广場场幣币奪夺勝胜敗败領领獨独態态廳厅區区紅红綠绿藍蓝黃黄靈灵敏敏擊击鎖锁層层圍围範范調调節节靜静"
        private val toSimplified = HashMap<Char, Char>().apply {
            for (i in 0 until TRAD.length - 1 step 2) put(TRAD[i], TRAD[i + 1])
        }

        // Info icons and toggle glyphs OCR picks up at either end (!, ?, ◇, ◆, <, |, quotes…).
        private val EDGE_JUNK = Regex("^[^\\p{L}\\p{N}(]+|[^\\p{L}\\p{N})%]+$")

        /** Trim, full-width → half-width, drop trailing "!"/"?" info icons OCR picks up. */
        fun normalize(s: String, simplify: Boolean = true): String {
            val b = StringBuilder(s.length)
            for (c in s) {
                b.append(
                    when (c) {
                        '　' -> ' '
                        in '！'..'～' -> c - 0xFEE0
                        else -> if (simplify) toSimplified[c] ?: c else c
                    }
                )
            }
            return b.toString().trim().replace(EDGE_JUNK, "").replace(Regex("\\s+"), " ")
        }
    }
}
