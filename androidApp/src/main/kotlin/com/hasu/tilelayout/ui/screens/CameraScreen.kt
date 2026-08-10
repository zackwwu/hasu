package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.models.TileSource
import kotlinx.coroutines.launch

/**
 * Camera placeholder for capturing tile textures.
 * Full edge detection lands in Phase 10; for now users can assign a
 * placeholder texture (texturePath = null, source = IMPORTED).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    tileGroupId: String,
    onDismiss: () -> Unit,
) {
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val scope = rememberCoroutineScope()

    var tileGroupName by remember { mutableStateOf("") }

    LaunchedEffect(tileGroupId) {
        tileGroupName = tileGroupRepo.getById(tileGroupId)?.name ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Text("←")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Camera capture coming in Phase 10",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Capture a photo of \"$tileGroupName\" to use as its texture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Button(onClick = {
                    scope.launch {
                        val tileGroup = tileGroupRepo.getById(tileGroupId) ?: return@launch
                        tileGroupRepo.insert(
                            tileGroup.copy(texturePath = null, source = TileSource.IMPORTED)
                        )
                        onDismiss()
                    }
                }) {
                    Text("Use Placeholder Texture")
                }
            }
        }
    }
}
