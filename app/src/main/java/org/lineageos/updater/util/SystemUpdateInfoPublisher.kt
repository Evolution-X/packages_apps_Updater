/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.util

import android.content.Context
import android.os.PersistableBundle
import android.os.SystemUpdateManager
import android.util.Log
import java.util.concurrent.Executors
import org.lineageos.updater.R
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.data.source.local.UpdatesLocalDataSource
import org.lineageos.updater.deviceinfo.DeviceInfoUtils

/**
 * Reports the most advanced pending update to [SystemUpdateManager], where Settings reads the
 * system update status from. The reported info is dropped by the platform on every boot.
 */
class SystemUpdateInfoPublisher(
    private val context: Context,
    private val localDataSource: UpdatesLocalDataSource,
) {
    private data class Info(val status: Int, val title: String?, val securityPatchLevel: String?)

    private val executor = Executors.newSingleThreadExecutor()

    private var lastInfo: Info? = null

    fun publish() {
        executor.execute(::publishNow)
    }

    private fun publishNow() {
        // Only the controller knows the live status; the database keeps persistent ones.
        val liveUpdates =
            UpdaterController.peekInstance()?.updates.orEmpty().associateBy { it.downloadId }
        val updates = localDataSource.getUpdates().associateBy { it.downloadId } + liveUpdates

        val pendingUpdate =
            updates.values
                .filter {
                    it.timestamp > DeviceInfoUtils.buildDateTimestamp &&
                        it.systemUpdateStatus != SystemUpdateManager.STATUS_IDLE
                }
                .maxByOrNull { it.systemUpdateStatus }

        val info =
            Info(
                status = pendingUpdate?.systemUpdateStatus ?: SystemUpdateManager.STATUS_IDLE,
                title =
                    pendingUpdate?.let {
                        context.getString(R.string.list_build_version, it.version)
                    },
                securityPatchLevel = pendingUpdate?.osPatchLevel,
            )
        if (info == lastInfo) {
            return
        }

        val systemUpdateManager = context.getSystemService(SystemUpdateManager::class.java)
        if (systemUpdateManager == null) {
            Log.w(TAG, "SystemUpdateManager is not available")
            return
        }

        val bundle =
            PersistableBundle().apply {
                putInt(SystemUpdateManager.KEY_STATUS, info.status)
                info.title?.let { putString(SystemUpdateManager.KEY_TITLE, it) }
                info.securityPatchLevel?.let {
                    putString(SystemUpdateManager.KEY_TARGET_SECURITY_PATCH_LEVEL, it)
                }
            }
        try {
            systemUpdateManager.updateSystemUpdateInfo(bundle)
            lastInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish system update info", e)
        }
    }

    private val Update.systemUpdateStatus: Int
        get() =
            when (status) {
                UpdateStatus.UPDATED_NEED_REBOOT -> SystemUpdateManager.STATUS_WAITING_REBOOT

                UpdateStatus.STARTING,
                UpdateStatus.DOWNLOADING,
                UpdateStatus.VERIFYING,
                UpdateStatus.INSTALLING,
                UpdateStatus.INSTALLATION_SUSPENDED -> SystemUpdateManager.STATUS_IN_PROGRESS

                UpdateStatus.VERIFIED,
                UpdateStatus.INSTALLATION_FAILED,
                UpdateStatus.INSTALLATION_CANCELLED -> SystemUpdateManager.STATUS_WAITING_INSTALL

                UpdateStatus.DELETED ->
                    if (isAvailableOnline) {
                        SystemUpdateManager.STATUS_WAITING_DOWNLOAD
                    } else {
                        SystemUpdateManager.STATUS_IDLE
                    }

                UpdateStatus.UNKNOWN,
                UpdateStatus.UPDATE_AVAILABLE,
                UpdateStatus.PAUSED,
                UpdateStatus.PAUSED_ERROR,
                UpdateStatus.VERIFICATION_FAILED -> SystemUpdateManager.STATUS_WAITING_DOWNLOAD
            }

    private companion object {
        const val TAG = "SystemUpdateInfoPublisher"
    }
}
