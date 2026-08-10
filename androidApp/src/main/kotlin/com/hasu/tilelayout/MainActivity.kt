package com.hasu.tilelayout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.ui.screens.CameraScreen
import com.hasu.tilelayout.ui.screens.HomeScreen
import com.hasu.tilelayout.ui.screens.ProjectDetailScreen
import com.hasu.tilelayout.ui.screens.RegionEditorScreen
import com.hasu.tilelayout.ui.screens.RoomEditorScreen
import com.hasu.tilelayout.ui.screens.SurfaceDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppDatabase.init(applicationContext)

        setContent {
            TileLayoutApp()
        }
    }
}

/** Simple state-based navigation. */
sealed class Screen {
    object Home : Screen()
    data class ProjectDetail(val projectId: String) : Screen()
    data class RoomEditor(val projectId: String, val roomId: String) : Screen()
    data class SurfaceDetail(val projectId: String, val roomId: String, val surfaceId: String) : Screen()
    data class RegionEditor(val projectId: String, val roomId: String, val surfaceId: String) : Screen()
    data class Camera(val projectId: String, val tileGroupId: String) : Screen()
}

@Composable
fun TileLayoutApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }

            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    onProjectClick = { screen = Screen.ProjectDetail(it) }
                )

                is Screen.ProjectDetail -> {
                    val projectId = current.projectId
                    ProjectDetailScreen(
                        projectId = projectId,
                        onBack = { screen = Screen.Home },
                        onRoomClick = { screen = Screen.RoomEditor(projectId, it) },
                        onTileGroupClick = { tileGroupId ->
                            screen = Screen.Camera(projectId, tileGroupId)
                        },
                    )
                }

                is Screen.RoomEditor -> {
                    val projectId = current.projectId
                    val roomId = current.roomId
                    RoomEditorScreen(
                        projectId = projectId,
                        roomId = roomId,
                        onBack = { screen = Screen.ProjectDetail(projectId) },
                        onSurfaceClick = { screen = Screen.SurfaceDetail(projectId, roomId, it) },
                    )
                }

                is Screen.SurfaceDetail -> {
                    val projectId = current.projectId
                    val roomId = current.roomId
                    SurfaceDetailScreen(
                        projectId = projectId,
                        roomId = roomId,
                        surfaceId = current.surfaceId,
                        onBack = { screen = Screen.RoomEditor(projectId, roomId) },
                        onAddRegion = { screen = Screen.RegionEditor(projectId, roomId, current.surfaceId) },
                    )
                }

                is Screen.RegionEditor -> {
                    val projectId = current.projectId
                    val roomId = current.roomId
                    RegionEditorScreen(
                        projectId = projectId,
                        roomId = roomId,
                        surfaceId = current.surfaceId,
                        onBack = { screen = Screen.SurfaceDetail(projectId, roomId, current.surfaceId) },
                    )
                }

                is Screen.Camera -> CameraScreen(
                    tileGroupId = current.tileGroupId,
                    onDismiss = { screen = Screen.ProjectDetail(current.projectId) },
                )
            }
        }
    }
}

