package com.albatora.app.ui.components

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints as ComposeConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albatora.app.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.shader.asAndroidRuntimeShader
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Liquid Glass bottom navigation bar built on miuix-blur, AGSL RuntimeShader, and miuix-squircle.
 */
private val DockHeight = 64.dp
private const val DockPressedScale = 1.04f
private val DockBlurRadius = 14.dp
private val DockRefractionHeight = 12.dp
private val DockRefractionAmount = 7.dp
private val DockChromaticAberration = 2.0.dp

private val GlassSurfaceTint = Color(0xFF6E7D96).copy(alpha = 0.03f)
private const val GlassGrainAlpha = 0.10f
private const val GRAIN_TILE = 96

private val SliderInset = 5.dp
private val SliderDragStartSlop = 4.dp
private val SliderRefractionHeight = 10.dp
private val SliderRefractionAmount = 6.dp
private val SliderChromaticAberration = 1.8.dp
private val SliderPressedOverhang = 4.dp
private const val SliderStretchMax = 0.22f

@Language("AGSL")
private const val LIQUID_LENS_SHADER = """
    uniform shader content;
    uniform float2 uPad;
    uniform float2 uSize;
    uniform float uHeight;
    uniform float uAmount;
    uniform float uChromatic;

    float sdPill(float2 p, float2 halfSize, float radius) {
        float2 q = abs(p) - (halfSize - float2(radius));
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = max(uSize * 0.5, float2(1.0));
        float r = min(halfSize.x, halfSize.y);
        float2 local = (fragCoord - uPad) - halfSize;

        float sd = sdPill(local, halfSize, r);
        float distInward = -sd;
        if (distInward <= 0.0 || distInward >= uHeight) {
            return content.eval(fragCoord);
        }

        float eps = 1.5;
        float gx = sdPill(local + float2(eps, 0.0), halfSize, r) - sdPill(local - float2(eps, 0.0), halfSize, r);
        float gy = sdPill(local + float2(0.0, eps), halfSize, r) - sdPill(local - float2(0.0, eps), halfSize, r);
        float2 edgeNormal = float2(gx, gy);
        float gLen = length(edgeNormal);
        float2 dir = (gLen > 0.0001) ? (edgeNormal / gLen) : float2(0.0);

        float centerLen = length(local);
        if (centerLen > 0.0001) {
            dir = normalize(dir + (local / centerLen) * 0.18);
        }

        float t = clamp(1.0 - distInward / max(uHeight, 1.0), 0.0, 1.0);
        float bend = t * t * (3.0 - 2.0 * t);
        float2 maxCoord = uPad * 2.0 + uSize - float2(0.5);
        float2 baseCoord = fragCoord - dir * (bend * uAmount);
        float2 chromaShift = dir * (bend * uChromatic);

        half red = content.eval(clamp(baseCoord + chromaShift, float2(0.5), maxCoord)).r;
        half4 greenSample = content.eval(clamp(baseCoord, float2(0.5), maxCoord));
        half blue = content.eval(clamp(baseCoord - chromaShift, float2(0.5), maxCoord)).b;
        return half4(red, greenSample.g, blue, greenSample.a);
    }
"""

@Composable
private fun rememberGrainBrush(): ShaderBrush = remember {
    val random = Random(0x5EEDBEEF)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) {
        val alpha = random.nextInt(0, 49)
        val level = random.nextInt(60, 201)
        (alpha shl 24) or (level shl 16) or (level shl 8) or level
    }
    val bitmap = Bitmap.createBitmap(pixels, GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun BackdropEffectScope.chainPlatformRenderEffect(effect: android.graphics.RenderEffect) {
    val prev = renderEffect
    renderEffect = if (prev != null) {
        android.graphics.RenderEffect.createChainEffect(effect, prev.asAndroidRenderEffect())
            .asComposeRenderEffect()
    } else {
        effect.asComposeRenderEffect()
    }
}

private fun BackdropEffectScope.vibrancySaturation(saturation: Float = 1.5f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    chainPlatformRenderEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    )
}

private fun BackdropEffectScope.liquidLensEffect(
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    chromaticPx: Float
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val scale = downscaleFactor.coerceAtLeast(1).toFloat()
    val shader = obtainRuntimeShader("AlbaLiquidLens", LIQUID_LENS_SHADER)
    shader.setFloatUniform("uPad", padding / scale, padding / scale)
    shader.setFloatUniform("uSize", size.width / scale, size.height / scale)
    shader.setFloatUniform("uHeight", (refractionHeightPx / scale).coerceAtLeast(1f))
    shader.setFloatUniform("uAmount", refractionAmountPx / scale)
    shader.setFloatUniform("uChromatic", chromaticPx / scale)
    chainPlatformRenderEffect(
        android.graphics.RenderEffect.createRuntimeShaderEffect(
            shader.asAndroidRuntimeShader(),
            "content"
        )
    )
}

