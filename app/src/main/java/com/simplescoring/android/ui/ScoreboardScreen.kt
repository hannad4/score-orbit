package com.simplescoring.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/** Find nearest anchor (non-side) player index to [i]. */
private fun findNearestAnchor(i: Int, n: Int, sideThreshold: Float, positions: Array<Offset?>): Int {
    for (offset in 1..n / 2) {
        val cw = (i + offset) % n
        val ccw = (i - offset + n) % n
        if (positions[cw] != null) return cw
        if (positions[ccw] != null) return ccw
    }
    return -1
}

/**
 * Distance from [center] along [dir] to the inside of the rect
 * [0, w]x[0, h] shrunk by [margin]. Used to push score labels out to the
 * screen edges like the iOS tabletop layout.
 */
private fun rayToEdge(center: Offset, dir: Offset, w: Float, h: Float, margin: Float): Float {
    var t = Float.MAX_VALUE
    if (dir.x > 1e-6f) t = min(t, (w - margin - center.x) / dir.x)
    else if (dir.x < -1e-6f) t = min(t, (margin - center.x) / dir.x)
    if (dir.y > 1e-6f) t = min(t, (h - margin - center.y) / dir.y)
    else if (dir.y < -1e-6f) t = min(t, (margin - center.y) / dir.y)
    return if (t.isFinite()) t.coerceAtLeast(0f) else 0f
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
    // Last committed delta, flashed in the middle of the ring (iOS behavior).
    var lastCommit by remember(game.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    val turn = turnValue(game.step)
    val activeIndex = game.players.indexOfFirst { it.id == activeId }
    val activePlayer = activeIndex.takeIf { it >= 0 }?.let { game.players[it] }

    fun resetGesture() {
        activeId = null
        pending = 0
        accRadians = 0f
        lastAngle = Float.NaN
    }

    fun commitGesture() {
        // NOTE: this is called from inside pointerInput, whose block is not
        // restarted mid-gesture. It must only read State (current at call
        // time), never plain vals captured from an old composition.
        val id = activeId
        val delta = pending
        val keepLast = latestGame.keepLastVisible
        val color = latestGame.players.firstOrNull { it.id == id }?.color
        resetGesture()
        if (id != null && delta != 0) {
            viewModel.addScore(id, delta)
            if (keepLast && color != null) lastCommit = color to delta
        }
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
            val center = Offset(cx, cy)
            val n = game.players.size
            val minDim = min(wPx, hPx)
            fun dp(px: Float): Dp = with(density) { px.toDp() }

            // Ring sized like iOS (~0.30 of the narrow side); dots sit on it.
            val ringR = minDim * 0.30f
            val share = (2 * PI.toFloat() * ringR / n) * 0.68f
            val dotD = share.coerceIn(
                with(density) { 34.dp.toPx() },
                with(density) { 64.dp.toPx() },
            )
            val trackWidth = (dotD * 0.65f).coerceIn(
                with(density) { 12.dp.toPx() },
                with(density) { 30.dp.toPx() },
            )

            // Score boxes: rotation-proof squares, sized by player count.
            val labelBoxPx = when {
                n <= 3 -> with(density) { 140.dp.toPx() }
                n <= 4 -> with(density) { 124.dp.toPx() }
                n <= 6 -> with(density) { 100.dp.toPx() }
                n <= 9 -> with(density) { 84.dp.toPx() }
                else -> with(density) { 72.dp.toPx() }
            }
            val scoreSp = (labelBoxPx * 0.44f / density.density).coerceIn(20f, 64f)
            val edgeMarginPx = with(density) { 8.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.id, game.step) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                val seat = nearestSeat(offset, center, g.players.size)
                                activeId = g.players[seat].id
                                lastCommit = null
                                lastAngle = atan2(
                                    (offset.y - cy).toDouble(),
                                    (offset.x - cx).toDouble()
                                ).toFloat()
                                accRadians = 0f
                                pending = 0
                            },
                            onDragEnd = { commitGesture() },
                            // A cancelled gesture (incoming call, etc.) still
                            // keeps completed points instead of dropping them.
                            onDragCancel = { commitGesture() },
                            onDrag = { change, _ ->
                                change.consume()
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
                                val p = (accRadians / (2f * PI.toFloat()) * turn).roundToInt()
                                if (p != pending) {
                                    pending = p
                                    buzz(ctx, 6)
                                }
                            }
                        )
                    }
            ) {
                // Track ring + swipe arc (bounded canvas: cheap frames).
                RingDial(
                    cxPx = cx,
                    cyPx = cy,
                    ringRPx = ringR,
                    trackPx = trackWidth,
                    activeColor = activePlayer?.let { Color(it.color) },
                    // Arc trails from the player's dot along the drag.
                    arcStartDeg = activeIndex.takeIf { it >= 0 }
                        ?.let { (seatAngle(it, n) * 180.0 / PI).toFloat() } ?: -90f,
                    arcSweepDeg = (accRadians * 180f / PI.toFloat()).coerceIn(-360f, 360f),
                )

                // Center flash of the last committed delta (iOS "keep last visible").
                val flash = lastCommit
                if (activeId == null && flash != null) {
                    val (colorInt, delta) = flash
                    Text(
                        text = if (delta > 0) "+$delta" else "$delta",
                        fontSize = (scoreSp * 1.1f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(colorInt),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

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

                // Scores pushed out to the screen edges along each seat ray.
                // Side players (near 90°/270°) stack vertically near anchors
                // instead of rotating and overlapping the central UI.
                val sideThreshold = 0.7f // |cos(angle)| below this = side player

                // Pre-compute label positions for anchor players.
                val labelPositions = Array(n) { null as Offset? }
                for (i in 0 until n) {
                    val a = seatAngle(i, n)
                    val dirX = cos(a).toFloat()
                    val dirY = sin(a).toFloat()
                    if (abs(dirX) >= sideThreshold) {
                        val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                        val dist = min(1.85f * ringR, maxDist)
                            .coerceAtLeast(ringR + dotD / 2f + with(density) { 8.dp.toPx() })
                        labelPositions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                    }
                }

                // For each side player, find nearest anchor and stack vertically.
                val stackOffset = labelBoxPx + with(density) { 8.dp.toPx() }
                for (i in 0 until n) {
                    val a = seatAngle(i, n)
                    val dirX = cos(a).toFloat()
                    if (abs(dirX) >= sideThreshold) continue // anchor, already placed

                    // Find nearest anchor.
                    var anchorIdx = -1
                    var anchorDist = n + 1
                    for (offset in 1..n / 2) {
                        val cw = (i + offset) % n
                        val ccw = (i - offset + n) % n
                        if (labelPositions[cw] != null && offset < anchorDist) {
                            anchorIdx = cw
                            anchorDist = offset
                        }
                        if (labelPositions[ccw] != null && offset < anchorDist) {
                            anchorIdx = ccw
                            anchorDist = offset
                        }
                    }

                    if (anchorIdx < 0) {
                        // No anchor found (all players are side players); fall back to edge.
                        val dirY = sin(a).toFloat()
                        val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                        val dist = min(1.85f * ringR, maxDist)
                            .coerceAtLeast(ringR + dotD / 2f + with(density) { 8.dp.toPx() })
                        labelPositions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                        continue
                    }

                    val anchorPos = labelPositions[anchorIdx]!!
                    val anchorAngle = seatAngle(anchorIdx, n)

                    // Determine above/below based on angular position.
                    var angleDiff = a - anchorAngle
                    while (angleDiff > PI) angleDiff -= 2 * PI
                    while (angleDiff < -PI) angleDiff += 2 * PI
                    val above = angleDiff < 0

                    // Count how many side players are already stacked here.
                    var stackCount = 0
                    for (j in 0 until n) {
                        if (j == i) continue
                        val jAngle = seatAngle(j, n)
                        val jDirX = cos(jAngle).toFloat()
                        if (abs(jDirX) >= sideThreshold) continue
                        val jAnchor = findNearestAnchor(j, n, sideThreshold, labelPositions)
                        if (jAnchor == anchorIdx) {
                            var jDiff = jAngle - anchorAngle
                            while (jDiff > PI) jDiff -= 2 * PI
                            while (jDiff < -PI) jDiff += 2 * PI
                            val jAbove = jDiff < 0
                            if (jAbove == above) stackCount++
                        }
                    }

                    // Offset by stack count to avoid overlap.
                    val totalOffset = stackOffset * (stackCount + 1)
                    labelPositions[i] = Offset(
                        anchorPos.x,
                        if (above) anchorPos.y - totalOffset else anchorPos.y + totalOffset
                    )
                }

                // Render all labels.
                game.players.forEachIndexed { i, player ->
                    val pos = labelPositions[i]!!
                    val isActive = activeId == player.id
                    val showingPending = isActive && pending != 0
                    SeatScore(
                        player = player,
                        // While swiping, the score shows the live pending delta.
                        text = when {
                            showingPending && pending > 0 -> "+$pending"
                            showingPending -> "$pending"
                            else -> "${game.currentScore(player.id)}"
                        },
                        scoreSp = scoreSp,
                        boxDp = dp(labelBoxPx),
                        dimmed = activeId != null && !isActive,
                        offsetPx = IntOffset(
                            x = (pos.x - labelBoxPx / 2f).toInt(),
                            y = (pos.y - labelBoxPx / 2f).toInt(),
                        ),
                        onTap = {
                            buzz(ctx, 10)
                            viewModel.rotatePlayer(player.id)
                        },
                    )
                }
            }
        }

        // Undo/redo row like the score history page.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            PillButton(
                label = "Undo",
                icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp)) },
                enabled = viewModel.undoStack.isNotEmpty(),
                onClick = { viewModel.undo() },
            )
            PillButton(
                label = "Redo",
                icon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp)) },
                enabled = viewModel.redoStack.isNotEmpty(),
                onClick = { viewModel.redo() },
            )
        }
    }
}

@Composable
private fun PillButton(label: String, icon: @Composable () -> Unit, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color(0xFF0A84FF),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            disabledContentColor = Color.White.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
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
    arcStartDeg: Float,
    arcSweepDeg: Float,
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
        if (activeColor != null && arcSweepDeg != 0f) {
            drawArc(
                color = activeColor,
                startAngle = arcStartDeg,
                sweepAngle = arcSweepDeg,
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
 * Score label near the screen edge. Square box (rotation-proof bounds);
 * tap rotates it 90° to face its player. Split out so only the active
 * player's label recomposes while a swipe is in progress.
 */
@Composable
private fun SeatScore(
    player: Player,
    text: String,
    scoreSp: Float,
    boxDp: Dp,
    dimmed: Boolean,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    val color = Color(player.color)
    val digits = text.filter { it.isDigit() }.length
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
                fontSize = (size * 0.24f).coerceAtLeast(10f).sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = text,
                fontSize = size.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
