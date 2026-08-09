/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updatescheck

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val MIN_CHECKING_DURATION_MILLIS = 2_000L

sealed interface UpdatesCheckState {
    data object Idle : UpdatesCheckState
    data object Checking : UpdatesCheckState
    data object NoInternet : UpdatesCheckState
    data object Error : UpdatesCheckState
}

data class UpdatesCheckModel(
    val state: UpdatesCheckState,
    val lastCheckedTimestamp: Long,
    val canCheckForUpdates: Boolean,
)

class UpdatesCheckUiState internal constructor(
    internal val displayedState: UpdatesCheckState,
) {
    val isStatusVisible = when (displayedState) {
        UpdatesCheckState.Idle -> false
        UpdatesCheckState.Checking,
        UpdatesCheckState.NoInternet,
        UpdatesCheckState.Error -> true
    }
}

/**
 * Keeps the checking state visible long enough for the progress animation to be readable.
 */
@Composable
private fun rememberStateWithMinimumCheckingDuration(
    state: UpdatesCheckState,
): UpdatesCheckState {
    var displayedState by remember { mutableStateOf(state) }
    var checkingStartedAtMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        if (state == UpdatesCheckState.Checking) {
            checkingStartedAtMillis = SystemClock.elapsedRealtime()
            displayedState = state
            return@LaunchedEffect
        }

        if (displayedState == UpdatesCheckState.Checking) {
            val elapsed = SystemClock.elapsedRealtime() - checkingStartedAtMillis
            val remaining = MIN_CHECKING_DURATION_MILLIS - elapsed
            if (remaining > 0L) delay(remaining)
        }

        displayedState = state
    }

    return displayedState
}

@Composable
internal fun rememberUpdatesCheckUiState(
    state: UpdatesCheckState,
): UpdatesCheckUiState {
    val displayedState = rememberStateWithMinimumCheckingDuration(state)
    return remember(displayedState) { UpdatesCheckUiState(displayedState) }
}
