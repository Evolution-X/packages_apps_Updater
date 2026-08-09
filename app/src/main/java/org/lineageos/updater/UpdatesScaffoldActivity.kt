/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.framework.compose.LocalNavController
import com.android.settingslib.spa.framework.compose.NavControllerWrapper
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.controller.UpdaterController
import org.lineageos.updater.data.Update
import org.lineageos.updater.data.UpdateStatus
import org.lineageos.updater.preferences.PreferencesActivity
import org.lineageos.updater.ui.SystemUpdateScreen
import org.lineageos.updater.updates.action.AlertDialogState
import org.lineageos.updater.updates.action.UpdateActionDialog
import org.lineageos.updater.updates.action.UpdateActionHandler
import org.lineageos.updater.updates.state.UpdateItemStateMapper
import org.lineageos.updater.updatescheck.UpdatesCheckState
import org.lineageos.updater.updatescheck.rememberUpdatesCheckUiState

abstract class UpdatesScaffoldActivity : ComponentActivity() {
    private val viewModel by viewModels<UpdatesViewModel>()
    private var activeUpdaterController: UpdaterController? by mutableStateOf(null)
    private var controllerStateVersion: Int by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun setupCompose() {
        setContent {
            val navController = remember {
                object : NavControllerWrapper {
                    override fun navigate(route: String, popUpCurrent: Boolean) {}
                    override fun navigateBack() = finish()
                }
            }

            CompositionLocalProvider(LocalNavController provides navController) {
                SettingsTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    UpdatesScaffoldContent(
                        uiState = uiState,
                        updaterController = activeUpdaterController,
                        controllerStateVersion = controllerStateVersion,
                        onBackClick = { finish() },
                        onRefreshClick = { onRefreshClick() },
                        onLocalUpdateClick = { onLocalUpdateClick() },
                        onPreferencesClick = {
                            startActivity(
                                Intent(
                                    this@UpdatesScaffoldActivity,
                                    PreferencesActivity::class.java,
                                )
                            )
                        },
                        onControllerStateChanged = { notifyControllerStateChanged() },
                    )
                }
            }
        }
    }

    protected fun setUpdaterController(controller: UpdaterController?) {
        activeUpdaterController = controller
        notifyControllerStateChanged()
    }

    protected fun notifyControllerStateChanged() {
        controllerStateVersion++
    }

    open fun onRefreshClick() {}
    open fun onLocalUpdateClick() {}
    open fun exportUpdate(update: Update) {}
}

