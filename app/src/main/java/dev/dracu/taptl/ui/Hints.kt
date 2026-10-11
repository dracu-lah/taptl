package dev.dracu.taptl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The how-to, shown in the intro and in the "You're ready" card. */
val HOW_TO = listOf(
    "Open your game",
    "Swipe down and tap Translate",
    "English shows over the text",
)

/** A numbered line: a small filled circle with the number, then the text. */
@Composable
fun NumberedLine(n: Int, text: String, active: Boolean = true, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .background(if (active) scheme.primary else scheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$n",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) Color.Unspecified else scheme.onSurfaceVariant,
        )
    }
}

/** Shown on the setup screen once every step is done. */
@Composable
fun ReadyCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("You're ready", style = MaterialTheme.typography.titleLarge)
            HOW_TO.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Tip("The English hides while you move in a match and comes back in menus.")
            Tip("Tap the tile again to stop.")
        }
    }
}

@Composable
private fun Tip(text: String) {
    Text("Tip: $text", style = MaterialTheme.typography.bodyMedium)
}
