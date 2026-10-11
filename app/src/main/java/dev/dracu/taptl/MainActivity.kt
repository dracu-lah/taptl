package dev.dracu.taptl

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.dracu.taptl.capture.CaptureService
import dev.dracu.taptl.ui.HomeScreen
import dev.dracu.taptl.ui.OnboardingScreen
import dev.dracu.taptl.ui.SettingsScreen
import dev.dracu.taptl.ui.rememberSetup

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

private enum class Screen { INTRO, HOME, SETTINGS }

/** The intro on first run, then home, with settings one tap away. */
@Composable
private fun App(resumes: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var screen by remember {
        mutableStateOf(if (prefs.getBoolean(KEY_ONBOARDED, false)) Screen.HOME else Screen.INTRO)
    }
    // The intro can change the language, so read it again whenever the screen changes.
    var lang by remember(screen) { mutableStateOf(Lang.get(context)) }
    val setup = rememberSetup(lang, resumes)
    val capture by CaptureService.states.collectAsState()

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "screen",
    ) { s ->
        when (s) {
            Screen.INTRO -> OnboardingScreen(onDone = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                screen = Screen.HOME
            })
            Screen.HOME -> HomeScreen(setup, capture, onSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(
                setup, capture,
                onLang = { lang = it; Lang.set(context, it) },
                onShowIntro = { screen = Screen.INTRO },
                onBack = { screen = Screen.HOME },
            )
        }
    }
}
