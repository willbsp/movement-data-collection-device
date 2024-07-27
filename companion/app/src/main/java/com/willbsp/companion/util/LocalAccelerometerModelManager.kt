package com.willbsp.companion.util

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Range
import com.willbsp.companion.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.count
import org.jetbrains.kotlinx.dataframe.api.drop
import org.jetbrains.kotlinx.dataframe.api.dropLast
import org.jetbrains.kotlinx.dataframe.api.expr
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.forEach
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.map
import org.jetbrains.kotlinx.dataframe.api.max
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.median
import org.jetbrains.kotlinx.dataframe.api.min
import org.jetbrains.kotlinx.dataframe.api.minus
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.sort
import org.jetbrains.kotlinx.dataframe.api.std
import java.nio.FloatBuffer
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

class LocalAccelerometerModelManager @Inject constructor(
    @ApplicationContext val context: Context
) : AccelerometerModelManager {

    // Required for ONNX runtime
    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession = createOrtSession(ortEnvironment)

    // Processes data, performs inference and returns the mode prediction
    override fun runPrediction(input: DataFrame<Any?>, range: Range<Float>): String {

        // Get processed data
        val df = processData(input, range)

        // Perform inference against the data, ensuring inputs are correct and no null values
        val predictions = df.map { row ->
            val inputs = row.values().filterIsInstance<Double>().map { it.toFloat() }
            performInference(inputs)
        }.filterNotNull()

        // Return error if no predictions were made
        if (predictions.isEmpty()) return context.getString(R.string.inference_error)

        // Return the mode prediction
        return predictions
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key

    }

    // Perform inference against the raw float inputs,
    // returning the predicted class as a String
    private fun performInference(inputs: List<Float>): String? {

        // Prepare inputs (34 float features)
        val inputName = ortSession.inputNames?.iterator()?.next()
        val floatBufferInputs = FloatBuffer.wrap(inputs.toFloatArray())
        val input = OnnxTensor.createTensor(ortEnvironment, floatBufferInputs, longArrayOf(1, 34))

        // Run the prediction
        val results = ortSession.run(mapOf(inputName to input))

        // Ensure string values, and return predicted class
        val output = results[0].value as Array<*>
        return output.filterIsInstance<String>().getOrNull(0)

    }

    // Pre-process data, resampling and extracting features
    private fun processData(data: DataFrame<Any?>, range: Range<Float>): DataFrame<Any?> {

        // Get time and acceleration values
        var df = data["t", "ax", "ay", "az"]

        // Get data only in the specified range
        df = df.filter { range.contains("t"<Int>().toFloat()) }

        // Create acceleration magnitude column
        df = df.add("m") {
            sqrt("ax"<Double>().pow(2.0) + "ay"<Double>().pow(2.0) + "az"<Double>().pow(2.0))
        }

        // Resample into 1s periods, computing features
        df = df.groupBy() { expr("t") { ("t"<Long>() / 1000).toInt() } }
            .aggregate { // aggregate

                // Mean
                "ax"<Double>().mean() into "mean_x"
                "ay"<Double>().mean() into "mean_y"
                "az"<Double>().mean() into "mean_z"
                "m"<Double>().mean() into "mean_m"

                // Median
                "ax"<Double>().median() into "med_x"
                "ay"<Double>().median() into "med_y"
                "az"<Double>().median() into "med_z"
                "m"<Double>().median() into "med_m"

                // Standard deviation
                "ax"<Double>().std() into "std_x"
                "ay"<Double>().std() into "std_y"
                "az"<Double>().std() into "std_z"
                "m"<Double>().std() into "std_m"

                // Range
                "ax"<Double>().range() into "range_x"
                "ay"<Double>().range() into "range_y"
                "az"<Double>().range() into "range_z"
                "m"<Double>().range() into "range_m"

                // Average (mean) absolute difference
                "ax"<Double>().aad() into "aad_x"
                "ay"<Double>().aad() into "aad_y"
                "az"<Double>().aad() into "aad_z"
                "m"<Double>().aad() into "aad_m"

                // Median absolute difference
                "ax"<Double>().mad() into "mad_x"
                "ay"<Double>().mad() into "mad_y"
                "az"<Double>().mad() into "mad_z"
                "m"<Double>().mad() into "mad_m"

                // Interquartile range
                "ax"<Double>().iqr() into "iqr_x"
                "ay"<Double>().iqr() into "iqr_y"
                "az"<Double>().iqr() into "iqr_z"
                "m"<Double>().iqr() into "iqr_m"

                // Positive count (excludes magnitude)
                "ax"<Double>().posCount() into "pos_x"
                "ay"<Double>().posCount() into "pos_y"
                "az"<Double>().posCount() into "pos_z"

                // Negative count (excludes magnitude)
                "ax"<Double>().negCount() into "neg_x"
                "ay"<Double>().negCount() into "neg_y"
                "az"<Double>().negCount() into "neg_z"

            }.remove("t")

        return df

    }

    // Various extension functions for calculating
    // data features from DataColumns

    private fun DataColumn<Double>.range(): Double {
        return max() - min()
    }

    private fun DataColumn<Double>.aad(): Double {
        return minus(mean())
            .map { it.absoluteValue }
            .mean()
    }

    private fun DataColumn<Double>.mad(): Double {
        return minus(median())
            .map { it.absoluteValue }
            .median()
    }

    private fun DataColumn<Double>.iqr(): Double {
        val sorted = this.sort()
        val q1Median = sorted.dropLast(sorted.count() / 2).median()
        val q3Median = sorted.drop(sorted.count() / 2).median()
        return q3Median - q1Median
    }

    private fun DataColumn<Double>.posCount(): Double {
        var count = 0
        forEach { x -> if (x > 0) count++ }
        return count.toDouble()
    }

    private fun DataColumn<Double>.negCount(): Double {
        var count = 0
        forEach { x -> if (x < 0) count++ }
        return count.toDouble()
    }

    // Load the model resource, and create an ONNX runtime session
    private fun createOrtSession(ortEnvironment: OrtEnvironment): OrtSession {
        val modelBytes =
            context.resources.openRawResource(R.raw.accelerometer_model_final_v2).readBytes()
        return ortEnvironment.createSession(modelBytes)
    }

}