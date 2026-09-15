package com.scoreorbit.android

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.scoreorbit.android.viewmodel.ScoreViewModel
import com.scoreorbit.android.viewmodel.ThemeMode
import com.scoreorbit.android.ui.ScoreOrbitApp
import com.scoreorbit.android.ui.theme.ScoreOrbitTheme

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
            ScoreOrbitTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScoreOrbitApp(viewModel = viewModel)
                }
            }
        }
    }
}
