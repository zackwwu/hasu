package com.hasu.tilelayout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.ui.screens.HomeScreen
import com.hasu.tilelayout.ui.screens.ProjectDetailScreen

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

/** Simple state-based navigation. The RoomEditor screen is a stub until Task 16. */
sealed class Screen {
    object Home : Screen()
    data class ProjectDetail(val projectId: String) : Screen()
    data class RoomEditor(val projectId: String, val roomId: String) : Screen()
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
                    )
                }

                is Screen.RoomEditor -> {
                    val projectId = current.projectId
                    RoomEditorPlaceholder(
                        roomId = current.roomId,
                        onBack = { screen = Screen.ProjectDetail(projectId) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomEditorPlaceholder(roomId: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Room editor coming soon", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Room $roomId", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
