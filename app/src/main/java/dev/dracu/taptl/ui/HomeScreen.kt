package dev.dracu.taptl.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.dracu.taptl.R
import dev.dracu.taptl.capture.CaptureService
import dev.dracu.taptl.capture.CaptureState
import java.util.Locale

/** Start/stop when everything is set up; otherwise the steps that are still missing. */
@Composable
fun HomeScreen(setup: Setup, capture: CaptureState, onSettings: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TapTL", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                }
            }

            if (setup.ready) {
                StatusCard(setup, capture)
                if (!setup.tileAdded) TileCard(setup)
                HowToUse()
            } else {
                SetupCard(setup)
            }
        }
    }
}

@Composable
private fun StatusCard(setup: Setup, capture: CaptureState) {
    val context = LocalContext.current
    val on = capture == CaptureState.ON
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = if (on) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (capture) {
                    CaptureState.OFF -> "Off"
                    CaptureState.REQUESTING -> "Starting…"
                    CaptureState.ON -> "On"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (on) "Open your game. English shows over the ${setup.lang.label} text."
                else "${setup.lang.label} to English",
                style = MaterialTheme.typography.bodyLarge,
                // On the highlighted card, the card's own text color reads best.
                color = if (on) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.padding(top = 12.dp)) {
                if (on) {
                    FilledTonalButton(onClick = { CaptureService.stop(context) }, Modifier.fillMaxWidth()) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = { CaptureService.requestStart(context) },
                        enabled = capture == CaptureState.OFF,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start") }
                }
            }
        }
    }
}

@Composable
private fun TileCard(setup: Setup) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Add the Translate tile", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (setup.addTile != null) "Start and stop from Quick Settings, without leaving your game."
                    else "Swipe down twice, tap the pencil, and drag Translate into your tiles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            setup.addTile?.let { add -> FilledTonalButton(onClick = add) { Text("Add") } }
        }
    }
}

@Composable
private fun HowToUse() {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How to use", style = MaterialTheme.typography.titleMedium)
        HOW_TO.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
        Text(
            "The English hides while you move in a match and comes back in menus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupCard(setup: Setup) {
    val pack = setup.pack
    val lang = setup.lang
    val downloading = pack.downloading == lang
    val done = listOf(setup.canOverlay, setup.canNotify, pack.ready == true).count { it }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Set up", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$done of 3 done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SetupStep(1, "Show over other apps", "Lets the English appear on top of your game.",
                setup.canOverlay, "Allow", setup.allowOverlay)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SetupStep(2, "Notifications", "Shows when translating is on, with a Stop button.",
                setup.canNotify, "Allow", setup.allowNotify)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SetupStep(
                3, "${lang.label} pack",
                when {
                    downloading -> downloadText(pack.progress)
                    pack.failed -> "Download failed. Check your internet and try again."
                    pack.ready == null -> "Checking…"
                    else -> "Needed to translate. About 40 MB, once. Then works offline."
                },
                pack.ready == true,
                when {
                    downloading || pack.ready == null -> null
                    pack.failed -> "Retry"
                    else -> "Download"
                },
                { pack.download(lang) },
            ) {
                if (downloading) {
                    val p = pack.progress
                    val modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    if (p != null && p.total > 0) {
                        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = modifier)
                    } else {
                        LinearProgressIndicator(modifier)
                    }
                }
            }
        }
    }
}

private fun downloadText(p: Progress?): String {
    if (p == null || p.done <= 0) return "Downloading… You can leave the app."
    val mb = { b: Long -> String.format(Locale.US, "%.1f", b / 1_000_000.0) }
    return if (p.total > 0) "Downloading… ${mb(p.done)} of ${mb(p.total)} MB"
    else "Downloading… ${mb(p.done)} MB"
}

@Composable
private fun SetupStep(
    n: Int,
    title: String,
    body: String,
    done: Boolean,
    action: String?,
    onAction: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepMark(n, done)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
            if (!done && action != null) {
                Button(onClick = onAction, Modifier.padding(top = 10.dp)) { Text(action) }
            }
        }
    }
}

/** The step number in a circle, or a tick once done. */
@Composable
private fun StepMark(n: Int, done: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(28.dp).background(if (done) scheme.primary else scheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (done) "✓" else "$n",
            style = MaterialTheme.typography.labelLarge,
            color = if (done) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}
