/*
 * SPDX-FileCopyrightText: 2026 PixelOS
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.lineageos.updater.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.updater.R
import org.lineageos.updater.deviceinfo.DeviceInfoUtils

sealed interface ChangelogState {
    data object Idle : ChangelogState

    data object Loading : ChangelogState

    data class Loaded(val markdown: String) : ChangelogState

    data object Error : ChangelogState
}

/**
 * Builds the changelog of an update from the changes merged after the running build, filtered the
 * same way as the changes page of the LineageOS download portal.
 */
class ChangelogRepository(private val context: Context) {
    private val client =
        OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()

    @Volatile private var cachedKey: String? = null

    @Volatile private var cachedMarkdown: String? = null

    suspend fun fetchChangelog(update: Update): String =
        withContext(Dispatchers.IO) {
            val device = DeviceInfoUtils.device
            require(device.isNotBlank()) { "Missing ro.lineage.device" }

            val key = "$device/${update.downloadId}"
            if (cachedKey == key) {
                cachedMarkdown?.let {
                    return@withContext it
                }
            }

            val dependencies =
                fetch<NetworkDevice>(
                        context.getString(R.string.device_api_url).replace("{device}", device)
                    )
                    .dependencies
                    .toSet()
            val since = DeviceInfoUtils.buildDateTimestamp

            val changes = mutableListOf<NetworkChange>()
            var complete = false
            for (page in 0 until MAX_PAGES) {
                val pageChanges = fetch<List<NetworkChange>>(changesUrl(page))
                changes += pageChanges.filter { it.isPartOf(update, since, dependencies) }
                // A change is never updated before it is submitted, and pages are sorted by update.
                if (pageChanges.isEmpty() || pageChanges.minOf { it.updated } <= since) {
                    complete = true
                    break
                }
            }

            val markdown =
                toMarkdown(
                    changes = changes,
                    truncated = !complete || changes.size > MAX_CHANGES,
                    device = device,
                )
            cachedKey = key
            cachedMarkdown = markdown
            markdown
        }

    private fun changesUrl(page: Int): String {
        val url = context.getString(R.string.changes_api_url)
        require(url.startsWith("https://")) { "Changes URL must use HTTPS" }
        return url.toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
            .toString()
    }

    private inline fun <reified T> fetch(url: String): T {
        require(url.startsWith("https://")) { "Changelog URL must use HTTPS" }

        val body =
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
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

        return Json.decodeFromString<T>(body)
    }

    private fun NetworkChange.isPartOf(
        update: Update,
        since: Long,
        dependencies: Set<String>,
    ): Boolean {
        val submitted = submitted ?: return false
        if (submitted <= since || submitted > update.timestamp) return false
        if (update.version !in branch || '/' in branch) return false
        if ("android_" !in project) return false

        return when (type) {
            TYPE_DEVICE_SPECIFIC -> repository in dependencies
            TYPE_PLATFORM -> true
            else -> false
        }
    }

    private fun toMarkdown(
        changes: List<NetworkChange>,
        truncated: Boolean,
        device: String,
    ): String =
        buildString {
                changes
                    .take(MAX_CHANGES)
                    .groupBy { it.repository }
                    .entries
                    .sortedWith(
                        compareBy({ it.value.first().type != TYPE_DEVICE_SPECIFIC }, { it.key })
                    )
                    .forEach { (repository, repositoryChanges) ->
                        append("**").append(repository.escapeMarkdown()).append("**\n\n")
                        repositoryChanges.forEach {
                            append("- [")
                                .append(it.subject.escapeMarkdown())
                                .append("](")
                                .append(it.url)
                                .append(")\n")
                        }
                        append('\n')
                    }

                if (truncated) {
                    append('[')
                        .append(context.getString(R.string.changelog_view_all))
                        .append("](")
                        .append(context.getString(R.string.menu_changelog_url, device))
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
        const val MAX_CHANGES = 100
        const val MAX_PAGES = 10
        const val MAX_RESPONSE_BYTES = 256L * 1024L

        const val MARKDOWN_SPECIAL_CHARS = "\\`*_{}[]()<>#+-.!|~"

        const val TYPE_DEVICE_SPECIFIC = "device specific"
        const val TYPE_PLATFORM = "platform"
    }
}

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
private data class NetworkDevice(
    @SerialName("dependencies") val dependencies: List<String> = emptyList()
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
private data class NetworkChange(
    @SerialName("branch") val branch: String,
    @SerialName("project") val project: String,
    @SerialName("repository") val repository: String,
    @SerialName("subject") val subject: String,
    @SerialName("submitted") val submitted: Long? = null,
    @SerialName("type") val type: String,
    @SerialName("updated") val updated: Long,
    @SerialName("url") val url: String,
)
