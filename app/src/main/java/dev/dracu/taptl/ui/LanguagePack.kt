package dev.dracu.taptl.ui

import android.app.DownloadManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.dracu.taptl.Lang
import dev.dracu.taptl.pipeline.Translator
import kotlinx.coroutines.delay

/** Bytes done and total of a running download. Total is 0 while unknown. */
class Progress(val done: Long, val total: Long)

/** Whether the translation model for a language is on the phone, and its download. */
class LanguagePack(private val context: Context) {

    /** null while checking. */
    var ready by mutableStateOf<Boolean?>(null); private set
    var failed by mutableStateOf(false); private set
    /** The language a download is running for, so switching language mid-download shows the right state. */
    var downloading by mutableStateOf<Lang?>(null); private set
    var progress by mutableStateOf<Progress?>(null); private set

    private var checked: Lang? = null

    fun check(lang: Lang) {
        checked = lang
        ready = null
        failed = false
        Translator.isModelDownloaded(lang).addOnCompleteListener {
            if (checked == lang) ready = it.isSuccessful && it.result
        }
    }

    fun download(lang: Lang) {
        if (downloading != null) return
        downloading = lang
        failed = false
        progress = null
        Translator.downloadModel(lang).addOnCompleteListener {
            downloading = null
            progress = null
            if (checked == lang) {
                failed = !it.isSuccessful
                ready = it.isSuccessful
            }
        }
    }

    /** ML Kit downloads through DownloadManager, which only lists this app's own downloads. */
    fun pollProgress() {
        val dm = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        )
        var done = 0L
        var total = 0L
        runCatching {
            dm.query(query)?.use { c ->
                val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    done += c.getLong(doneCol).coerceAtLeast(0)
                    total += c.getLong(totalCol).coerceAtLeast(0)
                }
            }
        }
        if (downloading != null) progress = Progress(done, total)
    }
}

@Composable
fun rememberLanguagePack(lang: Lang, resumes: Int): LanguagePack {
    val context = LocalContext.current
    val pack = remember { LanguagePack(context.applicationContext) }
    LaunchedEffect(lang, resumes) { pack.check(lang) }
    LaunchedEffect(pack.downloading) {
        while (pack.downloading != null) {
            pack.pollProgress()
            delay(500)
        }
    }
    return pack
}
