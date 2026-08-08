/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.isActive

private val retainedProgress = mutableMapOf<LottieCompositionSpec, Float>()

@Composable
fun Lottie(
    resId: Int,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
) {
    Lottie(
        spec = LottieCompositionSpec.RawRes(resId),
        modifier = modifier,
        iterations = iterations,
    )
}

@Composable
fun Lottie(
    spec: LottieCompositionSpec,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
) {
    val composition by rememberLottieComposition(spec)
    var progress by remember(spec) {
        mutableFloatStateOf(retainedProgress[spec] ?: 0f)
    }

    LaunchedEffect(composition, iterations) {
        val comp = composition ?: return@LaunchedEffect
        val duration = comp.duration.coerceAtLeast(1f)
        var anchorNanos = -1L

        while (isActive) {
            val elapsed = withFrameNanos { now ->
                if (anchorNanos < 0) {
                    anchorNanos = now - (progress * duration * 1_000_000f).toLong()
                }
                (now - anchorNanos) / 1_000_000f
            }

            progress = elapsed % duration / duration
            retainedProgress[spec] = progress

            if (iterations != LottieConstants.IterateForever &&
                elapsed / duration >= iterations
            ) {
                break
            }
        }
    }

    LottieAnimation(
        composition = composition,
        modifier = modifier,
        dynamicProperties = LottieColorUtils.getDefaultDynamicProperties(),
        progress = { progress },
    )
}
