package com.scoreorbit.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun buzz(ctx: Context, millis: Long, level: Int = 100) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        // Strength level 0..100 drives both amplitude (up to the device
        // maximum of 255) and duration, so 100 hits clearly harder than a
        // bare max-amplitude blip.
        val clamped = level.coerceIn(0, 100)
        val amplitude = (clamped * 2.55f).toInt().coerceIn(1, 255)
        val scaledMillis = (millis * (0.5f + clamped * 0.01f)).toLong().coerceAtLeast(1L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(scaledMillis, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(scaledMillis)
        }
    } catch (_: Exception) { }
}
