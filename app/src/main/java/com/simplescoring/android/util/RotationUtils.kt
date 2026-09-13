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

    /**
     * Sensible initial label orientation for a player seated at [index] of
     * [total] seats around a ring (seat 0 at the top, clockwise).
     * Top/bottom seats read upright; left/right seats face outward.
     * Top/bottom seats read upright; left/right seats face outward.
     */
    fun defaultForPosition(index: Int, total: Int): Rotation {
        if (total <= 0) return Rotation.NONE
        val angle = -90.0 + index * 360.0 / total
        val a = ((angle % 360) + 360) % 360
        return when {
            a >= 315 || a < 45 -> Rotation.ROTATED_90
            a >= 45 && a < 135 -> Rotation.NONE
            a >= 135 && a < 225 -> Rotation.ROTATED_270
            else -> Rotation.NONE
        }
    }
}
