package com.willbsp.companion

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.willbsp.companion.ui.CompanionNavigationGraph
import com.willbsp.companion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // Lock to portrait orientation
                    val windowSize = calculateWindowSizeClass(activity = this)
                    this.requestedOrientation = remember(key1 = windowSize) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }

                    CompanionNavigationGraph()

                }
            }
        }
    }
}