@Composable
private fun UpdatesScaffoldContent(
    uiState: UpdatesViewModel.UiState,
    updaterController: UpdaterController?,
    controllerStateVersion: Int,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onControllerStateChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as UpdatesScaffoldActivity
    val networkMonitor =
        remember { (context.applicationContext as UpdaterApplication).networkMonitor }
    val networkState by networkMonitor.networkState.collectAsState(
        initial = networkMonitor.currentNetworkState,
    )
    val userPreferencesRepository =
        remember { (context.applicationContext as UpdaterApplication).userPreferencesRepository }
    val streamUpdatesEnabled by userPreferencesRepository.streamUpdatesFlow.collectAsState(
        initial = true,
    )

    val updateItems = remember(
        uiState.updates,
        updaterController,
        networkState,
        streamUpdatesEnabled,
        controllerStateVersion,
    ) {
        val controller = updaterController ?: return@remember emptyList()
        val mapper = UpdateItemStateMapper(context, controller, streamUpdatesEnabled)
        uiState.updates.mapNotNull { update ->
            controller.getUpdate(update.downloadId)?.let {
                mapper.map(it, networkState)
            }
        }
    }

    val actionDialogState = remember { mutableStateOf<AlertDialogState?>(null) }
    actionDialogState.value?.let { dialog ->
        UpdateActionDialog(
            dialog = dialog,
            onDismiss = { actionDialogState.value = null },
        )
    }

    val actionHandler = remember(updaterController) {
        updaterController?.let { controller ->
            UpdateActionHandler(
                activity = activity,
                updaterController = controller,
                exportUpdate = { update -> activity.exportUpdate(update) },
                showDialog = { actionDialogState.value = it },
            )
        }
    }

    val model = uiState.updatesCheckModel
    val checkUiState = rememberUpdatesCheckUiState(model.state)
    val isChecking = checkUiState.displayedState is UpdatesCheckState.Checking
    val isPreparing = uiState.updates.any { it.status == UpdateStatus.STARTING }
    val isBusy = isChecking || isPreparing
    val isIdleAndEmpty = updateItems.isEmpty() && !isBusy

    val activeItem = updateItems.firstOrNull { it.progress != null } ?: updateItems.firstOrNull()

    SystemUpdateScreen(
        headline = getHeadline(
            updates = uiState.updates,
            displayedCheckState = checkUiState.displayedState,
            isPreparing = isPreparing,
            hasUpdateItems = updateItems.isNotEmpty(),
        ),
        supportingText = when (checkUiState.displayedState) {
            UpdatesCheckState.NoInternet ->
                stringResource(R.string.check_your_internet_connection)

            UpdatesCheckState.Error -> stringResource(R.string.updates_check_failed)
            else -> null
        },
        supportingTextIsError = checkUiState.displayedState is UpdatesCheckState.NoInternet ||
                checkUiState.displayedState is UpdatesCheckState.Error,
        isBusy = isBusy,
        canCheckForUpdates = model.canCheckForUpdates,
        showDeviceInfo = isIdleAndEmpty,
        lastCheckedTimestamp = if (isIdleAndEmpty) model.lastCheckedTimestamp else 0L,
        onBackClick = onBackClick,
        onCheckClick = onRefreshClick,
        onLocalUpdateClick = onLocalUpdateClick,
        onPreferencesClick = onPreferencesClick,
        updateItem = if (isBusy) null else activeItem,
        onUpdateAction = { action ->
            val item = activeItem
            val controller = updaterController
            if (item != null && controller != null) {
                controller.getUpdate(item.downloadId)?.let { update ->
                    actionHandler?.perform(action, update)
                    onControllerStateChanged()
                }
            }
        },
    )
}

@Composable
private fun getHeadline(
    updates: List<Update>,
    displayedCheckState: UpdatesCheckState,
    isPreparing: Boolean,
    hasUpdateItems: Boolean,
): String = when {
    updates.any { it.status == UpdateStatus.UPDATED_NEED_REBOOT } ->
        stringResource(R.string.installing_update_finished)

    updates.any { it.status == UpdateStatus.INSTALLATION_FAILED } ->
        stringResource(R.string.installing_update_error)

    updates.any { it.status == UpdateStatus.INSTALLING } ->
        stringResource(R.string.installing_update_title)

    isPreparing -> stringResource(R.string.preparing_update_title)

    displayedCheckState is UpdatesCheckState.Checking ->
        stringResource(R.string.checking_for_update_title)

    displayedCheckState is UpdatesCheckState.NoInternet ||
            displayedCheckState is UpdatesCheckState.Error ->
        stringResource(R.string.updates_check_failed_title)

    updates.any { it.status == UpdateStatus.DOWNLOADING } ->
        stringResource(R.string.downloading_update_title)

    updates.any { it.status == UpdateStatus.VERIFYING } ->
        stringResource(R.string.verifying_update_title)

    updates.any {
        it.status == UpdateStatus.PAUSED ||
                it.status == UpdateStatus.PAUSED_ERROR ||
                it.status == UpdateStatus.INSTALLATION_SUSPENDED
    } -> stringResource(R.string.update_paused_title)

    hasUpdateItems -> stringResource(R.string.update_available_title)

    else -> stringResource(R.string.system_up_to_date_title)
}
