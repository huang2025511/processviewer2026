package com.processmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.processmanager.app.models.ProcessInfo
import com.processmanager.app.ui.screens.ProcessDetailScreen
import com.processmanager.app.ui.screens.ProcessListScreen
import com.processmanager.app.ui.screens.StatsScreen
import com.processmanager.app.viewmodels.ProcessViewModel

sealed class Screen {
    object ProcessList : Screen()
    object Stats : Screen()
    data class ProcessDetail(val process: ProcessInfo) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProcessManagerApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessManagerApp() {
    val viewModel: ProcessViewModel = viewModel()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ProcessList) }
    var selectedProcess by remember { mutableStateOf<ProcessInfo?>(null) }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                if (currentScreen !is Screen.ProcessDetail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen is Screen.ProcessList,
                            onClick = { currentScreen = Screen.ProcessList },
                            icon = { Icon(Icons.Default.List, contentDescription = "进程") },
                            label = { Text("进程") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Stats,
                            onClick = { currentScreen = Screen.Stats },
                            icon = { Icon(Icons.Default.ShowChart, contentDescription = "统计") },
                            label = { Text("统计") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentScreen) {
                    is Screen.ProcessList -> {
                        ProcessListScreen(
                            viewModel = viewModel,
                            onProcessClick = { process ->
                                selectedProcess = process
                                currentScreen = Screen.ProcessDetail(process)
                            }
                        )
                    }
                    is Screen.Stats -> {
                        StatsScreen(viewModel = viewModel)
                    }
                    is Screen.ProcessDetail -> {
                        selectedProcess?.let { process ->
                            ProcessDetailScreen(
                                process = process,
                                viewModel = viewModel,
                                onBack = {
                                    currentScreen = Screen.ProcessList
                                    selectedProcess = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
