package com.scoreorbit.android.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreorbit.android.model.Game
import com.scoreorbit.android.model.Player
import com.scoreorbit.android.util.RotationUtils
import com.scoreorbit.android.viewmodel.AppScreen
import com.scoreorbit.android.viewmodel.ScoreViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Seat angle in radians for [index] of [total] (clockwise, y-down). Seat 0
 * sits at the top by default. Only a [total] that's a multiple of 4 always
 * lands a seat exactly at due east/west (90°/270°) — the tightest, most
 * ambiguous spot against the screen edge — so only those counts rotate the
 * whole ring back by half a seat's spacing (-π/total), landing seat 0
 * top-left instead of dead top. Other counts (even or odd) never hit that
 * exact side extremum, so they keep the plain top start.
 */
private fun seatAngle(index: Int, total: Int): Double {
    val origin = if (total >= 4 && total % 4 == 0) -PI / 2 - PI / total else -PI / 2
    return origin + index * 2.0 * PI / total
}

/**
 * Dial angles snapped to an even spread while staying as close as possible
 * to each score: every dot starts parked on the ray from the dial center
 * toward its own score label, then the whole set snaps to evenly spaced
 * slots — trying every rotation and keeping the least total movement — so
 * crowded labels share the compromise instead of piling dots on each other.
 */
private fun computeDotAngles(
    labelPositions: Array<Offset?>,
    cx: Float,
    cy: Float,
    ringR: Float,
    dotD: Float,
): DoubleArray {
    val n = labelPositions.size
    // Candidate: ray from center through each label's final position.
    val cand = DoubleArray(n) { i ->
        val p = labelPositions[i]!!
        var a = atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
        if (a < 0) a += 2 * PI
        a
    }
    if (n < 2) return cand
    // Minimum center-to-center separation (chord) so dots keep a small gap.
    val minSep = 2.0 * asin(min(1.0, (dotD * 1.1 / 2.0) / ringR.toDouble()))
    // Dots are sized from their angular share, so a full even spread always
    // fits; if it somehow couldn't, fall back to even spacing to keep the
    // dial usable.
    if (minSep * n > 2 * PI) {
        return DoubleArray(n) { -PI / 2 + it * 2 * PI / n }
    }
    // Unwrap candidates in circular order, duplicated with +2PI so every
    // rotation is a contiguous window.
    val order = (0 until n).sortedBy { cand[it] }
    val ext = DoubleArray(2 * n)
    for (k in 0 until 2 * n) {
        var target = cand[order[k % n]]
        if (k > 0) {
            while (target < ext[k - 1]) target += 2 * PI
        }
        ext[k] = target
    }
    // Even slots; try every rotation, keep the least total movement.
    val step = 2 * PI / n
    var bestStart = 0
    var bestBase = 0.0
    var bestCost = Double.MAX_VALUE
    for (s in 0 until n) {
        var base = 0.0
        for (k in 0 until n) base += ext[s + k] - k * step
        base /= n
        var cost = 0.0
        for (k in 0 until n) cost += abs(ext[s + k] - (base + k * step))
        if (cost < bestCost) {
            bestCost = cost
            bestStart = s
            bestBase = base
        }
    }
    val result = DoubleArray(n)
    for (k in 0 until n) {
        var a = (bestBase + k * step) % (2 * PI)
        if (a < 0) a += 2 * PI
        result[order[(bestStart + k) % n]] = a
    }
    return result
}

