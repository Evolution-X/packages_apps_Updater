/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsTheme

@Composable
fun UpdateCheckAnimation(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier,
        strokeWidth = 6.dp,
    )
}

@UiModePreviews
@Composable
private fun UpdateCheckAnimationPreview() {
    SettingsTheme {
        UpdateCheckAnimation()
    }
}
