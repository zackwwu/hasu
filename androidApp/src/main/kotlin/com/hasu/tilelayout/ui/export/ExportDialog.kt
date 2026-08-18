package com.hasu.tilelayout.ui.export

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportDialog(
    title: String,
    onDismiss: () -> Unit,
    onRender: (dpi: Int) -> Bitmap?,
) {
    var dpi by remember { mutableIntStateOf(ExportHelper.DEFAULT_DPI) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(dpi) {
        previewBitmap = withContext(Dispatchers.Default) {
            onRender(dpi)
        }
    }

    val performSave: () -> Unit = {
        scope.launch {
            isSaving = true
            val success = withContext(Dispatchers.Default) {
                previewBitmap?.let { ExportHelper.saveToGallery(context, it, "TileLayout_$title") }
                    ?: false
            }
            saveMessage = if (success) "Saved to gallery!" else "Failed to save."
            isSaving = false
        }
    }

    // WRITE_EXTERNAL_STORAGE is a runtime permission on API 24-28 (legacy save path).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) performSave() else saveMessage = "Storage permission denied."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // DPI stepper
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DPI: $dpi", modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { if (dpi > ExportHelper.MIN_DPI) dpi -= 50 },
                        enabled = dpi > ExportHelper.MIN_DPI,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = { if (dpi < ExportHelper.MAX_DPI) dpi += 50 },
                        enabled = dpi < ExportHelper.MAX_DPI,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("+") }
                }

                // Preview
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Export preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f))
                    )
                } ?: CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Save message
                saveMessage?.let {
                    Text(
                        it,
                        color = if (it.contains("Failed") || it.contains("denied"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        performSave()
                    }
                },
                enabled = previewBitmap != null && !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        previewBitmap?.let { bmp ->
                            scope.launch {
                                withContext(Dispatchers.Default) {
                                    ExportHelper.shareBitmap(context, bmp, "TileLayout_$title")
                                }
                            }
                        }
                    },
                    enabled = previewBitmap != null
                ) { Text("Share") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
