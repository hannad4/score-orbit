package com.scoreorbit.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.scoreorbit.android.viewmodel.ScoreViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenCall
import org.mockito.invocation.InvocationOnMock

/**
 * JVM tests for the score view model's timer default plumbing. The model at
 * these code paths only touches SharedPreferences and org.json, so both are
 * faked with mocks and a real org.json — no device needed.
 */
class ScoreViewModelTest {

    private lateinit var store: HashMap<String, String?>

    private fun makeViewModel(): ScoreViewModel {
        val app = mock(Application::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenCall(app.getSharedPreferences("score_orbit", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenCall(prefs.edit()).thenReturn(editor)
        whenCall(prefs.getString(any(), any())).thenAnswer { inv: InvocationOnMock ->
            store[inv.getArgument(0)] ?: inv.getArgument<String?>(1)
        }
        whenCall(editor.putString(any(), any())).thenAnswer { inv: InvocationOnMock ->
            store[inv.getArgument(0)] = inv.getArgument(1)
            editor
        }
        whenCall(editor.clear()).thenReturn(editor)
        whenCall(editor.apply()).thenAnswer { store.apply {} }
        return ScoreViewModel(app)
    }

    @Before
    fun clearStore() {
        store = HashMap()
    }

    @Test
    fun freshInstall_defaultsToTwoMinuteTimer() {
        val viewModel = makeViewModel()
        assertEquals(120_000L, viewModel.timerMs.value)
        assertEquals(120_000L, viewModel.timerSetMs.value)
        assertEquals(120, viewModel.currentGame.value!!.timerStartSec)
    }

    @Test
    fun changingDefaultDuration_updatesBoardTimerImmediately() {
        val viewModel = makeViewModel()

        viewModel.setTimerStartSec(300)

        // The board's live pill and the armed duration both follow the new
        // default right away, and the game record carries it for restarts.
        assertEquals(300_000L, viewModel.timerMs.value)
        assertEquals(300_000L, viewModel.timerSetMs.value)
        assertFalse(viewModel.timerRunning.value)
        assertEquals(300, viewModel.currentGame.value!!.timerStartSec)
    }

    @Test
    fun changingDefaultDuration_survivesRestart() {
        val viewModel = makeViewModel()
        viewModel.setTimerStartSec(300)

        // A brand-new view model over the same storage picks the value up.
        val restored = makeViewModel()
        assertEquals(300_000L, restored.timerMs.value)
        assertEquals(300_000L, restored.timerSetMs.value)
        assertEquals(300, restored.currentGame.value!!.timerStartSec)
    }

    @Test
    fun changedDefaultSlipsInSavedGame(): Unit {
        makeViewModel().setTimerStartSec(300)
        val saved = checkNotNull(store["saved_game"])
        assertEquals(300, JSONObject(saved).getInt("timerStartSec"))
    }

    @Test
    fun timerAdjust_stepsBetweenFiveSecondQuantums() {
        val viewModel = makeViewModel()
        viewModel.setTimerStartSec(120) // armed at 2:00

        // From a clean value, + takes a full step.
        viewModel.timerAdjust(5)
        assertEquals(125_000L, viewModel.timerMs.value)
        assertEquals(125_000L, viewModel.timerSetMs.value)

        // And - returns to the previous clean value.
        viewModel.timerAdjust(-5)
        assertEquals(120_000L, viewModel.timerMs.value)

        // Minus floors to the multiple of 5 below, never above it.
        viewModel.setTimerStartSec(118)
        viewModel.timerAdjust(-5)
        assertEquals(115_000L, viewModel.timerMs.value)

        // Plus rounds to the multiple of 5 above, at least one full step.
        viewModel.setTimerStartSec(120)
        viewModel.timerAdjust(-5) // 115
        viewModel.timerAdjust(-5) // 110
        viewModel.timerAdjust(-5) // 105
        viewModel.timerAdjust(-5) // 100
        viewModel.timerAdjust(-5) // 95
        viewModel.timerAdjust(-5) // 90
        viewModel.timerAdjust(-5) // 85
        viewModel.timerAdjust(-5) // 80
        viewModel.timerAdjust(-5) // 75
        viewModel.timerAdjust(-5) // 70
        viewModel.timerAdjust(-5) // 65
        viewModel.timerAdjust(-5) // 60
        viewModel.timerAdjust(-5) // 55
        viewModel.timerAdjust(-5) // 50
        viewModel.timerAdjust(-5) // 45
        viewModel.timerAdjust(-5) // 40
        viewModel.timerAdjust(-5) // 35
        viewModel.timerAdjust(-5) // 30
        viewModel.timerAdjust(-5) // 25
        viewModel.timerAdjust(-5) // 20
        viewModel.timerAdjust(-5) // 15: the floor
        viewModel.timerAdjust(-5)
        assertEquals(15_000L, viewModel.timerMs.value) // the 15-second floor stops it
        // And plus walks back up from the floor in full steps.
        viewModel.timerAdjust(5)
        assertEquals(20_000L, viewModel.timerMs.value)
    }
}