@Composable
fun Modifier.liquidGlassSurface(
    backdrop: LayerBackdrop?,
    shape: Shape,
    blurRadius: Dp = DockBlurRadius,
    refractionHeight: Dp = DockRefractionHeight,
    refractionAmount: Dp = DockRefractionAmount,
    chromaticAberration: Dp = DockChromaticAberration,
    refraction: Boolean = true,
    surfaceTint: Color = GlassSurfaceTint,
    grainAlpha: Float = GlassGrainAlpha,
    fallbackColor: Color = MiuixTheme.colorScheme.surfaceContainer
): Modifier {
    val isDark = LocalIsDarkTheme.current
    if (backdrop == null || !isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.clip(shape).background(fallbackColor.copy(alpha = 0.94f))
    }

    val grainBrush = rememberGrainBrush()
    val highlight = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { highlight },
        onDrawSurface = {
            if (surfaceTint.alpha > 0f) drawRect(color = surfaceTint)
            if (grainAlpha > 0f) drawRect(brush = grainBrush, alpha = grainAlpha)
        },
        effects = {
            vibrancySaturation(1.5f)
            blur(blurRadius.toPx())
            if (refraction) {
                liquidLensEffect(
                    refractionHeightPx = refractionHeight.toPx(),
                    refractionAmountPx = refractionAmount.toPx(),
                    chromaticPx = chromaticAberration.toPx()
                )
            }
        }
    )
}

private data class NavSlot(
    val slotIndex: Int,
    val tabIndex: Int?, // null for the centered emphasized '+' action
    val icon: ImageVector,
    val label: String
)

private fun tabToSlot(tabIndex: Int): Int = if (tabIndex < 2) tabIndex else tabIndex + 1

private fun slotToTab(slotIndex: Int): Int = if (slotIndex < 2) slotIndex else slotIndex - 1

private fun nearestAllSlot(
    leftPx: Float,
    insetPx: Float,
    itemWidthPx: Float
): Int {
    if (itemWidthPx <= 0f) return 0
    val approxSlot = ((leftPx - insetPx) / itemWidthPx).roundToInt()
    return approxSlot.coerceIn(0, 4)
}

