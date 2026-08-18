package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.models.CutEntry
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel

/**
 * Cut list tab: observes [RoomEditorViewModel.cutEntries], which the shared
 * ViewModel recomputes inside [RoomEditorViewModel.computeLayout] — so this
 * tab stays fresh reactively whenever a layout changes.
 */
@Composable
fun CutListTab(vm: RoomEditorViewModel) {
    val cutEntries by vm.cutEntries.collectAsState()

    if (cutEntries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cut tiles found. Compute a layout to see the cut list.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(cutEntries) { entry ->
                CutEntryCard(entry)
            }
        }
    }
}

@Composable
fun CutEntryCard(entry: CutEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.tileGroupName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${entry.width.toInt()}×${entry.height.toInt()}mm — ${entry.cutTypeDescription}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Total: ${entry.totalCount} tiles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                entry.locations.forEach { loc ->
                    Text(
                        "${loc.surfaceName}: ${loc.count} tiles",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Less" else "More")
            }
        }
    }
}
