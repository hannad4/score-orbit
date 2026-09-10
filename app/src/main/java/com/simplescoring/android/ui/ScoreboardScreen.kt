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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.simplescoring.android.model.Game
import com.simplescoring.android.model.Player
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
    var dragBase by remember { mutableIntStateOf(0) }

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
            if (min(wPx, hPx) <= 0f || game.players.isEmpty()) return@BoxWithConstraints

            val cx = wPx / 2f
            val cy = hPx / 2f
            val n = game.players.size
            val minDim = min(wPx, hPx)
            fun dp(px: Float): Dp = with(density) { px.toDp() }

            // --- Fit the ring + dots + labels inside the screen. ---
            // Labels live in rotation-proof square boxes so sideways seats
            // need no more room than upright ones.
            val labelBoxPx = when {
                n <= 4 -> with(density) { 104.dp.toPx() }
                n <= 6 -> with(density) { 88.dp.toPx() }
                n <= 9 -> with(density) { 72.dp.toPx() }
                else -> with(density) { 60.dp.toPx() }
            }
            val gapPx = with(density) { 12.dp.toPx() }
            val marginPx = with(density) { 6.dp.toPx() }
            var ringR = minDim * 0.30f
            var dotD = with(density) { 56.dp.toPx() }
            repeat(3) {
                val share = (2 * PI.toFloat() * ringR / n) * 0.68f
                dotD = share.coerceIn(
                    with(density) { 32.dp.toPx() },
                    with(density) { 64.dp.toPx() },
                )
                val need = ringR + dotD / 2f + gapPx + labelBoxPx / 2f + marginPx
                val avail = minDim / 2f
                if (need > avail) ringR -= (need - avail)
            }
            ringR = ringR.coerceAtLeast(with(density) { 40.dp.toPx() })
            val trackWidth = dotD * 1.12f
            val labelR = ringR + dotD / 2f + gapPx
            val scoreSp = (labelBoxPx * 0.42f / density.density).coerceIn(18f, 60f)

            // Clamp helper so labels can never leave the screen.
            fun clampX(x: Float) = x.coerceIn(labelBoxPx / 2f + 2f, wPx - labelBoxPx / 2f - 2f)
            fun clampY(y: Float) = y.coerceIn(labelBoxPx / 2f + 2f, hPx - labelBoxPx / 2f - 2f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.id, game.step, game.allowNegative) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                val seat = nearestSeat(offset, Offset(cx, cy), g.players.size)
                                val id = g.players[seat].id
                                activeId = id
                                dragBase = g.currentScore(id)
                                lastAngle = atan2(
                                    (offset.y - cy).toDouble(),
                                    (offset.x - cx).toDouble()
                                ).toFloat()
                                accRadians = 0f
                                pending = 0
                            },
                            onDragEnd = { commitGesture() },
                            onDragCancel = { resetGesture() },
                            onDrag = { change, _ ->
                                change.consume()
                                val g = latestGame
                                if (activeId == null) return@detectDragGestures
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
                                    p = p.coerceAtLeast(-dragBase)
                                }
                                if (p != pending) {
                                    pending = p
                                    buzz(ctx, 6)
                                }
                            }
                        )
                    }
            ) {
                // Track ring + progress arc, drawn on a canvas bounded to the
                // ring so every frame only repaints a small area.
                RingDial(
                    cxPx = cx,
                    cyPx = cy,
                    ringRPx = ringR,
                    trackPx = trackWidth,
                    activeColor = activePlayer?.let { Color(it.color) },
                    sweepFraction = if (pending == 0) 0f else (pending.toFloat() / turn.toFloat()).coerceIn(-1f, 1f),
                )

                game.players.forEachIndexed { i, player ->
                    val a = seatAngle(i, n)
                    SeatDot(
                        color = Color(player.color),
                        sizeDp = dp(dotD),
                        dimmed = activeId != null && activeId != player.id,
                        offsetPx = IntOffset(
                            x = (cx + cos(a).toFloat() * ringR - dotD / 2f).toInt(),
                            y = (cy + sin(a).toFloat() * ringR - dotD / 2f).toInt(),
                        ),
                        onTap = {
                            buzz(ctx, 12)
                            viewModel.addScore(player.id, game.step)
                        },
                    )
                }

                game.players.forEachIndexed { i, player ->
                    val a = seatAngle(i, n)
                    val lx = clampX(cx + cos(a).toFloat() * labelR)
                    val ly = clampY(cy + sin(a).toFloat() * labelR)
                    val isActive = activeId == player.id
                    SeatLabel(
                        player = player,
                        score = game.currentScore(player.id),
                        scoreSp = scoreSp,
                        boxDp = dp(labelBoxPx),
                        dimmed = activeId != null && !isActive,
                        pendingDelta = if (isActive) pending else 0,
                        lastDelta = if (game.keepLastVisible) {
                            game.entries.lastOrNull { it.playerId == player.id }?.delta
                        } else null,
                        offsetPx = IntOffset(
                            x = (lx - labelBoxPx / 2f).toInt(),
                            y = (ly - labelBoxPx / 2f).toInt(),
                        ),
                        onTap = {
                            buzz(ctx, 10)
                            viewModel.rotatePlayer(player.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Track ring with the in-progress swipe arc. Bounded to the ring diameter so
 * drag frames repaint a small canvas instead of the whole screen.
 */
@Composable
private fun RingDial(
    cxPx: Float,
    cyPx: Float,
    ringRPx: Float,
    trackPx: Float,
    activeColor: Color?,
    sweepFraction: Float,
) {
    val density = LocalDensity.current
    val diameterPx = ringRPx * 2f + trackPx + with(density) { 4.dp.toPx() }
    Canvas(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (cxPx - diameterPx / 2f).toInt(),
                    y = (cyPx - diameterPx / 2f).toInt(),
                )
            }
            .size(with(density) { diameterPx.toDp() })
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Color(0xFF262626),
            radius = ringRPx,
            center = center,
            style = Stroke(width = trackPx),
        )
        if (activeColor != null && sweepFraction != 0f) {
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = (sweepFraction * 360f).coerceIn(-360f, 360f),
                useCenter = false,
                topLeft = Offset(center.x - ringRPx, center.y - ringRPx),
                size = Size(ringRPx * 2f, ringRPx * 2f),
                style = Stroke(width = trackPx),
            )
        }
    }
}

/** One player dot on the ring. Split out so untouched dots skip recomposition. */
@Composable
private fun SeatDot(
    color: Color,
    sizeDp: Dp,
    dimmed: Boolean,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(sizeDp)
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
    )
}

/**
 * Score label outside the ring. Square box (rotation-proof bounds) holding
 * name + score; tap rotates it 90°. Split out so only the active player's
 * label recomposes while a swipe is in progress.
 */
@Composable
private fun SeatLabel(
    player: Player,
    score: Int,
    scoreSp: Float,
    boxDp: Dp,
    dimmed: Boolean,
    pendingDelta: Int,
    lastDelta: Int?,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    val color = Color(player.color)
    val digits = abs(score).toString().length
    val size = (scoreSp - (digits - 1).coerceAtLeast(0) * 2.5f).coerceAtLeast(14f)
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(boxDp)
            .alpha(if (dimmed) 0.4f else 1f)
            .rotate(RotationUtils.degrees(player.rotation))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = player.name,
                fontSize = (size * 0.26f).coerceAtLeast(10f).sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$score",
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            when {
                pendingDelta != 0 -> Text(
                    text = if (pendingDelta > 0) "+$pendingDelta" else "$pendingDelta",
                    fontSize = (size * 0.42f).coerceAtLeast(12f).sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                lastDelta != null -> Text(
                    text = if (lastDelta >= 0) "last +$lastDelta" else "last $lastDelta",
                    fontSize = (size * 0.24f).coerceAtLeast(9f).sp,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}
