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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

/** "Translate from: Chinese" card. Tapping it opens the list in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageCard(lang: Lang, onChange: (Lang) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Translate from",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${lang.label}  ·  ${lang.native}", style = MaterialTheme.typography.titleMedium)
            }
            Text("Change", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Translate from", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Changes apply the next time you start translating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                LanguageList(lang, { picked ->
                    onChange(picked)
                    scope.launch { sheet.hide() }.invokeOnCompletion { open = false }
                }, Modifier.padding(bottom = 16.dp))
            }
        }
    }
}
