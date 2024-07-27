package com.willbsp.companion.ui.screens.configurator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.willbsp.companion.R
import com.willbsp.companion.util.DeviceConnectionState
import com.willbsp.companion.util.DeviceState

// Configuration form for entering values that will be written to the device /
// populated when values are read

@Composable
fun ConfigurationForm(
    modifier: Modifier = Modifier,
    uiState: ConfiguratorUiState,
    updateUiState: (ConfiguratorUiState) -> Unit
) {

    // Only enable the form if the device is connected and idle
    val formEnabled = remember(uiState.connectionState, uiState.deviceState) {
        uiState.connectionState == DeviceConnectionState.CONNECTED && uiState.deviceState == DeviceState.IDLE
    }

    Column(
        modifier.alpha(if (!formEnabled) 0.5f else 1f) // Dim form if not interactable
    ) {

        DropdownConfigurationItem(
            label = stringResource(R.string.sample_rate),
            value = stringResource(uiState.sampleRate.userReadableStringRes),
            enabled = formEnabled,
            options = SampleRate.values().map { stringResource(it.userReadableStringRes) },
            leadingIcon = Icons.Filled.Timeline,
            onValueChange = { updateUiState(uiState.copy(sampleRate = SampleRate.values()[it])) }
        )

        DropdownConfigurationItem(
            label = stringResource(R.string.data_type),
            value = stringResource(uiState.dataType.userReadableStringRes),
            enabled = formEnabled,
            options = DataType.values().map { stringResource(it.userReadableStringRes) },
            leadingIcon = Icons.Filled.Storage,
            onValueChange = { updateUiState(uiState.copy(dataType = DataType.values()[it])) }
        )

        ConfigurationItem(
            label = stringResource(R.string.data_file_name),
            value = uiState.dataFileName,
            enabled = formEnabled,
            isError = uiState.fileNameInvalid,
            leadingIcon = Icons.Filled.UploadFile,
            onValueChange = { updateUiState(uiState.copy(dataFileName = it)) }
        )

        ConfigurationItem(
            label = stringResource(R.string.tracking_time),
            value = uiState.trackingTime?.toString() ?: "",
            enabled = formEnabled,
            isError = uiState.trackingTimeInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = Icons.Filled.Timer,
            onValueChange = { updateUiState(uiState.copy(trackingTime = it.toFloatOrNull())) }
        )

    }
}

// Dropdown form items for sample rate and data type as only a very limited number
// of potential values
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DropdownConfigurationItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    leadingIcon: ImageVector,
    onValueChange: (Int) -> Unit,
) {

    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {

        Icon(
            leadingIcon,
            modifier = Modifier,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(15.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = if (enabled) !expanded else false }
        ) {

            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = value,
                enabled = enabled,
                onValueChange = {},
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, value ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            selectedIndex = index
                            onValueChange(index)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }

        }
    }
}

// Text box form items for tracking time and data file name
// input validation takes place in the view model
@Composable
private fun ConfigurationItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    leadingIcon: ImageVector,
    onValueChange: (String) -> Unit
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {

        Icon(
            leadingIcon,
            modifier = Modifier,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(15.dp))

        OutlinedTextField(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            isError = isError,
            keyboardOptions = keyboardOptions,
            label = { Text(label) },
            value = value ?: "",
            onValueChange = onValueChange
        )

    }

}
