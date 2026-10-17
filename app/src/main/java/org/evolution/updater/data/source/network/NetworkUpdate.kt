/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.evolution.updater.data.source.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.evolution.updater.data.Update
import org.evolution.updater.deviceinfo.DeviceInfoUtils
import org.evolution.updater.misc.Constants

// Commented out fields below are available in production, but not needed in runtime.
// If you wish to uncomment any of them, please update the README.md to indicate that.

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdates(
    @SerialName("response") val response: List<NetworkUpdate>,
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    @SerialName("buildtype") val buildType: String? = null,
    // @SerialName("device") val device: String,
    @SerialName("download") val download: String,
    @SerialName("filename") val filename: String,
    // @SerialName("md5") val md5: String,
    // @SerialName("oem") val oem: String,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("version") val version: String,
)

private data class PackageFileRange(
    val offset: Long,
    val size: Long,
)

private fun String.parsePackageFileRanges() =
    split(",").associate { token ->
        val parts = token.trim().split(":")
        parts[0].trim() to PackageFileRange(
            offset = parts[1].trim().toLong(),
            size = parts[2].trim().toLong(),
        )
    }

fun NetworkUpdate.toUpdate(): Update {
    val packageFileRanges = otaPropertyFiles?.parsePackageFileRanges().orEmpty()
    val payloadMetadataRange = packageFileRanges[Constants.AB_PAYLOAD_METADATA_PATH]
    val payloadRange = packageFileRanges[Constants.AB_PAYLOAD_BIN_PATH]
    val payloadPropertiesRange = packageFileRanges[Constants.AB_PAYLOAD_PROPERTIES_PATH]

    return Update(
        downloadId = sha256,
        name = filename,
        timestamp = timestamp,
        type = buildType,
        fileSize = size,
        downloadUrl = download,
        version = version,
        osPatchLevel = osPatchLevel,
        // Evolution X builds don't advertise their SDK level; assume the running one so they
        // aren't treated as Android downgrades.
        osSdkLevel = osSdkLevel ?: DeviceInfoUtils.sdkLevel,
        payloadMetadataOffset = payloadMetadataRange?.offset,
        payloadMetadataSize = payloadMetadataRange?.size,
        payloadOffset = payloadRange?.offset,
        payloadSize = payloadRange?.size,
        payloadPropertiesOffset = payloadPropertiesRange?.offset,
        payloadPropertiesSize = payloadPropertiesRange?.size,
        isAvailableOnline = true,
    )
}