private fun stretchOf(leftPx: Float, anchorPx: Float, itemWidthPx: Float): Float {
    if (itemWidthPx <= 0f) return 0f
    val t = (abs(leftPx - anchorPx) / itemWidthPx).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
fun FloatingLiquidGlassNavBar(
    backdrop: LayerBackdrop?,
    selectedTab: Int,
    hasActiveShift: Boolean,
    unpaidCount: Int,
    onSelectTab: (Int) -> Unit,
    onCenterAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var barPressed by remember { mutableStateOf(false) }
    val barScale by animateFloatAsState(
        targetValue = if (barPressed) DockPressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockPressScale"
    )

    val slots = remember {
        listOf(
            NavSlot(0, 0, MiuixIcons.Regular.Home, "홈"),
            NavSlot(1, 1, MiuixIcons.Regular.Months, "달력"),
            NavSlot(2, null, MiuixIcons.Add, "추가"),
            NavSlot(3, 2, MiuixIcons.Regular.Notes, "기록"),
            NavSlot(4, 3, MiuixIcons.Regular.Tune, "계산기")
        )
    }

    // Dual-Box sibling structure:
    // 1) Captured dock layer (page-glass background + nav icons/labels/center '+') via dockBackdrop
    // 2) Sliding liquid-glass selector lens ABOVE the dock items so it visibly refracts them
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DockHeight)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            }
    ) {
        LiquidGlassNavBarContent(
            slots = slots,
            selectedTab = selectedTab,
            hasActiveShift = hasActiveShift,
            unpaidCount = unpaidCount,
            onSelectTab = onSelectTab,
            onCenterAddClick = onCenterAddClick,
            pageBackdrop = backdrop,
            contentHeight = DockHeight,
            onBarPressedChange = { barPressed = it },
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun LiquidGlassNavBarContent(
    slots: List<NavSlot>,
    selectedTab: Int,
    hasActiveShift: Boolean,
    unpaidCount: Int,
    onSelectTab: (Int) -> Unit,
    onCenterAddClick: () -> Unit,
    pageBackdrop: LayerBackdrop?,
    contentHeight: Dp,
    onBarPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val dockBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val slotCount = slots.size
    var barWidthPx by remember { mutableIntStateOf(0) }

    val itemWidthPx = if (barWidthPx == 0) 0f else barWidthPx.toFloat() / slotCount
    val insetPx = with(density) { SliderInset.toPx() }
    val sliderHeight = (contentHeight - SliderInset * 2).coerceAtLeast(0.dp)
    val sliderWidthPx = (itemWidthPx - insetPx * 2f).coerceAtLeast(0f)
    val sliderHeightPx = with(density) { sliderHeight.toPx() }

    val targetSlot = tabToSlot(selectedTab.coerceIn(0, 3))
    val targetOffsetPx = itemWidthPx * targetSlot + insetPx

    val interactionSources = remember(slotCount) { List(slotCount) { MutableInteractionSource() } }
    var pressedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionSources) {
        coroutineScope {
            interactionSources.forEach { src ->
                launch {
                    src.interactions.collect { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> pressedCount++
                            is PressInteraction.Release, is PressInteraction.Cancel ->
                                pressedCount = (pressedCount - 1).coerceAtLeast(0)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
    val anyPressed = pressedCount > 0

    var sliderLeftPx by remember { mutableFloatStateOf(0f) }
    val fingerTargetState = remember { mutableFloatStateOf(0f) }
    val restingTargetState = remember { mutableFloatStateOf(0f) }
    SideEffect { restingTargetState.floatValue = targetOffsetPx }

    val springVelocity = remember { mutableFloatStateOf(0f) }
    val draggingState = remember { mutableStateOf(false) }

    val pressed = anyPressed || draggingState.value
    SideEffect { onBarPressedChange(pressed) }

    var dragHighlightSlot by remember { mutableStateOf<Int?>(null) }
    val anchorSlot = dragHighlightSlot ?: targetSlot
    val anchorPx = itemWidthPx * anchorSlot + insetPx

    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sliderPress"
    )
    val pressOutsetPx = (insetPx + with(density) { SliderPressedOverhang.toPx() }) * pressProgress

    LaunchedEffect(itemWidthPx) {
        if (itemWidthPx <= 0f) return@LaunchedEffect
        sliderLeftPx = restingTargetState.floatValue
        springVelocity.floatValue = 0f

        val stiffness = 110f
        val dampingRest = 2f * 0.70f * sqrt(stiffness)
        val dampingDrag = 2f * 1.0f * sqrt(stiffness)
        var lastNanos = 0L

        while (true) {
            val now = withFrameNanos { it }
            val dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1_000_000_000f
            lastNanos = now
            if (dt <= 0f) continue

            val safeDt = dt.coerceAtMost(0.05f)
            val dragging = draggingState.value
            val target = if (dragging) fingerTargetState.floatValue else restingTargetState.floatValue
            var v = springVelocity.floatValue
            v += (stiffness * (target - sliderLeftPx) - (if (dragging) dampingDrag else dampingRest) * v) * safeDt
            springVelocity.floatValue = v
            sliderLeftPx += v * safeDt
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(itemWidthPx) {
                if (itemWidthPx <= 0f) return@pointerInput
                val minLeft = insetPx
                val maxLeft = itemWidthPx * (slotCount - 1) + insetPx
                val slopPx = with(density) { SliderDragStartSlop.toPx() }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val startX = down.position.x
                    var started = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            if (started) {
                                draggingState.value = false
                                dragHighlightSlot = null
                                val snapSlot = nearestAllSlot(
                                    leftPx = fingerTargetState.floatValue,
                                    insetPx = insetPx,
                                    itemWidthPx = itemWidthPx
                                )
                                if (snapSlot == 2) {
                                    onCenterAddClick()
                                } else {
                                    onSelectTab(slotToTab(snapSlot))
                                }
                            }
                            break
                        }
                        if (change.isConsumed) {
                            if (started) {
                                draggingState.value = false
                                dragHighlightSlot = null
                            }
                            break
                        }

                        val dx = change.position.x - startX
                        if (!started) {
                            if (abs(dx) < slopPx) continue
                            started = true
                            draggingState.value = true
                            val initialTarget = (change.position.x - sliderWidthPx / 2f).coerceIn(minLeft, maxLeft)
                            fingerTargetState.floatValue = initialTarget
                            dragHighlightSlot = nearestAllSlot(initialTarget, insetPx, itemWidthPx)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change.consume()

                        val next = (change.position.x - sliderWidthPx / 2f).coerceIn(minLeft, maxLeft)
                        fingerTargetState.floatValue = next
                        val nearest = nearestAllSlot(next, insetPx, itemWidthPx)
                        if (nearest != dragHighlightSlot) {
                            dragHighlightSlot = nearest
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
    ) {
        // Layer 1 (underneath selector lens, captured by dockBackdrop):
        // Dock glass surface + crisp tab icons/labels + center '+' button
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(dockBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassSurface(
                        backdrop = pageBackdrop,
                        shape = RoundedCornerShape(50),
                        blurRadius = DockBlurRadius,
                        refractionHeight = DockRefractionHeight,
                        refractionAmount = DockRefractionAmount,
                        refraction = true,
                        surfaceTint = if (isDark) {
                            Color(0xFF14161B).copy(alpha = 0.72f)
                        } else {
                            Color(0xFFF6F7FB).copy(alpha = 0.78f)
                        },
                        grainAlpha = if (isDark) 0.06f else 0.08f,
                        fallbackColor = MiuixTheme.colorScheme.surfaceContainer
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                slots.forEachIndexed { slotIdx, slot ->
                    if (slot.tabIndex == null) {
                        // Emphasized centered '+' action button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSources[slotIdx],
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onCenterAddClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 50.dp, height = 44.dp)
                                    .squircleBackground(
                                        color = MiuixTheme.colorScheme.primary,
                                        cornerRadius = 22.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = slot.icon,
                                    contentDescription = "새 알바 등록 및 즉시 출근",
                                    tint = MiuixTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        val selected = slotIdx == anchorSlot
                        val tint = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSources[slotIdx],
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelectTab(slot.tabIndex) }
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = slot.icon,
                                    contentDescription = slot.label,
                                    modifier = Modifier.size(22.dp),
                                    tint = tint
                                )
                            }
                            Text(
                                text = slot.label,
                                color = tint,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Layer 2 (on top of nav items, sibling of dockBackdrop):
        // Sliding Liquid Glass Selector Lens that refracts the dock icons/labels underneath
        if (sliderWidthPx > 0f && sliderHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val w = (sliderWidthPx + 2f * pressOutsetPx).roundToInt().coerceAtLeast(0)
                        val h = (sliderHeightPx + 2f * pressOutsetPx).roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(ComposeConstraints.fixed(w, h))
                        val reportW = w.coerceIn(constraints.minWidth, constraints.maxWidth)
                        val reportH = h.coerceIn(constraints.minHeight, constraints.maxHeight)
                        layout(reportW, reportH) {
                            placeable.place(
                                x = (sliderLeftPx - pressOutsetPx).roundToInt(),
                                y = (insetPx - pressOutsetPx).roundToInt()
                            )
                        }
                    }
                    .graphicsLayer {
                        val stretch = stretchOf(sliderLeftPx, anchorPx, itemWidthPx)
                        scaleX = 1f + SliderStretchMax * stretch
                    }
                    .liquidGlassSurface(
                        backdrop = dockBackdrop,
                        shape = RoundedCornerShape(50),
                        blurRadius = if (draggingState.value) 1.5.dp else 0.8.dp,
                        refractionHeight = SliderRefractionHeight,
                        refractionAmount = SliderRefractionAmount,
                        chromaticAberration = SliderChromaticAberration,
                        refraction = true,
                        surfaceTint = MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.08f),
                        grainAlpha = 0.04f,
                        fallbackColor = MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.16f)
                    )
            )
        }

        // Layer 3 (unrefracted badge overlay above the glass lens so red badges never distort):
        if (hasActiveShift || unpaidCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                slots.forEach { slot ->
                    if (slot.tabIndex == null) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Box(modifier = Modifier.size(22.dp))
                                if (slot.tabIndex == 0 && hasActiveShift) {
                                    Box(modifier = Modifier.offset(x = 5.dp, y = (-3).dp)) {
                                        Badge()
                                    }
                                } else if (slot.tabIndex == 2 && unpaidCount > 0) {
                                    Box(modifier = Modifier.offset(x = 9.dp, y = (-5).dp)) {
                                        Badge { Text(unpaidCount.toString()) }
                                    }
                                }
                            }
                            Text(
                                text = slot.label,
                                color = Color.Transparent,
                                fontSize = 11.sp,
                                fontWeight = if (slot.slotIndex == anchorSlot) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
