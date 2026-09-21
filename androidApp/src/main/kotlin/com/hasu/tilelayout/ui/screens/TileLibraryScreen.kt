package com.hasu.tilelayout.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * Lists tile groups for a project. Supports add (FAB + dialog), swipe-to-edit
 * and swipe-to-delete, texture thumbnails with tap-to-view, and a texture step
 * (capture / import / skip) after adding a tile.
 */
@Composable
fun TileLibraryScreen(
    projectId: String,
    onTileGroupClick: (String) -> Unit,
    onCaptureTexture: (String) -> Unit,
) {
    val context = LocalContext.current
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val textureLoader = remember { TextureLoader() }
    val scope = rememberCoroutineScope()

    var tileGroups by remember { mutableStateOf<List<TileGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TileGroup?>(null) }
    var textureStepGroup by remember { mutableStateOf<TileGroup?>(null) }
    var viewingTexture by remember { mutableStateOf<TileGroup?>(null) }
    var refresh by remember { mutableStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val group = textureStepGroup ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@runCatching
                val dir = File(context.filesDir, "textures").apply { mkdirs() }
                val path = File(dir, "${group.id}.png")
                FileOutputStream(path).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tileGroupRepo.getById(group.id)?.let {
                    tileGroupRepo.insert(
                        it.copy(texturePath = path.absolutePath, source = TileSource.IMPORTED)
                    )
                }
            }
            textureLoader.evict(group.id)
            textureStepGroup = null
            refresh++
        }
    }

    LaunchedEffect(projectId, refresh) {
        isLoading = true
        tileGroups = tileGroupRepo.getByProject(projectId)
        isLoading = false
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.testTag("add-tile-group"),
            ) {
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
                        onClick = {
                            // Tap shows the texture when one exists; otherwise
                            // it goes to the camera to capture one.
                            if (tileGroup.texturePath != null) {
                                viewingTexture = tileGroup
                            } else {
                                onCaptureTexture(tileGroup.id)
                            }
                        },
                        onEdit = { editingGroup = tileGroup },
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
                    val inserted = TileGroup(
                        projectId = projectId,
                        name = name,
                        tileWidth = width,
                        tileHeight = height,
                        source = TileSource.IMPORTED,
                    )
                    tileGroupRepo.insert(inserted)
                    refresh++
                    showAdd = false
                }
            },
            onAddTexture = { name, width, height ->
                scope.launch {
                    val inserted = TileGroup(
                        projectId = projectId,
                        name = name,
                        tileWidth = width,
                        tileHeight = height,
                        source = TileSource.IMPORTED,
                    )
                    tileGroupRepo.insert(inserted)
                    refresh++
                    showAdd = false
                    textureStepGroup = tileGroupRepo.getById(inserted.id) ?: inserted
                }
            },
        )
    }

    editingGroup?.let { group ->
        AddTileGroupDialog(
            editing = group,
            onDismiss = { editingGroup = null },
            onConfirm = { name, width, height ->
                scope.launch {
                    tileGroupRepo.insert(group.copy(name = name, tileWidth = width, tileHeight = height))
                    editingGroup = null
                    refresh++
                }
            },
            onAddTexture = { name, width, height ->
                scope.launch {
                    tileGroupRepo.insert(group.copy(name = name, tileWidth = width, tileHeight = height))
                    editingGroup = null
                    refresh++
                    textureStepGroup = tileGroupRepo.getById(group.id) ?: group
                }
            },
        )
    }

    textureStepGroup?.let { group ->
        TextureStepDialog(
            tileGroup = group,
            onCapture = {
                textureStepGroup = null
                onCaptureTexture(group.id)
            },
            onImport = {
                importLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSkip = { textureStepGroup = null },
        )
    }

    viewingTexture?.let { group ->
        TextureViewerDialog(tileGroup = group, onDismiss = { viewingTexture = null })
    }
}

@Composable
private fun TileGroupRow(
    tileGroup: TileGroup,
    textureLoader: TextureLoader,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var texture by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(tileGroup.id, tileGroup.texturePath) {
        texture = textureLoader.loadTexture(tileGroup) as? Bitmap
    }

    val dismissState = rememberSwipeToDismissBoxState()
    // Reset after an edit so the row springs back (delete removes the row).
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Edit", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                }
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
    editing: TileGroup? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Double, height: Double) -> Unit,
    onAddTexture: (name: String, width: Double, height: Double) -> Unit,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var width by remember { mutableStateOf(editing?.tileWidth?.toInt()?.toString() ?: "600") }
    var height by remember { mutableStateOf(editing?.tileHeight?.toInt()?.toString() ?: "600") }

    val widthValue = width.toDoubleOrNull() ?: 0.0
    val heightValue = height.toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && widthValue > 0 && heightValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "New Tile" else "Edit Tile") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { keyboard?.hide() }
                },
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tile name") },
                    singleLine = true,
                    modifier = Modifier.testTag("tile-name-field"),
                )
                TileDimensionField("Width (mm)", width, { width = it })
                TileDimensionField("Height (mm)", height, { height = it })

                // Texture entry point visible from the start: saves the tile
                // and opens the capture/import options.
                TextButton(
                    onClick = { onAddTexture(name.trim(), widthValue, heightValue) },
                    enabled = valid,
                ) {
                    Text("📷 Add Texture")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), widthValue, heightValue) },
                enabled = valid,
            ) {
                Text(if (editing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** After a tile is added: capture a texture, import a photo, or skip for now. */
@Composable
private fun TextureStepDialog(
    tileGroup: TileGroup,
    onCapture: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(tileGroup.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add a texture now? The tile's icon in the list will show it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                    Text("Capture with camera")
                }
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("Import photo")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        },
    )
}

/** Full-screen dialog showing the tile's captured/imported texture. */
@Composable
private fun TextureViewerDialog(tileGroup: TileGroup, onDismiss: () -> Unit) {
    val textureLoader = remember { TextureLoader() }
    var texture by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(tileGroup.id, tileGroup.texturePath) {
        texture = textureLoader.loadTexture(tileGroup) as? Bitmap
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${tileGroup.name} — ${tileGroup.tileWidth.toInt()} × ${tileGroup.tileHeight.toInt()} mm")
        },
        text = {
            val bmp = texture
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = tileGroup.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "No texture yet — capture or import one.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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
