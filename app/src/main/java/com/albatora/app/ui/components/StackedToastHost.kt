package com.albatora.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.albatora.app.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Stable
data class ToastEntry(
    val id: Long,
    val message: String,
    val durationMs: Long = 3400L
)

@Stable
class StackedToastState {
    private var nextId by mutableLongStateOf(0L)
    val entries = mutableStateListOf<ToastEntry>()

    fun show(message: String, durationMs: Long = 3400L) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val id = ++nextId
        entries.add(0, ToastEntry(id = id, message = trimmed, durationMs = durationMs))
        if (entries.size > 6) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun dismiss(id: Long) {
        entries.removeAll { it.id == id }
    }

    fun dismissAll() {
        entries.clear()
    }
}

@Composable
fun rememberStackedToastState(): StackedToastState = remember { StackedToastState() }

/**
 * Stacked card-deck toast host:
 * - Newly pushed notifications land on top (depth = 0), covering previous notifications.
 * - Older notifications peek upward layer-by-layer underneath with depth scaling & offset.
 * - Swiping the top toast left or right slides it away and promotes the next notification in order.
 * - Auto-dismiss timer only ticks for the active frontmost toast so buried cards remain readable as you swipe.
 */
@Composable
fun StackedToastHost(
    state: StackedToastState,
    modifier: Modifier = Modifier
) {
    val entries = state.entries
    if (entries.isEmpty()) return

    val frontEntry = entries.firstOrNull()
    LaunchedEffect(frontEntry?.id) {
        if (frontEntry != null) {
            delay(frontEntry.durationMs)
            state.dismiss(frontEntry.id)
        }
    }

    val visibleEntries = entries.take(4)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Render back-to-front so front card sits at highest zIndex
        for (depth in visibleEntries.lastIndex downTo 0) {
            val item = visibleEntries[depth]
            key(item.id) {
                val targetScale = (1f - depth * 0.045f).coerceAtLeast(0.86f)
                val targetOffsetY = (-9 * depth).dp
                val targetAlpha = (1f - depth * 0.14f).coerceAtLeast(0.58f)

                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "toastDeckScale"
                )
                val animatedOffsetY by animateDpAsState(
                    targetValue = targetOffsetY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "toastDeckOffsetY"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "toastDeckAlpha"
                )

                SwipeableToastCard(
                    message = item.message,
                    isFront = depth == 0,
                    onDismiss = { state.dismiss(item.id) },
                    modifier = Modifier
                        .zIndex((visibleEntries.size - depth).toFloat())
                        .offset(y = animatedOffsetY)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .alpha(animatedAlpha)
                )
            }
        }
    }
}

@Composable
private fun SwipeableToastCard(
    message: String,
    isFront: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }

    val containerColor = if (isFront) {
        if (isDark) MiuixTheme.colorScheme.surfaceContainerHighest else MiuixTheme.colorScheme.surfaceContainerHigh
    } else {
        if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else MiuixTheme.colorScheme.surfaceContainer
    }
    val borderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.14f else 0.09f)
    val corner = 18.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragX.value.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = (dragX.value / 55f).coerceIn(-8f, 8f)
            }
            .dropShadow(
                shape = RoundedCornerShape(corner),
                shadow = Shadow(
                    radius = if (isFront) 14.dp else 8.dp,
                    color = Color.Black,
                    alpha = if (isDark) 0.34f else 0.13f
                )
            )
            .squircleClip(corner)
            .squircleBackground(color = containerColor, cornerRadius = corner)
            .border(width = 0.75.dp, color = borderColor, shape = RoundedCornerShape(corner))
            .then(
                if (isFront) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val current = dragX.value
                                val threshold = size.width * 0.24f
                                if (abs(current) > threshold) {
                                    val direction = if (current == 0f) 1f else sign(current)
                                    scope.launch {
                                        dragX.animateTo(
                                            targetValue = direction * size.width * 1.15f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                        onDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        dragX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    dragX.snapTo(dragX.value + dragAmount)
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .squircleBackground(
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                    cornerRadius = 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .squircleClip(9.dp)
                .clickable(enabled = isFront, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Close,
                contentDescription = "알림 닫기",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
