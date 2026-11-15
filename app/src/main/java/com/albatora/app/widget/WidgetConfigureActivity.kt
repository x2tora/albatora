package com.albatora.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.model.NavBarStyle
import com.albatora.app.ui.components.rememberStackedToastState
import com.albatora.app.ui.screens.WidgetStudioScreen
import com.albatora.app.ui.theme.AlbaToraTheme
import com.albatora.app.ui.theme.LocalLiquidGlassEnabled

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED)
        val stateHolder = AlbaToraStateHolder.getInstance(applicationContext)

        fun commitAndFinish() {
            AlbaToraWidgetUpdater.updateAll(applicationContext)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(Activity.RESULT_OK, resultValue)
            } else {
                setResult(Activity.RESULT_OK)
            }
            finish()
        }

        setContent {
            val state by stateHolder.state.collectAsStateWithLifecycle()
            val toastState = rememberStackedToastState()

            AlbaToraTheme(
                themeOption = state.settings.themeOption,
                colorStyle = state.settings.resolvedColorStyle(),
                customKeyColorHex = state.settings.customKeyColorHex
            ) {
                CompositionLocalProvider(
                    LocalLiquidGlassEnabled provides (state.settings.navBarStyle == NavBarStyle.LIQUID_GLASS)
                ) {
                    WidgetStudioScreen(
                        state = state,
                        onUpdateWidgetConfig = { transform ->
                            stateHolder.updateSettings { st ->
                                st.copy(widgetConfig = transform(st.widgetConfig))
                            }
                        },
                        onBack = { commitAndFinish() },
                        onNotify = { toastState.show(it) },
                        toastState = toastState,
                        confirmActionText = "적용",
                        onConfirmFinish = { commitAndFinish() }
                    )
                }
            }
        }
    }
}
