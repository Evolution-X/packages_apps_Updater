/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.updater

import android.app.Application
import com.android.settingslib.spa.framework.common.SettingsPageProviderRepository
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import kotlinx.coroutines.MainScope
import org.evolution.updater.data.AppStateRepository
import org.evolution.updater.data.ChangelogRepository
import org.evolution.updater.data.UpdatesRepository
import org.evolution.updater.data.UserPreferencesRepository
import org.evolution.updater.data.source.local.UpdatesDatabase
import org.evolution.updater.data.source.local.UpdatesLocalDataSource
import org.evolution.updater.data.source.network.UpdatesNetworkDataSource
import org.evolution.updater.notifications.NotificationHelper
import org.evolution.updater.util.BatteryMonitor
import org.evolution.updater.util.NetworkMonitor
import org.evolution.updater.util.SystemUpdateInfoPublisher

class UpdaterApplication : Application() {
    private val coroutineScope = MainScope()
    private val database by lazy { UpdatesDatabase.getInstance(applicationContext) }
    private val networkDataSource by lazy { UpdatesNetworkDataSource(applicationContext) }
    private val localDataSource by lazy { UpdatesLocalDataSource(database.updateDao()) }


    val batteryMonitor by lazy {
        BatteryMonitor(applicationContext, coroutineScope, userPreferencesRepository)
    }
    val networkMonitor by lazy { NetworkMonitor(applicationContext, coroutineScope) }
    val notificationHelper by lazy { NotificationHelper(applicationContext) }
    val appStateRepository by lazy { AppStateRepository(applicationContext) }
    val changelogRepository by lazy { ChangelogRepository(applicationContext) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(applicationContext) }
    val systemUpdateInfoPublisher by lazy {
        SystemUpdateInfoPublisher(applicationContext, localDataSource)
    }
    val updatesRepository by lazy {
        UpdatesRepository(
            networkMonitor = networkMonitor,
            notificationHelper = notificationHelper,
            networkDataSource = networkDataSource,
            localDataSource = localDataSource,
            systemUpdateInfoPublisher = systemUpdateInfoPublisher,
        )
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.setUpNotificationChannels()
        SpaEnvironmentFactory.reset(object : SpaEnvironment(applicationContext) {
            override val pageProviderRepository = lazy {
                SettingsPageProviderRepository(emptyList())
            }

            override val isSpaExpressiveEnabled = true
        })
    }
}
