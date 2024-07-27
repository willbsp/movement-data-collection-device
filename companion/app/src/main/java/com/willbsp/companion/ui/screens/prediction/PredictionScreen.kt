package com.willbsp.companion.ui.screens.prediction

import android.graphics.PointF
import android.net.Uri
import android.util.Range
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.willbsp.companion.R
import com.willbsp.companion.ui.theme.Typography

// Composable for prediction screen
// Same as configuration screen, all actions are defined as
// lamdas which call view model methods allowing the
// prediction screen to have no knowledge of the view model

@Composable
fun PredictionScreen(
    modifier: Modifier = Modifier,
    uiState: PredictionUiState,
    makePrediction: (Range<Float>) -> Unit,
    onPredictionWindowSizeChange: (Float) -> Unit,
    loadData: (Uri) -> Unit,
    changeChannel: (DataChannel) -> Unit,
) {

    // File browser launcher used to select a csv file
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { loadData(uri) } }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = uiState.fileName ?: stringResource(R.string.no_file_selected),
            style = Typography.headlineMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .padding(5.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {

            // Show the prediction graph if data is loaded, else show load data file text, or
            // if loading then display a progress indicator

            if (uiState.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.width(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                if (uiState.dataPoints != null) {
                    PredictionGraph(
                        modifier = Modifier,
                        dataPoints = uiState.dataPoints,
                        predictionWindowSize = uiState.predictionWindowSize,
                        onPredictionWindowSelected = { makePrediction(it) }
                    )
                } else Text(stringResource(R.string.load_a_data_file))
            }

        }

        Spacer(Modifier.height(25.dp))

        // Text displaying the current prediction
        Text(
            text = uiState.prediction ?: stringResource(R.string.make_a_prediction),
            style = Typography.titleMedium
        )

        Spacer(Modifier.height(5.dp))

        // Do not enable graph controls if no data is loaded
        val enabled = remember(uiState.dataPoints) { uiState.dataPoints != null }

        ChannelSelectionChips(
            selectedChannel = uiState.channel,
            enabled = enabled,
            changeChannel = changeChannel
        )

        Slider(
            value = uiState.predictionWindowSize,
            enabled = enabled,
            onValueChange = { onPredictionWindowSizeChange(it) },
            steps = 10,
            valueRange = 1000f..10000f
        )

        Spacer(Modifier.height(25.dp))

        // Will launch the file browser to select a csv file to laod
        ElevatedButton(
            onClick = { fileLauncher.launch(arrayOf("*/*")) }
        ) { Text(stringResource(R.string.select_data_file)) }

        Spacer(Modifier.weight(1f))

    }

}

// Show a series of buttons (chips) to select between the X, Y, or Z
// component to view on the graph
@Composable
private fun ChannelSelectionChips(
    modifier: Modifier = Modifier,
    selectedChannel: DataChannel,
    enabled: Boolean,
    changeChannel: (DataChannel) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            modifier = Modifier.padding(horizontal = 8.dp),
            enabled = enabled,
            selected = selectedChannel == DataChannel.X,
            onClick = { changeChannel(DataChannel.X) },
            label = { Text(stringResource(R.string.x)) }
        )
        FilterChip(
            modifier = Modifier.padding(horizontal = 8.dp),
            enabled = enabled,
            selected = selectedChannel == DataChannel.Y,
            onClick = { changeChannel(DataChannel.Y) },
            label = { Text(stringResource(R.string.y)) }
        )
        FilterChip(
            modifier = Modifier.padding(horizontal = 8.dp),
            enabled = enabled,
            selected = selectedChannel == DataChannel.Z,
            onClick = { changeChannel(DataChannel.Z) },
            label = { Text(stringResource(R.string.z)) }
        )
    }
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun PredictionScreenPreview() {
    PredictionScreen(
        makePrediction = {},
        loadData = { },
        changeChannel = {},
        uiState = PredictionUiState(
            dataPoints = listOf(
                PointF(0f, 2f),
                PointF(1f, 5f),
                PointF(2f, -3f),
                PointF(3f, 0f),
                PointF(4f, 1f),
                PointF(5f, 6f),
                PointF(6f, -5f),
                PointF(7f, 0f)
            ),
        ),
        onPredictionWindowSizeChange = {}
    )
}