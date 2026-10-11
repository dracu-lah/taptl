package dev.dracu.taptl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The how-to on the home screen. */
val HOW_TO = listOf(
    "Tap Start, or the Translate tile",
    "Open your game",
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
