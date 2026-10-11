package dev.dracu.taptl

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.dracu.taptl.pipeline.Translator
import dev.dracu.taptl.tile.TranslateTileService
import dev.dracu.taptl.ui.LanguageCard
import dev.dracu.taptl.ui.OnboardingScreen
import dev.dracu.taptl.ui.ReadyCard

class MainActivity : ComponentActivity() {

    // Bumped on resume so the screen re-reads permission state after visiting Settings.
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App(resumes.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private const val PREFS = "taptl"
private const val KEY_ONBOARDED = "onboarded"
private const val KEY_TILE_ADDED = "tile_added"

/** The intro on first run, then the setup screen. */
@Composable
private fun App(resumes: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
    AnimatedContent(
        targetState = onboarded,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
        label = "app",
    ) { done ->
        if (done) {
            SetupScreen(resumes, onShowIntro = { onboarded = false })
        } else {
            OnboardingScreen(onDone = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                onboarded = true
            })
        }
    }
}

@Composable
private fun SetupScreen(resumes: Int, onShowIntro: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var lang by remember { mutableStateOf(Lang.get(context)) }
    var tileAdded by remember { mutableStateOf(prefs.getBoolean(KEY_TILE_ADDED, false)) }
    // Reading `resumes` makes these re-check every time the app comes back.
    val canOverlay = resumes >= 0 && Settings.canDrawOverlays(context)
    val canNotify = resumes >= 0 && (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED)
    // null = checking, then true/false. "Downloading" is its own flag so the button can't double-start.
    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    // The language a download is running for, so switching language mid-download shows the right state.
    var downloadingLang by remember { mutableStateOf<Lang?>(null) }
    val downloading = downloadingLang == lang
    var downloadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(resumes, lang) {
        val checking = lang
        modelReady = null
        downloadFailed = false
        Translator.isModelDownloaded(checking).addOnCompleteListener {
            if (checking == lang) modelReady = it.isSuccessful && it.result
        }
    }
    val ready = canOverlay && canNotify && modelReady == true
    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TapTL", style = MaterialTheme.typography.headlineMedium)
            Text("Shows English over text in other languages on your screen.")

            LanguageCard(lang) {
                lang = it
                Lang.set(context, it)
            }

            AnimatedVisibility(ready, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                ReadyCard()
            }

            Step(
                title = "Show over other apps",
                body = "Needed to draw the English on top of the game. " +
                    "Find TapTL in the list and switch it on.",
                done = canOverlay,
                action = "Allow",
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }

            if (Build.VERSION.SDK_INT >= 33) {
                Step(
                    title = "Notifications",
                    body = "Shows a notice while translating is on.",
                    done = canNotify,
                    action = "Allow",
                ) { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }

            Step(
                title = "Download the translator",
                body = when {
                    downloading -> "Downloading ${lang.label}… (about 30 MB, once). You can leave this screen."
                    downloadFailed -> "Download failed. Check your connection and try again."
                    else -> "${lang.label} to English. One-time download, about 30 MB. After that it works offline."
                },
                done = modelReady == true,
                action = if (downloading || modelReady == null) null else "Download",
            ) {
                val target = lang
                downloadingLang = target
                downloadFailed = false
                Translator.downloadModel(target).addOnCompleteListener {
                    if (downloadingLang == target) downloadingLang = null
                    if (target == lang) {
                        downloadFailed = !it.isSuccessful
                        modelReady = it.isSuccessful
                    }
                }
            }

            Step(
                title = "Add the Translate tile",
                body = "Swipe down twice, tap the pencil, then drag \"Translate\" into your tiles. " +
                    "Tap it to start or stop.",
                done = tileAdded,
                action = if (Build.VERSION.SDK_INT >= 33) "Add tile" else null,
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, TranslateTileService::class.java),
                        context.getString(R.string.tile_label),
                        Icon.createWithResource(context, R.drawable.ic_tile),
                        context.mainExecutor,
                    ) { result ->
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                        ) {
                            tileAdded = true
                            prefs.edit().putBoolean(KEY_TILE_ADDED, true).apply()
                        }
                    }
                }
            }

            Text(
                "Translations are automatic, by Google Translate (on-device), and can be wrong. " +
                    "Game terms come from a built-in word list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = onShowIntro, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Show intro again")
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, action: String?, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            when {
                done -> Text("Done", color = MaterialTheme.colorScheme.primary)
                action != null -> Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
