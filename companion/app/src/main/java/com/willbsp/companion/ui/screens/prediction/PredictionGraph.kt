package com.willbsp.companion.ui.screens.prediction

import android.graphics.PointF
import android.util.Range
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// Composable for drawing a graph from provided data points
// Uses Jetpack Compose Canvas composable

@Composable
fun PredictionGraph(
    modifier: Modifier = Modifier,
    dataPoints: List<PointF>,
    predictionWindowSize: Float,
    onPredictionWindowSelected: (Range<Float>) -> Unit
) {

    val textMeasurer = rememberTextMeasurer()
    var selectedPoint: Offset? by remember { mutableStateOf(null) }

    // Get colors from devices M3 color scheme
    val lineColor = MaterialTheme.colorScheme.onBackground
    val highlightColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer(alpha = 0.99f)
            .pointerInput(Unit) { detectTapGestures { selectedPoint = it } }
    ) {

        // Get minimum and maximum points
        val yMin = dataPoints.minOf { it.y }
        val yMax = dataPoints.maxOf { it.y }
        val xMin = dataPoints.minOf { it.x }
        val xMax = dataPoints.maxOf { it.x }

        // Calculate axis multipliers
        // available screen space / axis range
        val yMultiplier = (size.height) / (yMax + abs(yMin))
        val xMultiplier = (size.width) / (xMax - xMin)

        // Lambda methods to convert from data space to canvas space
        val xToCanvas: (Float) -> Float = { (it - xMin) * xMultiplier }
        val yToCanvas: (Float) -> Float = { (-it + yMax) * yMultiplier }

        val stroke = Path()
        for (i in dataPoints.indices) {

            // Convert data points to canvas coordinates
            val x = xToCanvas(dataPoints[i].x)
            val y = yToCanvas(dataPoints[i].y)

            // Build the path from coordinates
            if (i == 0) {
                stroke.reset()
                stroke.moveTo(x, y)
            } else stroke.lineTo(x, y)

        }

        // Draw the path
        drawPath(
            path = stroke,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )

        // If a point is selected, draw the selected range
        selectedPoint?.let { point ->

            // Get the x coordinate of the selected point
            val xPoint = (point.x / xMultiplier) + xMin

            // Calculate the intersection of the selected range,
            // and the range of the data
            // Ensures that no data that does not exist is selected
            val range = Range(
                xPoint - predictionWindowSize / 2,
                xPoint + predictionWindowSize / 2
            ).intersect(Range(xMin, xMax))

            // Draw the selected range, uses SrcOut blend mode
            // which inverts the area where the path has been
            // drawn
            drawRect(
                color = highlightColor,
                topLeft = Offset(xToCanvas(range.lower), 0f),
                blendMode = BlendMode.SrcOut,
                size = Size(
                    width = xToCanvas(range.upper) - xToCanvas(range.lower),
                    height = size.height
                ),
            )

            // Prediction window has been selected,
            // so propagate so that prediction can be made
            onPredictionWindowSelected(range)

        }

        // Add axis minimum and maximum text
        drawText(
            textLayoutResult = textMeasurer.measure("${yMax}g"),
            color = highlightColor,
            blendMode = BlendMode.SrcOut,
            topLeft = Offset(0f, yMax)
        )

        val yMinText = textMeasurer.measure("${yMin}g")
        drawText(
            textLayoutResult = yMinText,
            color = highlightColor,
            blendMode = BlendMode.SrcOut,
            topLeft = Offset(0f, yToCanvas(yMin) - yMinText.size.height)
        )

        val xMaxText = textMeasurer.measure("${(xMax / 1000).toInt()}s")
        drawText(
            textLayoutResult = xMaxText,
            color = highlightColor,
            blendMode = BlendMode.SrcOut,
            topLeft = Offset(size.width - xMaxText.size.width, yToCanvas(0f))
        )

        drawText(
            textLayoutResult = textMeasurer.measure("${(xMin / 1000).toInt()}s"),
            color = highlightColor,
            blendMode = BlendMode.SrcOut,
            topLeft = Offset(0f, yToCanvas(0f))
        )


    }

}

@Composable
@Preview(showSystemUi = true)
fun PredictionGraphPreview() {
    PredictionGraph(
        modifier = Modifier
            .width(350.dp)
            .height(350.dp),
        dataPoints = listOf(
            PointF(0f, 2f),
            PointF(1f, 5f),
            PointF(2f, -3f),
            PointF(3f, 0f),
            PointF(4f, 1f),
            PointF(5f, 6f),
            PointF(6f, -6f),
            PointF(7f, 0f)
        ),
        predictionWindowSize = 5000f,
        onPredictionWindowSelected = {}
    )
}

@Composable
@Preview(showSystemUi = true)
fun PredictionGraph2Preview() {
    PredictionGraph(
        modifier = Modifier
            .width(350.dp)
            .height(350.dp),
        dataPoints = listOf(
            PointF(15652f, 0.03f),
            PointF(15661f, 0.02f),
            PointF(15671f, 0.02f),
            PointF(15681f, 0.02f),
            PointF(15691f, 0.02f),
            PointF(15701f, 0.02f),
            PointF(15711f, 0.03f),
            PointF(15720f, 0.03f),
            PointF(15730f, 0.03f),
            PointF(15740f, 0.0f)
        ),
        predictionWindowSize = 5000f,
        onPredictionWindowSelected = {}
    )
}
