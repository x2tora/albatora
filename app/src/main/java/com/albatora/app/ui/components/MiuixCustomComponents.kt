package com.albatora.app.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.albatora.app.ui.theme.AlbaMotion
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.LocalAppBackdrop
import com.albatora.app.ui.theme.LocalIsDarkTheme
import com.albatora.app.ui.theme.LocalLiquidGlassEnabled
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean semantic icon slot without an extra enclosing box; enlarged proportionally for crisp clarity.
 */
@Composable
fun MiuixGlassIcon(
    icon: ImageVector,
    contentDescription: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    cornerRadius: Dp = 12.dp
) {
    val resolvedIconSize = maxOf(iconSize * 1.42f, (size.value * 0.72f).dp)
    val containerSize = maxOf(resolvedIconSize, (size.value * 0.80f).dp)

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(resolvedIconSize)
        )
    }
}

/**
 * Displays a user-selected icon key (or mapped legacy emoji) inside a unified [MiuixGlassIcon] container.
 */
@Composable
fun MiuixEntityBadge(
    emoji: String,
    fallbackIcon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = null
) {
    MiuixGlassIcon(
        icon = AlbaIconCatalog.resolveIcon(emoji, fallbackIcon),
        contentDescription = contentDescription,
        accentColor = accentColor,
        modifier = modifier,
        size = size,
        iconSize = (size.value * 0.48f).dp,
        cornerRadius = cornerRadius
    )
}

/**
 * Minimal status dot for active/paused shift indicators.
 */
@Composable
fun LiveStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Precision chronometer focus ring for the live earnings focus timer stage.
 */
