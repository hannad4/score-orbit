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
}
