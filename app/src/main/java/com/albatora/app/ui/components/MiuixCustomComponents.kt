package com.albatora.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.PayrollCalculator
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

/**
 * Glassmorphic icon container inspired by HyperOS / Miuix glass icons.
 * Uses continuous squircle geometry + miuix-icons + runtime shader textureBlur with glass edge stroke on API 33+.
 */
@Composable
fun MiuixGlassIcon(
    icon: ImageVector,
    contentDescription: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    cornerRadius: Dp = 12.dp
) {
    val isDark = LocalIsDarkTheme.current
    val backdrop = LocalAppBackdrop.current
    val useShaderBlur = isRuntimeShaderSupported() && backdrop != null
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val containerTint = if (isDark) accentColor.copy(alpha = 0.24f) else accentColor.copy(alpha = 0.14f)
    val borderTint = if (isDark) accentColor.copy(alpha = 0.42f) else accentColor.copy(alpha = 0.28f)

    val blurModifier = if (useShaderBlur) {
        Modifier
            .squircleClip(cornerRadius)
            .textureBlur(
                backdrop = backdrop!!,
                shape = shape,
                blurRadius = 16f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(containerTint, BlurBlendMode.SrcOver)
                    )
                ),
                highlight = if (isDark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight
            )
    } else {
        Modifier
            .squircleBackground(containerTint, cornerRadius)
            .squircleBorder(1.dp, borderTint, cornerRadius)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(blurModifier)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.35f),
                        Color.Transparent
                    )
                )
            ),
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
 * Modifier helper for HyperOS-style glassmorphic Hero/Live Tracker cards with fallback below API 33.
 */
@Composable
fun Modifier.miuixGlassSurface(
    cornerRadius: Dp = 20.dp,
    tintColor: Color? = null,
    useBigHighlight: Boolean = true
): Modifier {
    val isDark = LocalIsDarkTheme.current
    val backdrop = LocalAppBackdrop.current
    val baseTint = tintColor ?: MiuixTheme.colorScheme.surfaceContainer.copy(
        alpha = if (isDark) 0.78f else 0.86f
    )
    val shape = RoundedCornerShape(cornerRadius)
    return if (isRuntimeShaderSupported() && backdrop != null) {
        val hl = when {
            useBigHighlight && isDark -> Highlight.GlassStrokeBigDark
            useBigHighlight && !isDark -> Highlight.GlassStrokeBigLight
            isDark -> Highlight.GlassStrokeMiddleDark
            else -> Highlight.GlassStrokeMiddleLight
        }
        this
            .squircleClip(cornerRadius)
            .textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = 24f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(baseTint, BlurBlendMode.SrcOver)
                    )
                ),
                highlight = hl
            )
    } else {
        this
            .squircleBackground(
                color = tintColor ?: MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = cornerRadius
            )
            .squircleBorder(
                width = 1.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.28f),
                cornerRadius = cornerRadius
            )
    }
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
                    transitionSpec = {
                        (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn())
                            .togetherWith(slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut())
                            .using(SizeTransform(clip = false))
                    },
                    label = "odometer_digit_$posFromRight"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        style = textStyle,
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
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
