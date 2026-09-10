package com.simplescoring.android.util

import com.simplescoring.android.model.Rotation

object RotationUtils {
    // Returns rotation angle in degrees for Compose Modifier.rotate()
    fun degrees(rotation: Rotation): Float = when (rotation) {
        Rotation.NONE -> 0f
        Rotation.ROTATED_90 -> 90f
        Rotation.ROTATED_180 -> 180f
        Rotation.ROTATED_270 -> 270f
    }
}
