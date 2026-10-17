/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.updater.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.evolution.updater.deviceinfo.DeviceInfoUtils
import kotlin.time.Duration.Companion.seconds

private const val APP_STATE_DATASTORE_NAME = "app_state"

private val Context.appStatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_STATE_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

private object AppStatePreferencesKeys {
    val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
    val SUPPORT_FIRMWARE = stringPreferencesKey("support_firmware")
    val SUPPORT_FORUM = stringPreferencesKey("support_forum")
    val SUPPORT_MAINTAINER = stringPreferencesKey("support_maintainer")
    val SUPPORT_PAYPAL = stringPreferencesKey("support_paypal")
}

class AppStateRepository(context: Context) {
    private val appState = context.applicationContext.appStatePreferencesDataStore

    val lastCheckedTimestampFlow: Flow<Long> = appState.data.map { preferences ->
        preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP]
            ?: DeviceInfoUtils.buildDateTimestamp.seconds.inWholeMilliseconds
    }

    suspend fun setLastCheckedTimestamp(timestamp: Long) {
        appState.edit { preferences ->
            preferences[AppStatePreferencesKeys.LAST_CHECKED_TIMESTAMP] = timestamp
        }
    }

    val supportInfoFlow: Flow<SupportInfo> = appState.data.map { preferences ->
        SupportInfo(
            maintainer = preferences[AppStatePreferencesKeys.SUPPORT_MAINTAINER],
            forum = preferences[AppStatePreferencesKeys.SUPPORT_FORUM],
            firmware = preferences[AppStatePreferencesKeys.SUPPORT_FIRMWARE],
            paypal = preferences[AppStatePreferencesKeys.SUPPORT_PAYPAL],
        )
    }

    suspend fun setSupportInfo(supportInfo: SupportInfo) {
        appState.edit { preferences ->
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_MAINTAINER, supportInfo.maintainer)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_FORUM, supportInfo.forum)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_FIRMWARE, supportInfo.firmware)
            preferences.setOrRemove(AppStatePreferencesKeys.SUPPORT_PAYPAL, supportInfo.paypal)
        }
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value != null) {
            this[key] = value
        } else {
            remove(key)
        }
    }
}
