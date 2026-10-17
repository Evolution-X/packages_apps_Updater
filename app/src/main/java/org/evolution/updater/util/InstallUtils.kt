/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.updater.util

import org.evolution.updater.data.Update
import org.evolution.updater.deviceinfo.DeviceInfoUtils
import java.io.File

object InstallUtils {
    @JvmStatic
    fun isScratchMounted() = runCatching {
        File("/proc/mounts").useLines { lines ->
            lines.any { it.split(" ")[1] == "/mnt/scratch" }
        }
    }.getOrDefault(false)

    enum class BlockedReason {
        NONE, DOWNGRADE, VERSION_UNSUPPORTED
    }

    @JvmStatic
    fun getBlockedReason(update: Update) = when {
        !DeviceInfoUtils.isDowngradingAllowed &&
                (update.timestamp < DeviceInfoUtils.buildDateTimestamp ||
                        update.osSdkLevel < DeviceInfoUtils.sdkLevel) -> BlockedReason.DOWNGRADE

        !DeviceInfoUtils.isMajorUpdateAllowed &&
                update.osSdkLevel > DeviceInfoUtils.sdkLevel -> BlockedReason.VERSION_UNSUPPORTED

        else -> BlockedReason.NONE
    }

    @JvmStatic
    fun canInstall(update: Update) = getBlockedReason(update) == BlockedReason.NONE

    @JvmStatic
    fun canStreamUpdate(update: Update, streamUpdatesEnabled: Boolean) =
        DeviceInfoUtils.isABDevice &&
                streamUpdatesEnabled &&
                update.isAvailableOnline &&
                update.hasPayloadFileRanges() &&
                !update.hasFullyDownloadedPackage() &&
                !update.hasVerifiedPackage()
}
