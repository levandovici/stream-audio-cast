package com.audiocast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.audiocast.app.ui.screens.SettingsScreen
import com.audiocast.app.ui.screens.SetupScreen
import com.audiocast.app.ui.screens.StudioScreen
import com.audiocast.app.ui.theme.AudioCastTheme
import com.audiocast.app.viewmodel.AudioCastViewModel

object Routes {
    const val SETUP = "setup"
    const val STUDIO = "studio"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioCastTheme {
                AudioCastRoot()
            }
        }
    }
}

@Composable
private fun AudioCastRoot() {
    val nav = rememberNavController()
    val viewModel: AudioCastViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SETUP,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    viewModel = viewModel,
                    onContinue = { nav.navigate(Routes.STUDIO) },
                )
            }
            composable(Routes.STUDIO) {
                StudioScreen(
                    viewModel = viewModel,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
