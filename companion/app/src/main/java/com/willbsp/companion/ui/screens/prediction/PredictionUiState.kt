package com.willbsp.companion.ui.screens.prediction

import android.graphics.PointF

// Data class used to represent the ui state of the prediction screen
// Includes data to build the graph from the current channel,
// changing the data channel will reload data from the dataframe
// stored in the view model
data class PredictionUiState(
    val fileName: String? = null,
    val dataPoints: List<PointF>? = null,
    val channel: DataChannel = DataChannel.X,
    val prediction: String? = null,
    val loading: Boolean = false,
    val predictionWindowSize: Float = 5000f
)

enum class DataChannel() {
    X, Y, Z
}