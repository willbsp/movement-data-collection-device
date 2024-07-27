package com.willbsp.companion.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.ui.graphics.vector.ImageVector

// Define the screens (destinations) of the app
// Route identifies the screen, the icon is used for the bottom bar navigation
enum class CompanionNavigationDestination(val route: String, val icon: ImageVector) {
    Configurator("configurator", Icons.Default.Build),
    Prediction("prediction", Icons.Default.QueryStats)
}