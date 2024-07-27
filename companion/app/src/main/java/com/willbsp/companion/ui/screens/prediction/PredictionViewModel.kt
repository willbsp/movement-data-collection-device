package com.willbsp.companion.ui.screens.prediction

import android.app.Application
import android.graphics.PointF
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Range
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.willbsp.companion.R
import com.willbsp.companion.util.AccelerometerModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dropNA
import org.jetbrains.kotlinx.dataframe.api.dropNaNs
import org.jetbrains.kotlinx.dataframe.api.dropNulls
import org.jetbrains.kotlinx.dataframe.api.map
import org.jetbrains.kotlinx.dataframe.api.schema
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jetbrains.kotlinx.dataframe.schema.DataFrameSchema
import javax.inject.Inject
import kotlin.reflect.typeOf

// AndroidViewModel is used here as a reference to the Application
// context is required in order to load a file from a URI

@HiltViewModel
class PredictionViewModel @Inject constructor(
    application: Application,
    private val model: AccelerometerModelManager,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // Use StateFlows so that whenever a new value is written,
    // the relevant UI is immediately updated
    private val _uiState = MutableStateFlow(PredictionUiState())
    val uiState: StateFlow<PredictionUiState> = _uiState

    // The loaded data stored in memory
    private var data: DataFrame<Any?>? = null

    init {
        // Check to see if a data uri exists, in case of process death
        // If so, then load previous data immediately
        if (savedStateHandle.contains(DATA_URI_HANDLE)) {
            val uriString: String? = savedStateHandle[DATA_URI_HANDLE]
            loadCsvFile(Uri.parse(uriString))
        }
    }

    // Take a range and make a prediction using the
    // accelerometer model
    fun makePrediction(range: Range<Float>) {
        // Move to another thread as could potentially be a
        // long running operation
        viewModelScope.launch(Dispatchers.Default) {
            data?.let { df ->
                _uiState.value = _uiState.value.copy(
                    prediction = model.runPrediction(df, range)
                )
            }
        }
    }

    // As the data type of columns in the data frame are Double,
    // they must always be cast to double when used. setDataChannel method
    // requires floats, so this extension method is used simply to
    // improve readability
    private fun Any?.toFloat(): Float {
        return (this as Double).toFloat()
    }

    // Set channel of data to be used for populating data points
    // in the ui state, which is used to build the graph composable
    fun setDataChannel(channel: DataChannel) {
        data?.let { df ->
            when (channel) {
                DataChannel.X -> {
                    val x = df.map { PointF(it["t"].toFloat(), it["ax"].toFloat()) }
                    _uiState.value = _uiState.value.copy(dataPoints = x, channel = channel)
                }

                DataChannel.Y -> {
                    val y = df.map { PointF(it["t"].toFloat(), it["ay"].toFloat()) }
                    _uiState.value = _uiState.value.copy(dataPoints = y, channel = channel)
                }

                DataChannel.Z -> {
                    val z = df.map { PointF(it["t"].toFloat(), it["az"].toFloat()) }
                    _uiState.value = _uiState.value.copy(dataPoints = z, channel = channel)
                }
            }
        } ?: run { _uiState.value = _uiState.value.copy(channel = channel) }
    }

    fun setPredictionWindowSize(size: Float) {
        _uiState.value = _uiState.value.copy(predictionWindowSize = size)
    }

    fun loadCsvFile(uri: Uri) {
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver
        // Get the file name of the selected file
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index)
            else null
        }
        // Open the file, and move to another thread
        contentResolver.openInputStream(uri)?.let { input ->
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = uiState.value.copy(loading = true)
                // Read the CSV file, dropping any NAs, NaNs and Nulls
                DataFrame.readCSV(input)
                    .dropNA().dropNaNs().dropNulls()
                    .let { dataFrame ->
                        // Check the schema, and if correct then accept the data
                        if (validateSchema(dataFrame.schema())) {
                            data = dataFrame
                            setDataChannel(DataChannel.X)
                            savedStateHandle[DATA_URI_HANDLE] = uri.toString()
                            _uiState.value =
                                uiState.value.copy(fileName = fileName, loading = false)
                        } else {
                            // Else reset the ui state, and display invalid schema
                            _uiState.value = PredictionUiState(
                                fileName = context.getString(R.string.invalid_schema),
                                loading = false
                            )
                        }
                    }
            }
        }
    }

    // Method to check column names and types of schema
    private fun validateSchema(schema: DataFrameSchema): Boolean {
        val columns = schema.columns
        val correctColumns = columns.map { it.key }.containsAll(listOf("t", "ax", "ay", "az"))
        val correctTypes = columns.all { entry -> entry.value.type == typeOf<Double>() }
        return correctColumns && correctTypes
    }

    companion object {
        const val DATA_URI_HANDLE = "data_uri"
    }

}