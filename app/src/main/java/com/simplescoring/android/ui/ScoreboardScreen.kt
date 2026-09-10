package com.simplescoring.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.simplescoring.android.model.Game
import com.simplescoring.android.util.RotationUtils
import com.simplescoring.android.viewmodel.AppScreen
import com.simplescoring.android.viewmodel.ScoreViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Points scored by one full turn of the ring, scaled by the score step. */
private fun turnValue(step: Int) = 10 * step.coerceAtLeast(1)

/** Seat angle in radians for [index] of [total] (seat 0 at top, clockwise, y-down). */
private fun seatAngle(index: Int, total: Int): Double =
    (-90.0 + index * 360.0 / total) * PI / 180.0

private fun nearestSeat(point: Offset, center: Offset, total: Int): Int {
    val a = atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in 0 until total) {
        var d = abs(a - seatAngle(i, total)) % (2 * PI)
        if (d > PI) d = 2 * PI - d
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

@Composable
fun ScoreboardScreen(game: Game, viewModel: ScoreViewModel) {
    val ctx = LocalContext.current
    var latestGame by remember { mutableStateOf(game) }
    latestGame = game

    var activeId by remember(game.id) { mutableStateOf<String?>(null) }
    var pending by remember(game.id) { mutableIntStateOf(0) }
    var accRadians by remember(game.id) { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }

    val turn = turnValue(game.step)
    val activePlayer = game.players.firstOrNull { it.id == activeId }

    fun resetGesture() {
        activeId = null
        pending = 0
        accRadians = 0f
        lastAngle = Float.NaN
    }

    fun commitGesture() {
        val id = activeId
        val delta = pending
        resetGesture()
        if (id != null && delta != 0) viewModel.addScore(id, delta)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Minimal top bar like iOS: list left, gear right.
        Row(
            modifier = Modifier.height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.go(AppScreen.ScoreHistory) }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Score history", tint = Color.White.copy(alpha = 0.85f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = game.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.go(AppScreen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.85f))
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val cx = wPx / 2f
            val cy = hPx / 2f
            val n = game.players.size.coerceAtLeast(1)
            val ringR = min(wPx, hPx) * 0.335f
            val share = (2 * PI.toFloat() * ringR / n) * 0.70f
            val dotD = share.coerceIn(
                with(density) { 40.dp.toPx() },
                with(density) { 84.dp.toPx() },
            )
            val trackWidth = dotD * 1.18f
            val labelGap = with(density) { 30.dp.toPx() }
            val labelR = ringR + dotD / 2f + labelGap
            val labelW = with(density) { 120.dp.toPx() }

            val scoreSp = when {
                n <= 2 -> 68f
                n <= 4 -> 58f
                n <= 6 -> 46f
                n <= 9 -> 38f
                else -> 32f
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.id, game.step, game.allowNegative) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                val center = Offset(cx, cy)
                                val seat = nearestSeat(offset, center, g.players.size)
                                activeId = g.players[seat].id
                                val a = atan2(
                                    (offset.y - cy).toDouble(),
                                    (offset.x - cx).toDouble()
                                ).toFloat()
                                lastAngle = a
                                accRadians = 0f
                                pending = 0
                            },
                            onDragEnd = { commitGesture() },
                            onDragCancel = { resetGesture() },
                            onDrag = { change, _ ->
                                change.consume()
                                val g = latestGame
                                val id = activeId ?: return@detectDragGestures
                                if (lastAngle.isNaN()) {
                                    lastAngle = atan2(
                                        (change.position.y - cy).toDouble(),
                                        (change.position.x - cx).toDouble()
                                    ).toFloat()
                                    return@detectDragGestures
                                }
                                val angle = atan2(
                                    (change.position.y - cy).toDouble(),
                                    (change.position.x - cx).toDouble()
                                ).toFloat()
                                var delta = angle - lastAngle
                                while (delta > PI.toFloat()) delta -= 2f * PI.toFloat()
                                while (delta < -PI.toFloat()) delta += 2f * PI.toFloat()
                                if (abs(delta) > PI.toFloat() / 2f) {
                                    lastAngle = angle
                                    return@detectDragGestures
                                }
                                lastAngle = angle
                                accRadians += delta
                                var p = (accRadians / (2f * PI.toFloat()) * turn).roundToInt()
                                if (!g.allowNegative) {
                                    p = p.coerceAtLeast(-g.currentScore(id))
                                }
                                if (p != pending) {
                                    pending = p
                                    buzz(ctx, 6)
                                }
                            }
                        )
                    }
            ) {
                // Track ring + active progress arc.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF262626),
                        radius = ringR,
                        center = Offset(cx, cy),
                        style = Stroke(width = trackWidth),
                    )
                    val ap = activePlayer
                    if (ap != null && pending != 0) {
                        val sweep = (pending.toFloat() / turn.toFloat() * 360f)
                            .coerceIn(-360f, 360f)
                        drawArc(
                            color = Color(ap.color),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = trackWidth),
                        )
                    }
                }

                // Player dots on the ring.
                val dotSizeDp: Dp = with(density) { dotD.toDp() }
                val labelWidthDp: Dp = with(density) { labelW.toDp() }
                game.players.forEachIndexed { i, player ->
                    val a = seatAngle(i, n)
                    val dotCx = cx + cos(a).toFloat() * ringR
                    val dotCy = cy + sin(a).toFloat() * ringR
                    val dim = activeId != null && activeId != player.id
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (dotCx - dotD / 2f).toInt(),
                                    y = (dotCy - dotD / 2f).toInt(),
                                )
                            }
                            .size(dotSizeDp)
                            .alpha(if (dim) 0.35f else 1f)
                            .clip(CircleShape)
                            .background(Color(player.color))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    buzz(ctx, 12)
                                    viewModel.addScore(player.id, game.step)
                                },
                            )
                    )
                }

                // Score labels outside the ring, rotated to face their player.
                // Tap a score to rotate it 90° (iOS behavior).
                game.players.forEachIndexed { i, player ->
                    val a = seatAngle(i, n)
                    val lx = cx + cos(a).toFloat() * labelR
                    val ly = cy + sin(a).toFloat() * labelR
                    val score = game.currentScore(player.id)
                    val dim = activeId != null && activeId != player.id
                    val isActive = activeId == player.id && pending != 0
                    val lastDelta = game.entries.lastOrNull { it.playerId == player.id }?.delta
                    val digits = abs(score).toString().length
                    val size = (scoreSp - (digits - 1).coerceAtLeast(0) * 5f).coerceAtLeast(20f)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (lx - labelW / 2f).toInt(),
                                    y = (ly - with(density) { 52.dp.toPx() }).toInt(),
                                )
                            }
                            .width(labelWidthDp)
                            .alpha(if (dim) 0.4f else 1f)
                            .rotate(RotationUtils.degrees(player.rotation))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    buzz(ctx, 10)
                                    viewModel.rotatePlayer(player.id)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = player.name,
                                fontSize = 13.sp,
                                color = Color(player.color),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "$score",
                                fontSize = size.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(player.color),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            when {
                                isActive -> Text(
                                    text = if (pending > 0) "+$pending" else "$pending",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(player.color),
                                )
                                game.keepLastVisible && lastDelta != null -> Text(
                                    text = if (lastDelta >= 0) "last +$lastDelta" else "last $lastDelta",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
