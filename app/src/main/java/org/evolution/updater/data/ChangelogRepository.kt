/*
 * SPDX-FileCopyrightText: 2026 PixelOS
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.updater.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.evolution.updater.R
import org.evolution.updater.deviceinfo.DeviceInfoUtils

sealed interface ChangelogState {
    data object Idle : ChangelogState

    data object Loading : ChangelogState

    data class Loaded(val markdown: String) : ChangelogState

    data object Error : ChangelogState
}

/** Fetches the plain text changelog the OTA server publishes for the device. */
class ChangelogRepository(private val context: Context) {
    private val client =
        OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()

    @Volatile private var cachedKey: String? = null

    @Volatile private var cachedMarkdown: String? = null

    suspend fun fetchChangelog(update: Update): String =
        withContext(Dispatchers.IO) {
            val device = DeviceInfoUtils.device
            require(device.isNotBlank()) { "Missing ro.evolution.device" }

            val key = "$device/${update.downloadId}"
            if (cachedKey == key) {
                cachedMarkdown?.let {
                    return@withContext it
                }
            }

            val url = context.getString(R.string.menu_changelog_url, device)
            val lines = fetch(url).trimEnd().lines()
            val markdown =
                toMarkdown(
                    lines = lines.take(MAX_LINES),
                    truncated = lines.size > MAX_LINES,
                    url = url,
                )
            cachedKey = key
            cachedMarkdown = markdown
            markdown
        }

    private fun fetch(url: String): String {
        require(url.startsWith("https://")) { "Changelog URL must use HTTPS" }

        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected changelog HTTP status: ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty changelog response")
            val contentLength = body.contentLength()
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IOException("Changelog response is too large: $contentLength bytes")
            }
            val bytes = body.source().readByteArray(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) {
                throw IOException("Changelog response exceeds $MAX_RESPONSE_BYTES bytes")
            }
            bytes.decodeToString()
        }
    }

    // The changelog is plain text, so keep its line breaks and don't let Markdown reinterpret it.
    private fun toMarkdown(lines: List<String>, truncated: Boolean, url: String): String =
        buildString {
                lines.forEach { append(it.trimEnd().escapeMarkdown()).append("  \n") }

                if (truncated) {
                    append("\n[")
                        .append(context.getString(R.string.changelog_view_all))
                        .append("](")
                        .append(url)
                        .append(")\n")
                }
            }
            .trim()

    private fun String.escapeMarkdown() = buildString {
        this@escapeMarkdown.forEach {
            if (it in MARKDOWN_SPECIAL_CHARS) {
                append('\\')
            }
            append(it)
        }
    }

    private companion object {
        const val MAX_LINES = 200
        const val MAX_RESPONSE_BYTES = 256L * 1024L

        const val MARKDOWN_SPECIAL_CHARS = "\\`*_{}[]()<>#+-.!|~="
    }
}
