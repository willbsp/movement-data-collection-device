package com.willbsp.companion.util

import android.util.Range
import org.jetbrains.kotlinx.dataframe.DataFrame

// Interface for an accelerometer model
// Given a set of input data and a data range,
// return a predicted activity class

interface AccelerometerModelManager {
    fun runPrediction(input: DataFrame<Any?>, range: Range<Float>) : String
}