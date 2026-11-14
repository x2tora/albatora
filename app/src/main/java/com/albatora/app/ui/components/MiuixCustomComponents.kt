package com.albatora.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.LocalAppBackdrop
import com.albatora.app.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
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
 * Calm squircle icon container with subtle accent fill and hairline border.
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
    val isDark = LocalIsDarkTheme.current
    val containerTint = accentColor.copy(alpha = if (isDark) 0.18f else 0.11f)
    val borderTint = accentColor.copy(alpha = if (isDark) 0.30f else 0.18f)

    Box(
        modifier = modifier
            .size(size)
            .squircleBackground(containerTint, cornerRadius)
            .squircleBorder(1.dp, borderTint, cornerRadius),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(iconSize)
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
 * Quiet signature dot marking live/paused/status states without decorative emoji clutter.
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
        val strokeWidth = 10.dp.toPx()
        val inset = strokeWidth / 2f + 12.dp.toPx()
        val arcDiameter = size.minDimension - inset * 2f
        val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
        val arcSize = Size(arcDiameter, arcDiameter)
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerR = arcDiameter / 2f + strokeWidth / 2f + 8.dp.toPx()

        // Chronometer dial ticks (60 crisp ticks around perimeter)
        for (i in 0 until 60) {
            val angleRad = (-90.0 + i * 6.0) * (PI / 180.0)
            val isMajor = i % 5 == 0
            val innerLen = if (isMajor) 7.dp.toPx() else 3.5.dp.toPx()
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
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
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
 * Q4=B: 0.1-second (100ms) continuous odometer / slot-machine rolling display
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
                        (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn())
                            .togetherWith(slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut())
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
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .squircleBackground(backgroundColor, cornerRadius = 10.dp)
        .squircleClip(10.dp)
    val finalModifier = if (onClick != null) {
        base.clickable(onClick = onClick)
    } else {
        base
    }
    Box(
        modifier = finalModifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
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
