package dev.dracu.taptl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dracu.taptl.Lang
import kotlinx.coroutines.launch

/** Every language as a radio list: English name, with its own name under it. */
@Composable
fun LanguageList(selected: Lang, onSelect: (Lang) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(Lang.entries) { lang ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(lang == selected, role = Role.RadioButton) { onSelect(lang) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = lang == selected, onClick = null)
                Column {
                    Text(lang.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        lang.native,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The language list in a bottom sheet. Closes itself after a pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(lang: Lang, onPick: (Lang) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Translate from", style = MaterialTheme.typography.titleLarge)
            Text(
                "The language of the text in your game.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LanguageList(lang, { picked ->
                onPick(picked)
                scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
            }, Modifier.padding(bottom = 16.dp))
        }
    }
}
