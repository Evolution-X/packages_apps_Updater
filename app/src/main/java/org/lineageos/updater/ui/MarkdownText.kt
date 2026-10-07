/*
 * SPDX-FileCopyrightText: 2026 PixelOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textSize = MaterialTheme.typography.bodyMedium.fontSize.value

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.textSize = textSize
            markwon.setMarkdown(textView, markdown)
        },
    )
}
