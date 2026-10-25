package com.scoreorbit.android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import com.scoreorbit.android.model.MAX_TIMER_SEC
import com.scoreorbit.android.model.MIN_TIMER_SEC
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


/**
 * Settings row showing the default timer length; tapping it opens the
 * standard Material clock picker, read as minutes:seconds.
 */
@Composable
internal fun TimerDurationRow(seconds: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { open = true },
        headlineContent = { Text("Default Duration") },
        supportingContent = { Text("What the timer starts at, and what Reset goes back to") },
        leadingContent = {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    if (open) {
        TimerDurationDialog(
            seconds = seconds,
            onDismiss = { open = false },
            onConfirm = {
                onChange(it)
                open = false
            },
        )
    }
}


private val QuickPicks = listOf(30 to "30s", 60 to "1m", 120 to "2m", 300 to "5m")

private val WheelRowHeight = 56.dp

@Composable
private fun TimerDurationDialog(
    seconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(seconds / 60) }
    var secs by remember { mutableIntStateOf(seconds % 60) }
    val total = (minutes * 60 + secs).coerceIn(MIN_TIMER_SEC, MAX_TIMER_SEC)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Duration") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Minutes only widens to fit a third digit once it needs one.
                    val minWidth by animateDpAsState(if (minutes >= 99) 112.dp else 88.dp)
                    DurationWheel("Minutes", minutes, 999, minWidth) { minutes = it }
                    Spacer(modifier = Modifier.width(16.dp))
                    DurationWheel("Seconds", secs, 59, 88.dp) { secs = it }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QuickPicks.forEach { (t, label) ->
                        Surface(
                            onClick = {
                                minutes = t / 60
                                secs = t % 60
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Snapping number wheel: the middle row is the value, the rows above and
 * below fade and shrink away from it so the column reads as something to
 * scroll rather than a box to type in. Follows [value] when it changes from
 * outside (the preset chips) and reports the centered row once it settles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DurationWheel(
    label: String,
    value: Int,
    max: Int,
    width: androidx.compose.ui.unit.Dp,
    onChange: (Int) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val rowPx = with(LocalDensity.current) { WheelRowHeight.toPx() }
    val change by rememberUpdatedState(onChange)

    // Item offsets are measured from the end of the top padding, so the row
    // at offset zero is the middle one.
    val centered by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index ?: 0
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centered }
            .collect { (scrolling, c) -> if (!scrolling) change(c) }
    }
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && value != centered) state.animateScrollToItem(value)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(width).height(WheelRowHeight * 3)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(20.dp),
                    ),
            )
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(vertical = WheelRowHeight),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(max + 1) { n ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WheelRowHeight)
                            .graphicsLayer {
                                val item = state.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == n }
                                val away = if (item == null) 1f
                                else (abs(item.offset) / rowPx).coerceAtMost(1f)
                                alpha = 1f - 0.8f * away
                                scaleX = 1f - 0.18f * away
                                scaleY = scaleX
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (n < 100) "%02d".format(n) else "$n",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