private fun nearestSeat(point: Offset, center: Offset, dotAngles: DoubleArray): Int {
    val a = atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    var best = 0
    var bestD = Double.MAX_VALUE
    for (i in dotAngles.indices) {
        var d = abs(a - dotAngles[i]) % (2 * PI)
        if (d > PI) d = 2 * PI - d
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

/**
 * Find nearest anchor (non-side) seat index to [i], searching both
 * directions. Only considers anchors in the same top/bottom half as [i]
 * ([topHalf]) so a bottom-dwelling seat never gets pulled up to stack near a
 * top anchor (or vice versa) just because it's the closest one by index.
 */
private fun findNearestAnchor(i: Int, n: Int, isSide: BooleanArray, topHalf: BooleanArray): Int {
    for (offset in 1..n / 2) {
        val cw = (i + offset) % n
        val ccw = (i - offset + n) % n
        if (!isSide[cw] && topHalf[cw] == topHalf[i]) return cw
        if (!isSide[ccw] && topHalf[ccw] == topHalf[i]) return ccw
    }
    return -1
}

/**
 * Distance from [center] along [dir] to the inside of the rect
 * [0, w]x[0, h] shrunk by [margin]. Used to push score labels out to the
 * screen edges for the tabletop layout.
 */
private fun rayToEdge(center: Offset, dir: Offset, w: Float, h: Float, margin: Float): Float {
    var t = Float.MAX_VALUE
    if (dir.x > 1e-6f) t = min(t, (w - margin - center.x) / dir.x)
    else if (dir.x < -1e-6f) t = min(t, (margin - center.x) / dir.x)
    if (dir.y > 1e-6f) t = min(t, (h - margin - center.y) / dir.y)
    else if (dir.y < -1e-6f) t = min(t, (margin - center.y) / dir.y)
    return if (t.isFinite()) t.coerceAtLeast(0f) else 0f
}

/**
 * Edge-label positions for every seat. 1-3 players use the seatAngle-driven
 * layout (anchors at their natural rotary start point; side players stack
 * off a same-half anchor, or off their own ring-edge spot when there isn't
 * one). 4+ players use the player-order band grid. Pure layout math: it only
 * depends on the board geometry, never on gesture state.
 */
private fun computeLabelPositions(
    n: Int,
    wPx: Float,
    hPx: Float,
    cx: Float,
    cy: Float,
    ringR: Float,
    dotD: Float,
    labelBoxPx: Float,
    edgeMarginPx: Float,
    density: Density,
): Array<Offset?> {
    val center = Offset(cx, cy)
    val positions = arrayOfNulls<Offset>(n)
    val stackOffset = labelBoxPx + with(density) { 12.dp.toPx() }
    // Minimum breathing room between a label and the ring/dots so
    // crowded boards never read as clipped into the dial.
    val labelClearPx = with(density) { 28.dp.toPx() }
    if (n >= 4) {
        // Fixed 3-column band grid in player order: the first
        // half of the players fills the top band left to right,
        // top to bottom (P1 top-left, P2 top-middle, ...), the
        // rest fill the bottom band the same way. Short rows are
        // centered so columns stay aligned across rows. Wide
        // cells let scores fill the free space instead of
        // squeezing into per-seat columns.
        val nearD = ringR + dotD / 2f + labelClearPx
        val rowPitch = labelBoxPx + with(density) { 12.dp.toPx() }
        val xLo = edgeMarginPx + labelBoxPx / 2f
        val xHi = wPx - edgeMarginPx - labelBoxPx / 2f
        val anchors = floatArrayOf(xLo, (xLo + xHi) / 2f, xHi)
        val topCount = (n + 1) / 2
        for ((band, sign) in listOf(
            (0 until topCount).toList() to -1f,
            (topCount until n).toList() to 1f,
        )) {
            // Fit each band's own rows: a band with fewer rows
            // keeps full clearance instead of inheriting the
            // compression of a more crowded band.
            val rows = band.chunked(3)
            val vRoom = (min(cy, hPx - cy) - edgeMarginPx - labelBoxPx / 2f)
                .coerceAtLeast(0f)
            val vWant = nearD + (rows.size - 1) * rowPitch + labelBoxPx / 2f
            val vFit = if (vWant > vRoom && vWant > 0f) {
                (vRoom / vWant).coerceIn(0.2f, 1f)
            } else 1f
            rows.forEachIndexed { r, row ->
                val xs = when (row.size) {
                    3 -> anchors
                    2 -> floatArrayOf(anchors[0], anchors[2])
                    else -> floatArrayOf(anchors[1])
                }
                // Top band fills outward (first chunk farthest)
                // so P1 lands top-left; bottom band fills
                // downward in the same reading order.
                val rr = if (sign < 0f) rows.size - 1 - r else r
                row.forEachIndexed { c, i ->
                    val d = (nearD + rr * rowPitch + labelBoxPx / 2f) * vFit
                    positions[i] = Offset(xs[c], cy + sign * d)
                }
            }
        }
    } else {
        // Anchors (valid, non-side positions) stay put on their
        // natural rotary start point. Side players — those whose
        // horizontal component |cos(theta)| clears the threshold,
        // i.e. living on the left/right of the wheel — stack
        // above/below their nearest anchor instead of overlapping
        // the central UI.
        val sideThreshold = cos(40 * PI / 180).toFloat() // |cos(angle)| above this = side player
        val isSide = BooleanArray(n) { abs(cos(seatAngle(it, n)).toFloat()) >= sideThreshold }
        // Which half of the wheel a seat's own position falls in
        // — a side player stacks within its own half, never the
        // other.
        val topHalf = BooleanArray(n) { sin(seatAngle(it, n)) <= 0.0 }

        // Place anchors (non-side players) at their natural positions.
        for (i in 0 until n) {
            if (isSide[i]) continue
            val a = seatAngle(i, n)
            val dirX = cos(a).toFloat()
            val dirY = sin(a).toFloat()
            val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
            val dist = min(1.85f * ringR, maxDist)
                .coerceAtLeast(ringR + dotD / 2f + labelClearPx)
            positions[i] = Offset(cx + dirX * dist, cy + dirY * dist)
        }

        // For each side player, resolve the point it stacks from and
        // whether it belongs above or below that point. This only
        // looks at true anchors (isSide == false) in the same half
        // of the wheel, never at other side players or the opposite
        // half, so the result doesn't depend on processing order and
        // a bottom-half player never gets thrown to the top.
        val anchorPosOf = arrayOfNulls<Offset>(n)
        val aboveOf = BooleanArray(n)
        val diffOf = DoubleArray(n)
        for (i in 0 until n) {
            if (!isSide[i]) continue
            val a = seatAngle(i, n)
            val dirX = cos(a).toFloat()
            val dirY = sin(a).toFloat()
            val anchorIdx = findNearestAnchor(i, n, isSide, topHalf)
            if (anchorIdx < 0) {
                // No anchor in this half of the wheel; stack out from
                // the seat's own ring-edge position instead of
                // reaching across to an anchor on the other half.
                val maxDist = rayToEdge(center, Offset(dirX, dirY), wPx, hPx, edgeMarginPx + labelBoxPx / 2f)
                val dist = min(1.85f * ringR, maxDist)
                    .coerceAtLeast(ringR + dotD / 2f + labelClearPx)
                anchorPosOf[i] = Offset(cx + dirX * dist, cy + dirY * dist)
                aboveOf[i] = topHalf[i]
                diffOf[i] = 0.0
                continue
            }
            val anchorAngle = seatAngle(anchorIdx, n)
            var angleDiff = a - anchorAngle
            while (angleDiff > PI) angleDiff -= 2 * PI
            while (angleDiff < -PI) angleDiff += 2 * PI
            val onRightSide = dirX > 0
            anchorPosOf[i] = positions[anchorIdx]
            aboveOf[i] = if (onRightSide) angleDiff > 0 else angleDiff < 0
            diffOf[i] = abs(angleDiff)
        }

        // Stack each side player above/below its reference point,
        // ranked by angular closeness so players sharing a reference
        // point + direction land at increasing distances instead of
        // on top of each other — always at least one full step out,
        // so even a lone side player clears the central dial.
        for (i in 0 until n) {
            if (!isSide[i]) continue
            val anchorPos = anchorPosOf[i] ?: continue
            var rank = 1
            for (j in 0 until n) {
                if (j == i || !isSide[j] || anchorPosOf[j] != anchorPos || aboveOf[j] != aboveOf[i]) continue
                if (diffOf[j] < diffOf[i] || (diffOf[j] == diffOf[i] && j < i)) rank++
            }
            val totalOffset = stackOffset * rank
            positions[i] = Offset(
                anchorPos.x,
                if (aboveOf[i]) anchorPos.y - totalOffset else anchorPos.y + totalOffset
            )
        }
    }
    // Final guard for every layout above: no label may leave the
    // screen. Crowded boards overlap slightly instead of
    // clipping cut-off text at the edges.
    for (i in 0 until n) {
        val p = positions[i] ?: continue
        val loX = labelBoxPx / 2f + edgeMarginPx
        val hiX = wPx - labelBoxPx / 2f - edgeMarginPx
        val loY = labelBoxPx / 2f + edgeMarginPx
        val hiY = hPx - labelBoxPx / 2f - edgeMarginPx
        positions[i] = Offset(
            x = if (hiX <= loX) wPx / 2f else p.x.coerceIn(loX, hiX),
            y = if (hiY <= loY) hPx / 2f else p.y.coerceIn(loY, hiY),
        )
    }
    return positions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(game: Game, viewModel: ScoreViewModel) {
    val ctx = LocalContext.current
    var latestGame by remember { mutableStateOf(game) }
    latestGame = game

    var activeId by remember(game.id) { mutableStateOf<String?>(null) }
    var pending by remember(game.id) { mutableIntStateOf(0) }
    var accRadians by remember(game.id) { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    // Last committed action, flashed in the middle of the ring as name +
    // final delta, fading out on its own after ~2s.
    var lastFlash by remember(game.id) { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var flashAlpha by remember(game.id) { mutableFloatStateOf(0f) }
    // Settle fade 0->1 after the rewind: bead fades out while the hidden
    // dots fade back in, so nothing pops.
    var settle by remember(game.id) { mutableFloatStateOf(0f) }
    // Marker grow 0->1 on grab (only when enlargement is on).
    var markerScale by remember(game.id) { mutableFloatStateOf(0f) }
    var markerJob by remember(game.id) { mutableStateOf<Job?>(null) }
    var flashJob by remember(game.id) { mutableStateOf<Job?>(null) }
    // In-flight "spring back" animation that unwinds the dial after release.
    val scope = rememberCoroutineScope()
    var springJob by remember(game.id) { mutableStateOf<Job?>(null) }

    val turn = game.rotationPoints.coerceAtLeast(1)
    // Committed totals are O(ledger) each: compute once per ledger change,
    // not once per player on every drag frame.
    val totals = remember(game.entries, game.players) { game.scoresMap() }
    val activeIndex = game.players.indexOfFirst { it.id == activeId }
    val activePlayer = activeIndex.takeIf { it >= 0 }?.let { game.players[it] }

    fun resetGesture() {
        springJob?.cancel()
        springJob = null
        markerJob?.cancel()
        markerJob = null
        flashJob?.cancel()
        flashJob = null
        lastFlash = null
        flashAlpha = 0f
        settle = 0f
        activeId = null
        pending = 0
        accRadians = 0f
        lastAngle = Float.NaN
    }

    fun showFlash(color: Int, delta: Int, name: String) {
        // NOTE: like commitGesture, this runs from pointer-input and tap
        // handlers that may hold stale compositions: only State reads here.
        // Zero-point taps flash nothing.
        if (delta == 0) return
        flashJob?.cancel()
        lastFlash = Triple(color, delta, name)
        flashAlpha = 1f
        if (!latestGame.keepLastVisible) {
            // Brief flash then fade ("off" = transient display)
            flashJob = scope.launch {
                delay(800)
                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300),
                ) { value, _ -> flashAlpha = value }
                lastFlash = null
                flashJob = null
            }
        }
        // "on" = persistent: flash stays until next scoring event
    }

    fun commitGesture() {
        // NOTE: this is called from inside pointerInput, whose block is not
        // restarted mid-gesture. It must only read State (current at call
        // time), never plain vals captured from an old composition.
        val id = activeId
        val delta = pending
        val g = latestGame
        val player = g.players.firstOrNull { it.id == id }
        if (id != null && delta != 0) {
            viewModel.addScore(id, delta)
            // Totals are committed: drop the live readout immediately so the
            // center switches to the flash instead of showing stale pending
            // through the unwind. activeId stays until the spring settles so
            // the dots/trail keep animating back in place.
            pending = 0
            if (player != null) showFlash(player.color, delta, player.name)
        }
        lastAngle = Float.NaN

        // Like a real rotary dial's return spring: rewind opposite the
        // spin just until the dots sit back on their start coordinates,
        // then snap the (rotation-identical) remainder to zero. Only the
        // visible offset ever travels — whole completed laps already sit
        // at home, so they don't need to spin back.
        springJob?.cancel()
        val twoPi = 2f * PI.toFloat()
        val start = accRadians
        val home = if (start >= 0f) floor(start / twoPi) * twoPi
                   else ceil(start / twoPi) * twoPi
        springJob = scope.launch {
            if (abs(start - home) >= 0.05f) {
                animate(
                    initialValue = start,
                    targetValue = home,
                    // Slow, stately rotary return: settling time scales with
                    // 1/sqrt(stiffness), so ~70 takes roughly 4-5x as long as
                    // StiffnessMedium (1500) to settle.
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 70f,
                    ),
                ) { value, _ -> accRadians = value }
                // NOTE: accRadians deliberately stays at home (rotation-
                // equivalent to zero) through the fade below, so the trail
                // keeps its full final sweep to dissolve from.
            } else {
                // Already (visually) home: nothing to unwind.
                accRadians = 0f
            }
            // One shared dissolve so dots, bead and trail move together:
            // the hidden dots fade back in over the same window that the
            // bead and the sweep color fade out. Runs after every release
            // — even tiny flicks — so nothing ever pops, and nothing ever
            // finishes before anything else.
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 990),
            ) { value, _ -> settle = value }
            accRadians = 0f
            settle = 0f
            markerScale = 0f
            activeId = null
            springJob = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (game.name.isNotBlank()) {
                        Text(
                            game.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.go(AppScreen.ScoreHistory) }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Score history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        IconButton(onClick = { viewModel.go(AppScreen.Leaderboard) }) {
                            Icon(
                                Icons.Default.Leaderboard,
                                contentDescription = "Leaderboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.go(AppScreen.Settings) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            if (min(wPx, hPx) <= 0f || game.players.isEmpty()) return@BoxWithConstraints

            // Bottom strip reserved for system gesture navigation: the whole
            // layout lives above it, and a transparent overlay (below)
            // swallows every touch inside it, so a swipe-up-home never grabs
            // the dial or taps a label by accident.
            val deadZoneFrac = 0.08f
            val layoutH = hPx * (1f - deadZoneFrac)
            val cx = wPx / 2f
            val cy = layoutH / 2f
            val center = Offset(cx, cy)
            val n = game.players.size
            val minDim = min(wPx, hPx)
            fun dp(px: Float): Dp = with(density) { px.toDp() }

            // Ring sized for the narrow side; dots sit on it. Both run 15% slimmed
            // down from raw scale so the dial doesn't crowd the labels.
            // High counts get a slightly smaller dial so the label bands
            // keep generous clearance instead of crowding the ring.
            val ringR = minDim * when {
                n <= 6 -> 0.34f
                n <= 8 -> 0.32f
                else -> 0.30f
            }
            val share = (2 * PI.toFloat() * ringR / n) * 0.68f
            val dotD = (share.coerceIn(
                with(density) { 34.dp.toPx() },
                with(density) { 64.dp.toPx() },
            )) * 0.85f
            // Track exactly matches the dot diameter (derived from the same
            // already-clamped value, never capped separately) so the ring
            // and the colored circles are always the same thickness.
            val trackWidth = dotD

            // Score boxes: rotation-proof squares, sized by player count.
            // Small counts use large boxes; 4+ players use the band grid
            // below, so the box comes straight from the column width: as
            // large as it can be while still fitting three across.
            val edgeMarginPx = with(density) { 8.dp.toPx() }
            val labelBoxPx = when {
                n <= 3 -> with(density) { 140.dp.toPx() }
                n <= 6 -> with(density) { 100.dp.toPx() }
                else -> {
                    // Band grid uses 3 fixed columns: size the box from the
                    // column width so scores fill the free space instead of
                    // squeezing into per-seat columns.
                    val gap = with(density) { 4.dp.toPx() }
                    ((wPx - 2 * edgeMarginPx - 2 * gap) / 3f)
                        .coerceIn(with(density) { 72.dp.toPx() }, with(density) { 100.dp.toPx() })
                }
            }
            val scoreSp = (labelBoxPx * 0.44f / density.density).coerceIn(20f, 64f)

            // Scores pushed out to the screen edges (memoized: pure layout
            // math, independent of gesture state). 1-3 players use the
            // seatAngle-driven layout (anchors at their natural rotary
            // start point; side players stack off a same-half anchor, or
            // off their own ring-edge spot when there isn't one). 4+
            // players use the player-order band grid instead.
            val labelPositions: Array<Offset?> = remember(
                n, wPx, layoutH, ringR, dotD, labelBoxPx, edgeMarginPx, density,
            ) {
                computeLabelPositions(n, wPx, layoutH, cx, cy, ringR, dotD, labelBoxPx, edgeMarginPx, density)
            }
            // Dial angles, one dot parked near its own score then snapped
            // to even spacing (memoized likewise).
            val dotAngles: DoubleArray = remember(labelPositions, cx, cy, ringR, dotD) {
                computeDotAngles(labelPositions, cx, cy, ringR, dotD)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.id, game.rotationPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val g = latestGame
                                if (g.players.isEmpty()) return@detectDragGestures
                                // Grabbing the dial again mid spring-back cancels the
                                // return animation instead of fighting it for control
                                // of accRadians.
                                resetGesture()
                                val seat = nearestSeat(offset, center, dotAngles)
                                activeId = g.players[seat].id
                                // Grow the touch marker in on grab (only when
                                // enlargement is on — otherwise it stays put).
                                if (g.enlargeActiveDot) {
                                    markerJob?.cancel()
                                    markerJob = scope.launch {
                                        animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = 500f,
                                            ),
                                        ) { value, _ -> markerScale = value }
                                    }
                                }
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
                                    if (latestGame.hapticsEnabled) {
                                        buzz(ctx, 18, latestGame.hapticStrength)
                                    }
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
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    activeColor = activePlayer?.let { Color(it.color) },
                    showMarker = game.enlargeActiveDot,
                    settleAlpha = settle,
                    markerScale = markerScale,
                    trailAlpha = 1f - settle,
                    // Arc trails from the player's dot along the drag. Not
                    // clamped to one lap: RingDial itself turns anything
                    // beyond 360° into a stacked, full-circle fade instead of
                    // truncating the visual at the first turn.
                    arcStartDeg = activeIndex.takeIf { it >= 0 }
                        ?.let { (dotAngles[it] * 180.0 / PI).toFloat() } ?: -90f,
                    arcSweepDeg = accRadians * 180f / PI.toFloat(),
                )

                // Center readout: one slot shared by the live spin readout
                // and the post-commit flash, so stale text can never linger.
                // While turning: name + live delta. On release the same
                // name + final delta keeps showing (now fading). Empty
                // otherwise. In particular the flash takes over the moment
                // pending hits zero — including through the unwind, when the
                // gesture is technically still settling.
                val livePlayer = activePlayer
                val flash = lastFlash
                // With "keep last visible" off, nothing static may linger in
                // the center: no touch-down name, no unwind name, no flash.
                // The live readout only ever shows while points accumulate.
                val keepLast = game.keepLastVisible
                val showLive = livePlayer != null && (pending != 0 || (flash == null && keepLast))
                // Readout anchored on the ring center (cx, cy) — not the box
                // center, which sits lower now that the layout reserves the
                // bottom strip for gesture navigation.
                var readoutSize by remember { mutableStateOf(IntSize.Zero) }
                val readoutAnchor = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { readoutSize = it }
                    .offset {
                        IntOffset(
                            x = (cx - readoutSize.width / 2f).roundToInt(),
                            y = (cy - readoutSize.height / 2f).roundToInt(),
                        )
                    }
                if (showLive && livePlayer != null) {
                    val c = Color(livePlayer.color)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = readoutAnchor,
                    ) {
                        Text(
                            text = livePlayer.name,
                            fontSize = (scoreSp * 0.32f).coerceAtLeast(12f).sp,
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (pending != 0) {
                            Text(
                                text = if (pending > 0) "+$pending" else "$pending",
                                fontSize = (scoreSp * 1.1f).sp,
                                fontWeight = FontWeight.Bold,
                                color = c,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else if (flash != null && flashAlpha > 0f) {
                    val (colorInt, delta, name) = flash
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = readoutAnchor
                            .alpha(flashAlpha)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.go(AppScreen.ScoreHistory) },
                            ),
                    ) {
                        Text(
                            text = name,
                            fontSize = (scoreSp * 0.32f).coerceAtLeast(12f).sp,
                            color = Color(colorInt),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (delta > 0) "+$delta" else "$delta",
                            fontSize = (scoreSp * 1.1f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(colorInt),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                game.players.forEachIndexed { i, player ->
                    // While dragging, the whole wheel of dots turns together
                    // with the touch (a real rotary dial's disk), rather than
                    // just the active player's own trail moving in place.
                    // Idle dots hide for the spin and fade back in with the
                    // settle animation instead of popping.
                    val isActiveDot = activeId == null || activeId == player.id
                    val a = if (activeId != null) dotAngles[i] + accRadians else dotAngles[i]
                    SeatDot(
                        color = Color(player.color),
                        sizeDp = dp(dotD),
                        alpha = if (isActiveDot) 1f else settle,
                        offsetPx = IntOffset(
                            x = (cx + cos(a).toFloat() * ringR - dotD / 2f).toInt(),
                            y = (cy + sin(a).toFloat() * ringR - dotD / 2f).toInt(),
                        ),
                        onTap = {
                            if (game.hapticsEnabled) {
                                buzz(ctx, 30, game.hapticStrength)
                            }
                            viewModel.addScore(player.id, game.tapPoints)
                            showFlash(player.color, game.tapPoints, player.name)
                        },
                    )
                }

                // Render all labels (positions already clamped on-screen by
                // computeLabelPositions).
                game.players.forEachIndexed { i, player ->
                    val pos = labelPositions[i]!!
                    val isActive = activeId == player.id
                    SeatScore(
                        player = player,
                        // The edge score always shows the player's committed
                        // total; the live spin delta lives only in the center
                        // readout while swiping, then commits on release.
                        score = totals[player.id] ?: 0,
                        showName = game.showPlayerNames,
                        scoreSp = scoreSp,
                        boxDp = dp(labelBoxPx),
                        dimmed = activeId != null && !isActive,
                        offsetPx = IntOffset(
                            x = (pos.x - labelBoxPx / 2f).toInt(),
                            y = (pos.y - labelBoxPx / 2f).toInt(),
                        ),
                        onTap = {
                            if (game.hapticsEnabled) {
                                buzz(ctx, 20, game.hapticStrength)
                            }
                            viewModel.rotatePlayer(player.id)
                        },
                    )
                }
            }
            // Non-interactable strip along the bottom: transparent, but eats
            // every touch so nothing below the layout — and no
            // system-gesture swipe passing through — can reach the dial, a
            // dot, or a label. Taps are swallowed by the no-op click target,
            // drags by the consuming drag detector (both sit above the board
            // surface, so nothing below ever sees the gesture).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(deadZoneFrac)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> change.consume() }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
            )
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
    trackColor: Color,
    activeColor: Color?,
    showMarker: Boolean,
    settleAlpha: Float,
    markerScale: Float,
    trailAlpha: Float,
    arcStartDeg: Float,
    arcSweepDeg: Float,
) {
    val density = LocalDensity.current
    val diameterPx = ringRPx * 2f + trackPx + with(density) { 4.dp.toPx() }
    // The sweep-gradient shader is rebuilt only when the tip moves ~3°:
    // identical pixels, far fewer shader objects per drag.
    val tipDeg = arcStartDeg + arcSweepDeg
    val clockwise = arcSweepDeg >= 0f
    val tipQ = (tipDeg / 3f).roundToInt()
    val trailBrush = remember(activeColor, tipQ, clockwise, diameterPx) {
        Brush.sweepGradient(
            colors = trailFadeColors(activeColor ?: Color.Transparent, tipQ * 3f, clockwise = clockwise),
            center = Offset(diameterPx / 2f, diameterPx / 2f),
        )
    }
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
            color = trackColor,
            radius = ringRPx,
            center = center,
            style = Stroke(width = trackPx),
        )
        if (activeColor != null) {
            // Slow dissolve for the whole trail (not just the bead): after a
            // multi-turn spin the sweep stays past 360° through the entire
            // rewind, so without this the full colored ring would blink out
            // in a single frame at release instead of easing back to gray.
            if (arcSweepDeg != 0f && trailAlpha > 0f) {
                // Comet trail: full opacity at the touch point, fading to
                // 10% one full turn behind it. Sampled as a sweep gradient
                // fixed in absolute canvas angle (not rotated to the moving
                // tip), so drawArc/drawCircle can just sample it directly.
                // Past one lap the whole ring is covered by the same
                // periodic fade, which is what makes extra laps "stack"
                // instead of erasing the earlier trail.
                if (abs(arcSweepDeg) >= 360f) {
                    drawCircle(
                        brush = trailBrush,
                        radius = ringRPx,
                        center = center,
                        alpha = trailAlpha,
                        style = Stroke(width = trackPx),
                    )
                } else {
                    // Negative sweeps don't rasterize on this canvas path,
                    // so counter-clockwise drags are redrawn as the
                    // equivalent positive sweep back from the tip. The
                    // visible trail and tip position are identical either
                    // way — this just guarantees both directions render.
                    val start = if (arcSweepDeg >= 0f) arcStartDeg else tipDeg
                    // Round cap: the trailing (oldest) end rounds off instead
                    // of a flat cut-off. The tip end is rounded too, but the
                    // touch-marker disk drawn below fully covers it either way.
                    drawArc(
                        brush = trailBrush,
                        startAngle = start,
                        sweepAngle = abs(arcSweepDeg),
                        useCenter = false,
                        topLeft = Offset(center.x - ringRPx, center.y - ringRPx),
                        size = Size(ringRPx * 2f, ringRPx * 2f),
                        alpha = trailAlpha,
                        style = Stroke(width = trackPx, cap = StrokeCap.Round),
                    )
                }
            }

            // Touch-marker disk: grows tiny-to-large on grab, then shrinks
            // back out with the settle animation instead of popping. Gated
            // by the "enlarge active dot" setting — off means a
            // fixed-diameter dial.
            if (!showMarker) return@Canvas
            val beadAlpha = 1f - settleAlpha
            if (beadAlpha <= 0f || markerScale <= 0f) return@Canvas
            val tipRad = tipDeg * PI.toFloat() / 180f
            drawCircle(
                color = activeColor.copy(alpha = beadAlpha),
                radius = trackPx * 0.7f * markerScale,
                center = Offset(
                    center.x + cos(tipRad) * ringRPx,
                    center.y + sin(tipRad) * ringRPx,
                ),
            )
        }
    }
}

/**
 * Sweep-gradient color stops (absolute canvas angle, 0..360, first and last
 * matching for a seamless wrap) giving [color] full opacity at [tipDeg] and
 * fading to 10% opacity one full turn behind it, in the direction opposite
 * travel ([clockwise]).
 */
private fun trailFadeColors(color: Color, tipDeg: Float, clockwise: Boolean): List<Color> {
    val stops = 64
    return List(stops + 1) { i ->
        val absDeg = i / stops.toFloat() * 360f
        val back = if (clockwise) {
            (((tipDeg - absDeg) % 360f) + 360f) % 360f
        } else {
            (((absDeg - tipDeg) % 360f) + 360f) % 360f
        }
        color.copy(alpha = (1f - 0.9f * (back / 360f)).coerceIn(0.1f, 1f))
    }
}

/** One player dot on the ring. Split out so untouched dots skip recomposition. */
@Composable
private fun SeatDot(
    color: Color,
    sizeDp: Dp,
    alpha: Float,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(sizeDp)
            .alpha(alpha)
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
    score: Int,
    showName: Boolean,
    scoreSp: Float,
    boxDp: Dp,
    dimmed: Boolean,
    offsetPx: IntOffset,
    onTap: () -> Unit,
) {
    val color = Color(player.color)
    // Count up/down to the new total instead of jumping to it. At rest the
    // animated value already equals the target, so idle frames are static.
    val shown by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1200),
        label = "scoreCount",
    )
    val text = "$shown"
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
            if (showName) {
                Text(
                    text = player.name,
                    // Name-to-score ratio mirrors the reference layout;
                    // always one weight below the Bold score itself.
                    fontSize = (size * 0.36f).coerceAtLeast(11f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
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