@Composable
fun FocusTimerRingCanvas(
    progress: Float?,
    accentColor: Color,
    trackColor: Color,
    tickColor: Color,
    isPaused: Boolean,
    secondFraction: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val scale = (size.minDimension / 230.dp.toPx()).coerceIn(1f, 1.8f)
        val strokeWidth = (10f * scale).dp.toPx()
        val inset = strokeWidth / 2f + (12f * scale).dp.toPx()
        val arcDiameter = size.minDimension - inset * 2f
        val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
        val arcSize = Size(arcDiameter, arcDiameter)
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerR = arcDiameter / 2f + strokeWidth / 2f + (8f * scale).dp.toPx()

        // Chronometer dial ticks (60 crisp ticks around perimeter)
        for (i in 0 until 60) {
            val angleRad = (-90.0 + i * 6.0) * (PI / 180.0)
            val isMajor = i % 5 == 0
            val innerLen = (if (isMajor) 7f * scale else 3.5f * scale).dp.toPx()
            val start = Offset(
                x = center.x + (outerR - innerLen) * cos(angleRad).toFloat(),
                y = center.y + (outerR - innerLen) * sin(angleRad).toFloat()
            )
            val end = Offset(
                x = center.x + outerR * cos(angleRad).toFloat(),
                y = center.y + outerR * sin(angleRad).toFloat()
            )
            drawLine(
                color = if (isMajor) tickColor.copy(alpha = 0.55f) else tickColor.copy(alpha = 0.25f),
                start = start,
                end = end,
                strokeWidth = (if (isMajor) 1.5f * scale else 1f * scale).dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Background track ring
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val sweepColor = if (isPaused) Color(0xFFD97706) else accentColor
        if (progress != null) {
            val sweep = (progress.coerceIn(0f, 1f) * 360f).coerceAtLeast(3f)
            drawArc(
                color = sweepColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            // Stopwatch mode: calm 60-second sweep showing live forward progression
            val startAngle = -90f + (secondFraction.coerceIn(0f, 1f) * 360f)
            drawArc(
                color = sweepColor,
                startAngle = startAngle - 72f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Calm HyperOS squircle raised card surface with quiet 8% hairline border.
 */
@Composable
fun Modifier.miuixGlassSurface(
    cornerRadius: Dp = 20.dp,
    tintColor: Color? = null,
    useBigHighlight: Boolean = true
): Modifier {
    val bg = tintColor ?: MiuixTheme.colorScheme.surfaceContainer
    return this
        .squircleBackground(color = bg, cornerRadius = cornerRadius)
        .squircleBorder(
            width = 1.dp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            cornerRadius = cornerRadius
        )
}

/**
 * Rolling 0.1-second odometer numeric display
 * up to 1 decimal place (e.g., "12,345.6원").
 */
@Composable
fun OdometerCurrencyText(
    amountWon: Double,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
    color: Color = MiuixTheme.colorScheme.onSurface,
    suffix: String = "원"
) {
    val formatted = remember(amountWon) {
        val safe = if (amountWon < 0.0) 0.0 else amountWon
        String.format(Locale.US, "%,.1f", safe)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        formatted.forEachIndexed { index, ch ->
            // Key by index from right so decimal & places stay aligned smoothly
            val posFromRight = formatted.length - index
            key(posFromRight) {
                AnimatedContent(
                    targetState = ch,
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        (slideInVertically(animationSpec = AlbaMotion.OdometerSpring, initialOffsetY = { it / 2 }) +
                            fadeIn(tween(150)))
                            .togetherWith(
                                slideOutVertically(animationSpec = AlbaMotion.OdometerSpring, targetOffsetY = { -it / 2 }) +
                                    fadeOut(tween(120))
                            )
                            .using(SizeTransform(clip = true))
                    },
                    label = "odometer_digit_$posFromRight"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        style = textStyle.copy(fontFeatureSettings = "tnum"),
                        color = color,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = suffix,
            style = textStyle.copy(
                fontSize = textStyle.fontSize * 0.62f,
                fontWeight = FontWeight.SemiBold
            ),
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

@Composable
fun SquirclePillBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    textStyle: TextStyle = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.94f else 1f,
        animationSpec = AlbaMotion.InteractiveSpring,
        label = "pill_press_scale"
    )
    val base = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .squircleBackground(backgroundColor, cornerRadius = 10.dp)
        .squircleClip(10.dp)
    val finalModifier = if (onClick != null) {
        base.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
    } else {
        base
    }
    Box(
        modifier = finalModifier.padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = textStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyDataBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = null,
                accentColor = accentColor,
                size = 60.dp,
                iconSize = 28.dp,
                cornerRadius = 18.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    text = actionLabel,
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Reusable Miuix NumberPicker Time Dialog (hours 0..23, minutes step 5 or 1).
 */
@Composable
fun MiuixTimePickerDialog(
    show: Boolean,
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hour by remember(show, initialMinuteOfDay) { mutableIntStateOf((initialMinuteOfDay / 60).coerceIn(0, 23)) }
    var minute by remember(show, initialMinuteOfDay) { mutableIntStateOf((initialMinuteOfDay % 60).coerceIn(0, 59)) }

    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberPicker(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 0..23,
                    label = { String.format(Locale.US, "%02d시", it) },
                    wrapAround = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ":",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                NumberPicker(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = { String.format(Locale.US, "%02d분", it) },
                    wrapAround = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "확인",
                    onClick = { onConfirm(hour * 60 + minute) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Reusable Miuix NumberPicker Date Dialog (yyyy-MM-dd).
 */
@Composable
fun MiuixDatePickerDialog(
    show: Boolean,
    title: String,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var year by remember(show, initialDate) { mutableIntStateOf(initialDate.year) }
    var month by remember(show, initialDate) { mutableIntStateOf(initialDate.monthValue) }
    var day by remember(show, initialDate) { mutableIntStateOf(initialDate.dayOfMonth) }

    val maxDay = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    LaunchedEffect(maxDay) {
        if (day > maxDay) day = maxDay
    }

    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NumberPicker(
                    value = year,
                    onValueChange = { year = it },
                    range = 2020..2035,
                    label = { "${it}년" },
                    wrapAround = false,
                    modifier = Modifier.weight(1.2f)
                )
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    range = 1..12,
                    label = { "${it}월" },
                    wrapAround = true,
                    modifier = Modifier.weight(1f)
                )
                NumberPicker(
                    value = day.coerceIn(1, maxDay),
                    onValueChange = { day = it },
                    range = 1..maxDay,
                    label = { "${it}일" },
                    wrapAround = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "선택",
                    onClick = { onConfirm(LocalDate.of(year, month, day.coerceIn(1, maxDay))) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun dayOfWeekKorean(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "월"
    2 -> "화"
    3 -> "수"
    4 -> "목"
    5 -> "금"
    6 -> "토"
    7 -> "일"
    else -> ""
}

@Composable
fun ConfirmActionDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    isDestructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = "취소",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.error,
                        textColor = Color.White
                    )
                } else {
                    ButtonDefaults.textButtonColorsPrimary()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Shared floating top bar that reveals separate frosted floating squircle islands behind only the
 * occupied slots (navigation button, title, action buttons), animating strictly left-to-right.
 */
@Composable
fun MiuixTopBarActionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .width(1.dp)
            .height(18.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f))
    )
}

@Composable
fun MiuixFloatingScrollTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    isScrolledOverride: Boolean? = null,
    backdrop: LayerBackdrop? = null,
    maxContentWidth: Dp = Dp.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val surfaceBg = MiuixTheme.colorScheme.surface
    SideEffect {
        // Keep ExitUntilCollapsedScrollBehavior from consuming upward scroll deltas when SmallTopAppBar is replaced
        if (scrollBehavior.state.heightOffsetLimit != 0f) {
            scrollBehavior.state.heightOffsetLimit = 0f
        }
        if (scrollBehavior.state.heightOffset != 0f) {
            scrollBehavior.state.heightOffset = 0f
        }
    }
    val isScrolled by remember(scrollBehavior, isScrolledOverride) {
        derivedStateOf {
            isScrolledOverride ?: (scrollBehavior.state.contentOffset < -12f ||
                scrollBehavior.state.overlappedFraction > 0.04f)
        }
    }

    var hasNav by remember { mutableStateOf(false) }
    var hasActions by remember { mutableStateOf(false) }
    var navPillVisible by remember { mutableStateOf(false) }
    var titlePillVisible by remember { mutableStateOf(false) }
    var actionsPillVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolled, hasNav, hasActions) {
        val target = isScrolled
        val stepDelayMs = 85L
        if (target) {
            // Enter stagger: left -> right (navigationIcon -> title -> actions)
            if (hasNav) {
                navPillVisible = true
                delay(stepDelayMs)
            } else {
                navPillVisible = false
            }
            titlePillVisible = true
            if (hasActions) {
                delay(stepDelayMs)
                actionsPillVisible = true
            } else {
                actionsPillVisible = false
            }
        } else {
            // Exit stagger: right -> left (actions -> title -> navigationIcon)
            if (hasActions) {
                actionsPillVisible = false
                delay(stepDelayMs)
            } else {
                actionsPillVisible = false
            }
            titlePillVisible = false
            if (hasNav) {
                delay(stepDelayMs)
                navPillVisible = false
            } else {
                navPillVisible = false
            }
        }
    }

    val islandSpring = spring<Float>(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessMediumLow
    )
    val navProgress by animateFloatAsState(
        targetValue = if (navPillVisible) 1f else 0f,
        animationSpec = islandSpring,
        label = "floating_top_nav_progress"
    )
    val titleProgress by animateFloatAsState(
        targetValue = if (titlePillVisible) 1f else 0f,
        animationSpec = islandSpring,
        label = "floating_top_title_progress"
    )
    val actionsProgress by animateFloatAsState(
        targetValue = if (actionsPillVisible) 1f else 0f,
        animationSpec = islandSpring,
        label = "floating_top_actions_progress"
    )
    val slideOffsetPx = with(density) { 48.dp.toPx() }
    val useShader = isRuntimeShaderSupported()
    val edgeOverlayColor = surfaceBg.copy(alpha = if (useShader) 0.48f else 0.90f)
    val topEdgeBlurMod = if (backdrop != null && useShader) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 6f,
            gradient = ProgressiveBlur.Top,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(edgeOverlayColor, BlurBlendMode.SrcOver))
            )
        )
    } else {
        Modifier.background(
            Brush.verticalGradient(
                0.0f to surfaceBg.copy(alpha = 0.94f),
                0.62f to surfaceBg.copy(alpha = 0.56f),
                1.0f to Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Restored top progressive blur scrim behind the separated floating islands
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(topEdgeBlurMod)
        )

        Row(
            modifier = Modifier
                .then(if (maxContentWidth != Dp.Unspecified) Modifier.widthIn(max = maxContentWidth) else Modifier)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingTopBarIsland(
                    progress = navProgress,
                    isDark = isDark,
                    backdrop = backdrop,
                    cornerRadius = 21.dp,
                    slideOffsetPx = slideOffsetPx,
                    onHasContentChange = { hasNav = it },
                    content = navigationIcon
                )
                FloatingTopBarIsland(
                    progress = titleProgress,
                    isDark = isDark,
                    backdrop = backdrop,
                    cornerRadius = 21.dp,
                    slideOffsetPx = slideOffsetPx,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInVertically(animationSpec = AlbaMotion.OffsetSpring) { it / 6 } +
                                fadeIn(tween(200)))
                                .togetherWith(
                                    slideOutVertically(animationSpec = AlbaMotion.ExitOffsetSpring) { -it / 6 } +
                                        fadeOut(tween(130))
                                )
                        },
                        label = "top_bar_title_fade"
                    ) { animTitle ->
                        Text(
                            text = animTitle,
                            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            FloatingTopBarIsland(
                progress = actionsProgress,
                isDark = isDark,
                backdrop = backdrop,
                cornerRadius = 21.dp,
                slideOffsetPx = slideOffsetPx,
                onHasContentChange = { hasActions = it }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun FloatingTopBarIsland(
    progress: Float,
    isDark: Boolean,
    backdrop: LayerBackdrop?,
    cornerRadius: Dp,
    slideOffsetPx: Float,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onHasContentChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val p = progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val minSizePx = with(density) { 48.dp.roundToPx() }
    val pillShape = RoundedCornerShape(cornerRadius)
    val pillFill = if (isDark) {
        MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
    } else {
        Color.White.copy(alpha = 0.94f)
    }
    val pillBorder = MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.14f else 0.08f)
    val useRealLiquidGlass = isLiquidGlass &&
        backdrop != null &&
        isRuntimeShaderSupported() &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Layout(
        modifier = modifier.onSizeChanged { size ->
            onHasContentChange(size.width > 0 && size.height > 0)
        },
        content = {
            Box(
                modifier = Modifier.padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            if (p > 0.005f) {
                val bgSurfaceMod = if (useRealLiquidGlass) {
                    Modifier.liquidGlassSurface(
                        backdrop = backdrop,
                        shape = pillShape,
                        cornerRadius = cornerRadius,
                        blurRadius = 2.5.dp,
                        refractionHeight = 10.dp,
                        refractionAmount = 7.dp,
                        chromaticAberration = 2.4.dp,
                        surfaceTint = if (isDark) {
                            Color(0xFF141822).copy(alpha = 0.40f)
                        } else {
                            Color(0xFFF6F7FB).copy(alpha = 0.46f)
                        },
                        grainAlpha = 0.06f,
                        fallbackColor = if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color.White
                    )
                } else {
                    val blurMod = if (backdrop != null && isRuntimeShaderSupported()) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = pillShape,
                            blurRadius = 10f,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(BlendColorEntry(pillFill, BlurBlendMode.SrcOver))
                            )
                        )
                    } else {
                        Modifier
                    }
                    Modifier
                        .clip(pillShape)
                        .then(blurMod)
                        .squircleBackground(color = pillFill, cornerRadius = cornerRadius)
                        .squircleBorder(width = 1.dp, color = pillBorder, cornerRadius = cornerRadius)
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = (1f - p) * -slideOffsetPx
                            alpha = p
                            val scale = 0.92f + 0.08f * p
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(
                            elevation = (10f * p).dp,
                            shape = pillShape,
                            ambientColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.14f),
                            spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f)
                        )
                        .then(bgSurfaceMod)
                )
            }
        }
    ) { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        if (contentPlaceable.width == 0 || contentPlaceable.height == 0) {
            return@Layout layout(0, 0) {}
        }
        val pillW = contentPlaceable.width.coerceAtLeast(minSizePx).coerceAtMost(constraints.maxWidth)
        val pillH = contentPlaceable.height.coerceAtLeast(minSizePx).coerceAtMost(constraints.maxHeight)
        val bgPlaceable = if (measurables.size > 1) {
            measurables[1].measure(Constraints.fixed(pillW, pillH))
        } else {
            null
        }
        layout(pillW, pillH) {
            bgPlaceable?.placeRelative(0, 0)
            contentPlaceable.placeRelative(
                (pillW - contentPlaceable.width) / 2,
                (pillH - contentPlaceable.height) / 2
            )
        }
    }
}

/**
 * Progressive bottom edge blur overlay matching the top edge progressive blur across all screens.
 */
@Composable
fun MiuixBottomEdgeBlur(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    extraHeight: Dp = 52.dp
) {
    val surfaceBg = MiuixTheme.colorScheme.surface
    val useShader = isRuntimeShaderSupported()
    val edgeOverlayColor = surfaceBg.copy(alpha = if (useShader) 0.48f else 0.90f)
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val blurMod = if (useShader) {
        Modifier.progressiveTextureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 6f,
            gradient = ProgressiveBlur.Bottom,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(edgeOverlayColor, BlurBlendMode.SrcOver))
            )
        )
    } else {
        Modifier.background(
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.38f to surfaceBg.copy(alpha = 0.56f),
                1.0f to surfaceBg.copy(alpha = 0.94f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navBottomInset + extraHeight)
            .then(blurMod)
    )
}
