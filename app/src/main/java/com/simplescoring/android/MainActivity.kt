package com.simplescoring.android

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.simplescoring.android.viewmodel.ScoreViewModel
import com.simplescoring.android.viewmodel.ThemeMode
import com.simplescoring.android.ui.ScoreAnythingApp
import com.simplescoring.android.ui.theme.ScoreAnythingTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScoreViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ScoreViewModel(application) as T
                }
            }
        )[ScoreViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            ScoreAnythingTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScoreAnythingApp(viewModel = viewModel)
                }
            }
        }
    }
}
