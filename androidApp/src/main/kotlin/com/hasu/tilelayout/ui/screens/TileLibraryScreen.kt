package com.hasu.tilelayout.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TileSource
import com.hasu.tilelayout.viewmodel.TextureLoader
import kotlinx.coroutines.launch

/**
 * Lists tile groups for a project. Supports add (FAB + dialog), swipe-to-delete,
 * and tap-through to the camera screen (capture is Phase 10).
 */
@Composable
fun TileLibraryScreen(
    projectId: String,
    onTileGroupClick: (String) -> Unit,
) {
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val textureLoader = remember { TextureLoader() }
    val scope = rememberCoroutineScope()

    var tileGroups by remember { mutableStateOf<List<TileGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(projectId, refresh) {
        isLoading = true
        tileGroups = tileGroupRepo.getByProject(projectId)
        isLoading = false
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            tileGroups.isEmpty() -> EmptyState(
                title = "No Tiles",
                hint = "Tap + to add a tile group to use in your layouts.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(tileGroups, key = { it.id }) { tileGroup ->
                    TileGroupRow(
                        tileGroup = tileGroup,
                        textureLoader = textureLoader,
                        onClick = { onTileGroupClick(tileGroup.id) },
                        onDelete = {
                            scope.launch {
                                textureLoader.evict(tileGroup.id)
                                tileGroupRepo.delete(tileGroup.id)
                                refresh++
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTileGroupDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, width, height ->
                scope.launch {
                    tileGroupRepo.insert(
                        TileGroup(
                            projectId = projectId,
                            name = name,
                            tileWidth = width,
                            tileHeight = height,
                            source = TileSource.IMPORTED,
                        )
                    )
                    showAdd = false
                    refresh++
                }
            },
        )
    }
}

@Composable
private fun TileGroupRow(
    tileGroup: TileGroup,
    textureLoader: TextureLoader,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var texture by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(tileGroup.id, tileGroup.texturePath) {
        texture = textureLoader.loadTexture(tileGroup) as? Bitmap
    }

    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumbnail = texture
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = tileGroup.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🧱")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tileGroup.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${tileGroup.tileWidth.toInt()} × ${tileGroup.tileHeight.toInt()} mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SourceBadge(tileGroup.source)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: TileSource) {
    Text(
        source.name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AddTileGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Double, height: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("600") }
    var height by remember { mutableStateOf("600") }

    val widthValue = width.toDoubleOrNull() ?: 0.0
    val heightValue = height.toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && widthValue > 0 && heightValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Tile Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tile name") },
                    singleLine = true,
                )
                TileDimensionField("Width (mm)", width, { width = it })
                TileDimensionField("Height (mm)", height, { height = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), widthValue, heightValue) },
                enabled = valid,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TileDimensionField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
