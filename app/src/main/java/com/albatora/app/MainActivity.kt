package com.albatora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.albatora.app.model.ThemeOption
import com.albatora.app.ui.screens.MainScaffoldScreen
import com.albatora.app.ui.theme.AlbaToraTheme
import com.albatora.app.viewmodel.AlbaToraViewModel
import com.albatora.app.widget.AlbaToraWidgetUpdater

class MainActivity : ComponentActivity() {
    private var launchActionNonce by mutableIntStateOf(0)
    private var launchTargetTab by mutableIntStateOf(0)
    private var launchOpenStopwatch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)

        setContent {
            val viewModel: AlbaToraViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = when (state.settings.themeOption) {
                ThemeOption.SYSTEM -> systemDark
                ThemeOption.LIGHT -> false
                ThemeOption.DARK -> true
            }

            LaunchedEffect(effectiveDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { effectiveDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { effectiveDark }
                )
            }

            AlbaToraTheme(
                themeOption = state.settings.themeOption,
                colorStyle = state.settings.resolvedColorStyle(),
                customKeyColorHex = state.settings.customKeyColorHex
            ) {
                MainScaffoldScreen(
                    viewModel = viewModel,
                    launchActionNonce = launchActionNonce,
                    launchTargetTab = launchTargetTab,
                    launchOpenStopwatch = launchOpenStopwatch
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val hasTab = intent.hasExtra(AlbaToraWidgetUpdater.EXTRA_OPEN_TAB)
        val hasStopwatch = intent.getBooleanExtra(AlbaToraWidgetUpdater.EXTRA_OPEN_STOPWATCH, false)
        if (hasTab || hasStopwatch) {
            launchTargetTab = intent.getIntExtra(AlbaToraWidgetUpdater.EXTRA_OPEN_TAB, 0).coerceIn(0, 3)
            launchOpenStopwatch = hasStopwatch
            launchActionNonce++
        }
    }
}
