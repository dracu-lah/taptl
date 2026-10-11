package dev.dracu.taptl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.dracu.taptl.Lang
import dev.dracu.taptl.R
import dev.dracu.taptl.capture.CaptureState

@Composable
fun SettingsScreen(
    setup: Setup,
    capture: CaptureState,
    onLang: (Lang) -> Unit,
    onShowIntro: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var picking by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }

            Section("Translation")
            Card(Modifier.fillMaxWidth()) {
                Item(
                    "Translate from", "${setup.lang.label} · ${setup.lang.native}",
                    note = if (capture == CaptureState.ON) "Takes effect the next time you start." else null,
                ) { picking = true }
                HorizontalDivider()
                val pack = setup.pack
                Item(
                    "Language pack",
                    when {
                        pack.downloading == setup.lang -> "Downloading…"
                        pack.ready == true -> "Downloaded"
                        pack.ready == null -> "Checking…"
                        else -> "Not downloaded. Download it on the home screen."
                    },
                )
            }

            Section("Permissions")
            Card(Modifier.fillMaxWidth()) {
                Item("Show over other apps", if (setup.canOverlay) "Allowed" else "Not allowed", onClick = setup.allowOverlay)
                HorizontalDivider()
                Item("Notifications", if (setup.canNotify) "Allowed" else "Not allowed", onClick = setup.allowNotify)
                HorizontalDivider()
                Item(
                    "Quick Settings tile", if (setup.tileAdded) "Added" else "Not added",
                    onClick = setup.addTile.takeIf { !setup.tileAdded },
                )
            }

            Section("About")
            Card(Modifier.fillMaxWidth()) {
                Item("Show intro again", null, onClick = onShowIntro)
                HorizontalDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Note("Translations are made on your phone and can be wrong. Game words come from a built-in list.")
                    Note("TapTL only looks at your screen, like a screenshot. It never touches the game. " +
                        "Try it on a second account first.")
                    version?.let { Note("Version $it") }
                }
            }
        }
    }

    if (picking) {
        LanguageSheet(setup.lang, onPick = onLang, onDismiss = { picking = false })
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, value: String?, note: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
