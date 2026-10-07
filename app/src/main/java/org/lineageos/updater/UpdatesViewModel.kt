/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lineageos.updater.data.ChangelogState
import org.lineageos.updater.data.Update
import org.lineageos.updater.updatescheck.UpdatesCheckModel
import org.lineageos.updater.updatescheck.UpdatesCheckState

private const val TAG = "UpdatesViewModel"

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    data class UiState(
        val updates: List<Update> = emptyList(),
        val isCheckingForUpdates: Boolean = false,
        val isOnline: Boolean = true,
        val lastCheckedTimestamp: Long = 0L,
        val hasUpdateCheckFailed: Boolean = false,
        val changelogState: ChangelogState = ChangelogState.Idle,
        val changelogUpdateId: String? = null,
    ) {
        val updatesCheckModel = UpdatesCheckModel(
            state = when {
                isCheckingForUpdates -> UpdatesCheckState.Checking
                !isOnline -> UpdatesCheckState.NoInternet
                hasUpdateCheckFailed -> UpdatesCheckState.Error
                else -> UpdatesCheckState.Idle
            },
            lastCheckedTimestamp = lastCheckedTimestamp,
            canCheckForUpdates = isOnline && !isCheckingForUpdates,
        )
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UiState> = _uiState.asLiveData()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val appStateRepository = updaterApplication.appStateRepository
    private val changelogRepository = updaterApplication.changelogRepository
    private val networkMonitor = updaterApplication.networkMonitor
    private var changelogJob: Job? = null

    init {
        viewModelScope.launch {
            appStateRepository.lastCheckedTimestampFlow.collect { ts ->
                _uiState.update { it.copy(lastCheckedTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                _uiState.update { it.copy(updates = updates) }
                loadChangelog(updates)
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .collect { networkState ->
                    _uiState.update { it.copy(isOnline = networkState.isOnline) }
                }
        }
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    hasUpdateCheckFailed = false,
                )
            }
            try {
                val fetchedAt = repository.fetchUpdates()
                fetchedAt?.let { fetchedAt ->
                    appStateRepository.setLastCheckedTimestamp(fetchedAt)
                }
                _uiState.update { it.copy(isCheckingForUpdates = false) }
                loadChangelog(_uiState.value.updates)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        hasUpdateCheckFailed = true,
                    )
                }
            }
        }
    }

    private fun loadChangelog(updates: List<Update>) {
        // Updates are sorted newest first, so this is the update the screen offers.
        val update = updates.firstOrNull { it.isAvailableOnline } ?: return
        val state = _uiState.value
        if (state.changelogUpdateId == update.downloadId &&
            (changelogJob?.isActive == true || state.changelogState is ChangelogState.Loaded)
        ) {
            return
        }
        changelogJob?.cancel()
        changelogJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    changelogState = ChangelogState.Loading,
                    changelogUpdateId = update.downloadId,
                )
            }
            val changelogState = try {
                ChangelogState.Loaded(changelogRepository.fetchChangelog(update))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch changelog", e)
                ChangelogState.Error
            }
            _uiState.update { it.copy(changelogState = changelogState) }
        }
    }
}
