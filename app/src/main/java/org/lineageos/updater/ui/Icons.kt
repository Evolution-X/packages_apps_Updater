/*
 * SPDX-FileCopyrightText: Material Design Authors / Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private val SystemUpdatePathData = addPathNodes(
    "M17,1.01L7,1C5.9,1 5,1.9 5,3v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3C19,1.9 " +
        "18.1,1.01 17,1.01zM17,21H7v-1h10V21zM17,18H7V6h10V18zM7,4V3h10v1H7zM16,12l-4,4l-4," +
        "-4l1.41,-1.41L11,12.17V8h2v4.17l1.59,-1.59L16,12z",
)

internal val SystemUpdateIcon: ImageVector = ImageVector.Builder(
    name = "SystemUpdate",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = SystemUpdatePathData,
    fill = SolidColor(Color.Black),
).build()
