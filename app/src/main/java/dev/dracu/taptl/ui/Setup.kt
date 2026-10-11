package dev.dracu.taptl.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.dracu.taptl.Lang
import dev.dracu.taptl.R
import dev.dracu.taptl.tile.TranslateTileService

private const val PREFS = "taptl"
private const val KEY_TILE_ADDED = "tile_added"

/** Everything the app needs before it can translate, re-read each time the app comes back. */
class Setup(
    val lang: Lang,
    val pack: LanguagePack,
    val canOverlay: Boolean,
    val canNotify: Boolean,
    val tileAdded: Boolean,
    val allowOverlay: () -> Unit,
    val allowNotify: () -> Unit,
    /** null below Android 13, where the tile can only be added by hand. */
    val addTile: (() -> Unit)?,
) {
    /** The tile is a shortcut, not a must: Start on the home screen works without it. */
    val ready get() = canOverlay && canNotify && pack.ready == true
}

@Composable
fun rememberSetup(lang: Lang, resumes: Int): Setup {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val pack = rememberLanguagePack(lang, resumes)
    val tileLabel = stringResource(R.string.tile_label)
    var tileAdded by remember(resumes) { mutableStateOf(prefs.getBoolean(KEY_TILE_ADDED, false)) }
    // Bumped when the permission dialog closes, so canNotify is read again.
    var notifyAsks by remember { mutableIntStateOf(0) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifyAsks++
    }
    // Reading `resumes` makes these re-check every time the app comes back.
    val canOverlay = resumes >= 0 && Settings.canDrawOverlays(context)
    val canNotify = resumes + notifyAsks >= 0 && (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED)

    return Setup(
        lang = lang,
        pack = pack,
        canOverlay = canOverlay,
        canNotify = canNotify,
        tileAdded = tileAdded,
        allowOverlay = {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        },
        allowNotify = {
            // Once denied, Android stops showing the dialog, so the second tap goes to Settings.
            if (Build.VERSION.SDK_INT >= 33 && notifyAsks == 0) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        addTile = if (Build.VERSION.SDK_INT >= 33) {
            {
                context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(context, TranslateTileService::class.java),
                    tileLabel,
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
        } else null,
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}
