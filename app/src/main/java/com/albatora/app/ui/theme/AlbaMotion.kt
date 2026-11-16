package com.albatora.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Unified iOS-inspired fluid spring motion specs.
 * Spatial motion uses lightly damped springs for smooth, lively feel without over-bouncing;
 * opacity transitions use short linear/eased fades.
 */
object AlbaMotion {
    val FluidSpring = spring<Float>(
        dampingRatio = 0.74f,
        stiffness = 165f
    )

    val InteractiveSpring = spring<Float>(
        dampingRatio = 0.70f,
        stiffness = 210f
    )

    val SettleSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 185f
    )

    val OffsetSpring = spring<IntOffset>(
        dampingRatio = 0.75f,
        stiffness = 165f
    )

    val ExitOffsetSpring = spring<IntOffset>(
        dampingRatio = 0.84f,
        stiffness = 185f
    )

    val LayoutSizeSpring = spring<IntSize>(
        dampingRatio = 0.76f,
        stiffness = 160f
    )

    val ExitLayoutSizeSpring = spring<IntSize>(
        dampingRatio = 0.84f,
        stiffness = 180f
    )

    val OdometerSpring = spring<IntOffset>(
        dampingRatio = 0.74f,
        stiffness = 200f
    )

    fun expandEnter(): EnterTransition =
        expandVertically(animationSpec = LayoutSizeSpring) + fadeIn(tween(320))

    fun shrinkExit(): ExitTransition =
        shrinkVertically(animationSpec = ExitLayoutSizeSpring) + fadeOut(tween(240))

    fun tabTransition(forward: Boolean, slideDivisor: Int = 12): ContentTransform {
        val enterOffset: (Int) -> Int = { full -> if (forward) full / slideDivisor else -full / slideDivisor }
        val exitOffset: (Int) -> Int = { full -> if (forward) -full / slideDivisor else full / slideDivisor }
        return (
            slideInHorizontally(animationSpec = OffsetSpring, initialOffsetX = enterOffset) +
                fadeIn(tween(320)) +
                scaleIn(initialScale = 0.98f, animationSpec = FluidSpring)
            ).togetherWith(
            slideOutHorizontally(animationSpec = ExitOffsetSpring, targetOffsetX = exitOffset) +
                fadeOut(tween(230)) +
                scaleOut(targetScale = 0.98f, animationSpec = SettleSpring)
        )
    }
}
