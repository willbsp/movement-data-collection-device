package com.willbsp.companion.ui.screens.configurator

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.willbsp.companion.R
import com.willbsp.companion.ui.theme.Typography
import com.willbsp.companion.util.DeviceConnectionState
import com.willbsp.companion.util.DeviceState

// Composable for the configurator screen
// All action are defined as lambdas and assigned on the navigation graph,
// which calls the corresponding view model method(s)
// Screen has no knowledge of the view model, completely decoupling it

@Composable
fun ConfiguratorScreen(
    modifier: Modifier = Modifier,
    uiState: ConfiguratorUiState,
    readConfiguration: () -> Unit,
    writeConfiguration: () -> Unit,
    toggleTracking: () -> Unit,
    onConnectPressed: () -> Unit,
    updateUiState: (ConfiguratorUiState) -> Unit,
) {

    var invalidConfigurationDialogOpen by remember { mutableStateOf(false) }

    // Lazy column ensures that small screen devices will be able to
    // scroll the screen, so nothing is cut off
    LazyColumn(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        item {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .height(IntrinsicSize.Min)
            ) {

                // Forms the header component, displaying title, connection state and device state
                Column(modifier.width(IntrinsicSize.Min)) {
                    Text(
                        stringResource(R.string.arduino),
                        style = Typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusText(
                        connectionState = uiState.connectionState,
                        deviceState = uiState.deviceState
                    )
                    Spacer(Modifier.height(10.dp))
                    ConfigurationButtons(
                        connectionState = uiState.connectionState,
                        deviceState = uiState.deviceState,
                        onConnectPressed = { onConnectPressed() },
                        onReadConfigurationPressed = { readConfiguration() },
                        onWriteConfigurationPressed = {
                            if (uiState.configurationIsValid) writeConfiguration()
                            else invalidConfigurationDialogOpen = true
                        },
                        onStartTrackingPressed = { toggleTracking() }
                    )
                }

                Spacer(Modifier.weight(0.4f))

                // Vector image draws the tracker graphic
                Image(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.8f)
                        .padding(top = 75.dp),
                    painter = painterResource(id = R.drawable.device_icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                )

                Spacer(Modifier.weight(0.3f))
            }

            HorizontalDivider(Modifier.padding(10.dp))
            Spacer(Modifier.height(20.dp))

            ConfigurationForm(uiState = uiState, updateUiState = updateUiState)

        }

    }

    if (invalidConfigurationDialogOpen) {
        AlertDialog(
            onDismissRequest = { invalidConfigurationDialogOpen = false },
            icon = { Icon(imageVector = Icons.Filled.Error, contentDescription = null) },
            title = { Text(stringResource(R.string.invalid_configuration)) },
            text = { Text(stringResource(R.string.invalid_configuration_desc)) },
            confirmButton = {
                Button(onClick = { invalidConfigurationDialogOpen = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

}

// Status text which is shown beneath the screen title
// Displays connection and device states
@Composable
private fun StatusText(
    modifier: Modifier = Modifier,
    connectionState: DeviceConnectionState,
    deviceState: DeviceState
) {
    Column(modifier) {
        Text(
            modifier = Modifier.padding(start = 5.dp),
            text = when (connectionState) {
                DeviceConnectionState.CONNECTED -> stringResource(R.string.connected)
                DeviceConnectionState.NO_CONNECTION -> stringResource(R.string.disconnected)
                DeviceConnectionState.SCANNING -> stringResource(R.string.scanning)
                DeviceConnectionState.ERROR -> stringResource(R.string.error)
            },
            style = Typography.labelLarge
        )
        Text(
            modifier = Modifier.padding(start = 5.dp),
            text = when (deviceState) {
                DeviceState.IDLE -> stringResource(R.string.idle)
                DeviceState.SYNCING -> stringResource(R.string.syncing)
                DeviceState.TRACKING -> stringResource(R.string.tracking)
                DeviceState.UNKNOWN -> ""
                DeviceState.ERROR -> stringResource(R.string.error)
            },
            style = Typography.labelLarge
        )
    }
}

// Configuration buttons with connect / disconnect, read, write and
// start / stop actions. Displayed to the left of the device graphic.
@Composable
private fun ConfigurationButtons(
    modifier: Modifier = Modifier,
    connectionState: DeviceConnectionState,
    deviceState: DeviceState,
    onConnectPressed: () -> Unit,
    onReadConfigurationPressed: () -> Unit,
    onWriteConfigurationPressed: () -> Unit,
    onStartTrackingPressed: () -> Unit
) {
    Column(modifier) {
        ElevatedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onConnectPressed() }
        ) {
            when (connectionState) {
                DeviceConnectionState.CONNECTED -> Text(stringResource(R.string.disconnect))
                DeviceConnectionState.SCANNING -> Text(stringResource(R.string.stop))
                else -> Text(stringResource(R.string.connect))
            }
        }
        ElevatedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = deviceState == DeviceState.IDLE,
            onClick = { onReadConfigurationPressed() }
        ) {
            Text(stringResource(R.string.read))
        }
        ElevatedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = deviceState == DeviceState.IDLE,
            onClick = { onWriteConfigurationPressed() }
        ) {
            Text(stringResource(R.string.write))
        }
        ElevatedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = deviceState == DeviceState.IDLE || deviceState == DeviceState.TRACKING,
            onClick = { onStartTrackingPressed() }
        ) {
            when (deviceState) {
                DeviceState.TRACKING -> Text(stringResource(R.string.stop))
                else -> Text(stringResource(R.string.start))
            }
        }
    }
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun MainScreenPreview() {
    ConfiguratorScreen(
        uiState = ConfiguratorUiState(
            connectionState = DeviceConnectionState.CONNECTED,
            deviceState = DeviceState.IDLE
        ),
        readConfiguration = { },
        writeConfiguration = { },
        onConnectPressed = {},
        toggleTracking = { },
        updateUiState = { }
    )
}