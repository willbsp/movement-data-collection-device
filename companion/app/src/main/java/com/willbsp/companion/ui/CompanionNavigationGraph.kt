package com.willbsp.companion.ui

import android.Manifest
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.willbsp.companion.ui.screens.configurator.ConfiguratorScreen
import com.willbsp.companion.ui.screens.configurator.ConfiguratorViewModel
import com.willbsp.companion.ui.screens.prediction.PredictionScreen
import com.willbsp.companion.ui.screens.prediction.PredictionViewModel
import com.willbsp.companion.util.DeviceConnectionState

// Defines the navigation graph for the app
// Uses the Jetpack Compose navigation library

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CompanionNavigationGraph() {

    val navController = rememberNavController()
    val defaultRoute = CompanionNavigationDestination.Configurator.route
    val bottomBarItems = listOf(
        CompanionNavigationDestination.Configurator,
        CompanionNavigationDestination.Prediction
    )

    // Scaffold is used as top level component to host the bottom nav bar
    Scaffold(
        bottomBar = {
            BottomAppBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                // Create button on navbar for each screen
                bottomBarItems.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.route == destination.route,
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.toString()) },
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            startDestination = defaultRoute
        ) {

            // Configuration screen
            composable(
                CompanionNavigationDestination.Configurator.route,
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() },
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() })
            {
                val viewModel = hiltViewModel<ConfiguratorViewModel>()
                val requestBluetoothPermission = rememberPermissionState(
                    permission = Manifest.permission.BLUETOOTH_SCAN
                )
                ConfiguratorScreen(
                    uiState = viewModel.uiState,
                    readConfiguration = { viewModel.readConfiguration() },
                    writeConfiguration = { viewModel.writeConfiguration() },
                    toggleTracking = { viewModel.toggleTracking() },
                    updateUiState = { newUiState -> viewModel.updateUiState(newUiState) },
                    onConnectPressed = {
                        // Check if bluetooth permissions are granted,
                        // if not then launch a permission request
                        if (requestBluetoothPermission.status.isGranted) {
                            when (viewModel.uiState.connectionState) {
                                DeviceConnectionState.CONNECTED -> viewModel.disconnectFromDevice()
                                DeviceConnectionState.SCANNING -> viewModel.disconnectFromDevice()
                                else -> viewModel.connectToDevice()
                            }
                        } else {
                            requestBluetoothPermission.launchPermissionRequest()
                        }
                    },
                )
            }

            // Prediction screen
            composable(CompanionNavigationDestination.Prediction.route,
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() },
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() })
            {
                val viewModel = hiltViewModel<PredictionViewModel>()
                // Updates the ui state whenever an emission is made from the StateFlow
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                PredictionScreen(
                    uiState = uiState,
                    makePrediction = { range -> viewModel.makePrediction(range) },
                    loadData = { uri -> viewModel.loadCsvFile(uri) },
                    changeChannel = { channel -> viewModel.setDataChannel(channel) },
                    onPredictionWindowSizeChange = { size -> viewModel.setPredictionWindowSize(size)}
                )
            }

        }
    }


}