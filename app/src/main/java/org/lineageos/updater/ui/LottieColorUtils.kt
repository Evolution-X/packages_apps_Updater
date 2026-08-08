/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

internal object LottieColorUtils {
    /**
     * Tints every layer named [keyPathName] with [color]. Layer names follow
     * the dynamic-color convention used by system animations, e.g. ".primary".
     */
    @Composable
    private fun createColorFilter(
        keyPathName: String,
        color: Color,
    ): LottieDynamicProperty<ColorFilter> = rememberLottieDynamicProperty(
        property = LottieProperty.COLOR_FILTER,
        keyPath = arrayOf("**", keyPathName, "**"),
    ) {
        PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
    }

    @Composable
    fun getDefaultDynamicProperties(): LottieDynamicProperties {
        val colorScheme = MaterialTheme.colorScheme
        return rememberLottieDynamicProperties(
            createColorFilter(".onSurface", colorScheme.onSurface),
            createColorFilter(".primary", colorScheme.primary),
            createColorFilter(".secondaryContainer", colorScheme.secondaryContainer),
            createColorFilter(".onSecondaryContainer", colorScheme.onSecondaryContainer),
        )
    }
}
