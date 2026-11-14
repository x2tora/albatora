package com.albatora.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { splashViewProvider ->
            val interpolator = DecelerateInterpolator(1.6f)
            val containerFade = ObjectAnimator.ofFloat(splashViewProvider.view, View.ALPHA, 1f, 0f).apply {
                duration = 260L
                this.interpolator = interpolator
            }
            val animators = mutableListOf<android.animation.Animator>(containerFade)
            runCatching { splashViewProvider.iconView }.getOrNull()?.let { icon ->
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.08f).apply {
                    duration = 260L
                    this.interpolator = interpolator
                }
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.08f).apply {
                    duration = 260L
                    this.interpolator = interpolator
                }
            }
            AnimatorSet().apply {
                playTogether(animators)
                doOnEnd { splashViewProvider.remove() }
                start()
            }
        }
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
