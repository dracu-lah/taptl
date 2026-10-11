// One-off generator for app/src/main/assets/glossary/valorant_zh_en.json.
// Pulls official Valorant names from valorant-api.com in zh-CN and en-US and joins them by uuid.
// Run from the repo root:  kotlinc -script tools/build_glossary.kts
// No dependencies: a tiny JSON reader and writer are included below.

import java.io.File
import java.net.URI

// ---- tiny JSON ----
class Json(private val s: String) {
    private var i = 0
    fun parse(): Any? { ws(); return value() }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun value(): Any? {
        ws()
        return when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> num()
        }
    }
    private fun obj(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        i++; ws()
        if (s[i] == '}') { i++; return m }
        while (true) {
            ws(); val k = str(); ws(); i++ // ':'
            m[k] = value(); ws()
            if (s[i++] == '}') return m
        }
    }
    private fun arr(): List<Any?> {
        val l = ArrayList<Any?>()
        i++; ws()
        if (s[i] == ']') { i++; return l }
        while (true) {
            l += value(); ws()
            if (s[i++] == ']') return l
        }
    }
    private fun str(): String {
        val b = StringBuilder()
        i++
        while (true) {
            val c = s[i++]
            when (c) {
                '"' -> return b.toString()
                '\\' -> when (val e = s[i++]) {
                    'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                    'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                    'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> b.append(e)
                }
                else -> b.append(c)
            }
        }
    }
    private fun num(): Double {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return s.substring(start, i).toDouble()
    }
}

fun quote(v: String) = buildString {
    append('"')
    for (c in v) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
        else -> append(c)
    }
    append('"')
}

@Suppress("UNCHECKED_CAST")
fun Any?.obj() = this as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
fun Any?.list() = (this as? List<Any?>) ?: emptyList()

fun fetch(path: String, lang: String): List<Any?> {
    val sep = if ('?' in path) '&' else '?'
    val url = "https://valorant-api.com/v1/$path${sep}language=$lang"
    System.err.println("GET $url")
    val text = URI(url).toURL().readText()
    return Json(text).parse().obj()["data"].list()
}

// ---- join ----
data class Entry(val zh: String, val en: String, val kind: String)
val out = LinkedHashMap<String, Entry>() // keyed by zh; first one wins

fun add(zh: Any?, en: Any?, kind: String) {
    val z = (zh as? String)?.trim().orEmpty()
    val e = (en as? String)?.trim().orEmpty()
    if (z.isEmpty() || e.isEmpty() || z == e) return
    if (z.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return
    out.putIfAbsent(z, Entry(z, e, kind))
}

fun byUuid(list: List<Any?>) = list.associateBy { it.obj()["uuid"] as String }

fun both(path: String, block: (zh: Map<String, Any?>, en: Map<String, Any?>) -> Unit) {
    val zh = fetch(path, "zh-CN")
    val en = byUuid(fetch(path, "en-US"))
    for (z in zh) {
        val e = en[z.obj()["uuid"]] ?: continue
        block(z.obj(), e.obj())
    }
}

both("agents?isPlayableCharacter=true") { z, e ->
    add(z["displayName"], e["displayName"], "agent")
    add(z["role"]?.obj()?.get("displayName"), e["role"]?.obj()?.get("displayName"), "role")
    val enAbilities = e["abilities"].list().associateBy { it.obj()["slot"] }
    for (a in z["abilities"].list()) {
        val match = enAbilities[a.obj()["slot"]] ?: continue
        add(a.obj()["displayName"], match.obj()["displayName"], "ability")
    }
}
both("weapons") { z, e ->
    add(z["displayName"], e["displayName"], "weapon")
    add(z["shopData"]?.obj()?.get("categoryText"), e["shopData"]?.obj()?.get("categoryText"), "weapon")
}
both("maps") { z, e ->
    // Only real maps have a tactical description; the rest are menus and test levels.
    if (z["tacticalDescription"] != null) add(z["displayName"], e["displayName"], "map")
}
both("gamemodes") { z, e -> add(z["displayName"], e["displayName"], "mode") }
both("gear") { z, e ->
    add(z["displayName"], e["displayName"], "gear")
    add(z["shopData"]?.obj()?.get("categoryText"), e["shopData"]?.obj()?.get("categoryText"), "gear")
}
run {
    // Tiers have no uuid: join on the tier number inside the newest table. Tiers 1-2 are unused.
    val zh = fetch("competitivetiers", "zh-CN").last().obj()
    val en = byUuid(fetch("competitivetiers", "en-US"))[zh["uuid"]]!!.obj()
    val enTiers = en["tiers"].list().associateBy { (it.obj()["tier"] as Double).toInt() }
    for (t in zh["tiers"].list()) {
        val n = (t.obj()["tier"] as Double).toInt()
        if (n == 1 || n == 2) continue
        val e = enTiers[n]?.obj() ?: continue
        // English tier names are all caps ("IRON 2"); make them read like the game's menus.
        val en2 = (e["tierName"] as String).lowercase().split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        add(t.obj()["tierName"], en2, "rank")
    }
}

val file = File("app/src/main/assets/glossary/valorant_zh_en.json")
file.writeText(
    out.values.joinToString(",\n", "[\n", "\n]\n") {
        " {\"zh\": ${quote(it.zh)}, \"en\": ${quote(it.en)}, \"kind\": ${quote(it.kind)}}"
    }
)
System.err.println("wrote ${out.size} entries to $file")
