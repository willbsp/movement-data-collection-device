package com.willbsp.companion.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Kotlin extension methods for converting to / from ByteArrays
// required for reading and writing to BLE characteristics

fun ByteArray.toInt(): Int {
    return ByteBuffer.allocate(Int.SIZE_BYTES)
        .put(this)
        .order(ByteOrder.LITTLE_ENDIAN)
        .getInt(0)
}

fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(Int.SIZE_BYTES)
        .putInt(0, this)
        .array()
        .reversedArray()
}
