/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.lineageos.updater.ui

import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.lineageos.updater.R
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import org.lineageos.updater.updates.action.UpdateAction
import org.lineageos.updater.updates.action.UpdateActionType
import org.lineageos.updater.updates.state.ProgressState
import org.lineageos.updater.updates.state.UpdateItemState
import org.lineageos.updater.util.StringUtil
import java.util.Date

private val ContentMaxWidth = 560.dp
private val HorizontalPadding = 24.dp
private val IconSize = 48.dp
private val ButtonHeight = 56.dp

@Composable
fun SystemUpdateScreen(
    headline: String,
    supportingText: String?,
    supportingTextIsError: Boolean = false,
    isBusy: Boolean,
    canCheckForUpdates: Boolean,
    showDeviceInfo: Boolean,
    lastCheckedTimestamp: Long,
    onBackClick: () -> Unit,
    onCheckClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier,
    updateItem: UpdateItemState? = null,
    onUpdateAction: (UpdateAction) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            SystemUpdateTopBar(
                onBackClick = onBackClick,
                onLocalUpdateClick = onLocalUpdateClick,
                onPreferencesClick = onPreferencesClick,
                updateOverflowActions = updateItem?.actions?.overflow ?: emptyList(),
                onUpdateAction = onUpdateAction,
            )
        },
        bottomBar = {
            if (!isBusy) {
                if (updateItem != null) {
                    UpdateActionButtons(
                        item = updateItem,
                        onAction = onUpdateAction,
                    )
                } else {
                    CheckForUpdateButton(
                        enabled = canCheckForUpdates,
                        onClick = onCheckClick,
                    )
                }
            }
        },
    ) { paddingValues ->
        if (isBusy) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                    ScreenHeader(headline)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    UpdateCheckAnimation()
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth)) {
                ScreenHeader(headline)

                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (supportingTextIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(
                            start = HorizontalPadding,
                            top = 32.dp,
                            end = HorizontalPadding,
                        ),
                    )
                }

                if (showDeviceInfo) {
                    DeviceInfoText(
                        modifier = Modifier.padding(
                            start = HorizontalPadding,
                            top = 32.dp,
                            end = HorizontalPadding,
                        ),
                    )
                }

                if (lastCheckedTimestamp > 0) {
                    LastCheckedText(
                        timestampMillis = lastCheckedTimestamp,
                        modifier = Modifier.padding(
                            start = HorizontalPadding,
                            top = 24.dp,
                            end = HorizontalPadding,
                        ),
                    )
                }

                updateItem?.let { item ->
                    UpdateDetails(
                        item = item,
                        modifier = Modifier.padding(horizontal = HorizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun UpdateDetails(
    item: UpdateItemState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        item.progress?.let { progress ->
            UpdateProgress(progress)
        }

        Text(
            text = "${item.buildVersion} - ${item.buildDate}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 32.dp),
        )

        val showsInstallInfo = item.progress == null &&
                item.actions.primary.type in setOf(
            UpdateActionType.START_DOWNLOAD,
            UpdateActionType.START_INSTALL,
        )
        if (showsInstallInfo) {
            if (item.fileSize.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.list_update_size, item.fileSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.metered_warning_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.UpdateProgress(progress: ProgressState) {
    when (progress) {
        is ProgressState.Determinate -> {
            LinearWavyProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )
            val caption = listOf(progress.downloadedSize, progress.eta)
                .filter { it.isNotEmpty() }
                .joinToString(" • ")
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp),
                )
            }
        }

        ProgressState.Indeterminate -> {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            )
            Text(
                text = stringResource(R.string.downloading_installing_caption),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun UpdateActionButtons(
    item: UpdateItemState,
    onAction: (UpdateAction) -> Unit,
) {
    val context = LocalContext.current
    val primary = item.actions.primary
    val primaryLabel = when (primary.type) {
        UpdateActionType.START_DOWNLOAD -> stringResource(R.string.action_download_install)
        else -> primary.type.title(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item.actions.secondary?.let { secondary ->
            TextButton(
                onClick = { onAction(secondary) },
                enabled = secondary.enabled,
            ) {
                Text(secondary.type.title(context))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Button(
            onClick = { onAction(primary) },
            enabled = primary.enabled,
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .height(ButtonHeight),
            shape = RoundedCornerShape(ButtonHeight / 2),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ScreenHeader(headline: String) {
    Text(
        text = headline,
        style = MaterialTheme.typography.displaySmall,
        fontFamily = DisplaySmallEmphasizedFontFamily,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(
            start = HorizontalPadding,
            top = 20.dp,
            end = HorizontalPadding,
        ),
    )
}

@Composable
private fun SystemUpdateTopBar(
    onBackClick: () -> Unit,
    onLocalUpdateClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    updateOverflowActions: List<UpdateAction> = emptyList(),
    onUpdateAction: (UpdateAction) -> Unit = {},
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = HorizontalPadding, vertical = 8.dp),
    ) {
        FilledTonalIconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_system_update),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(IconSize),
            tint = MaterialTheme.colorScheme.primary,
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            FilledTonalIconButton(
                onClick = { menuExpanded = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.menu_more_options),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.local_update_import)) },
                    onClick = {
                        menuExpanded = false
                        onLocalUpdateClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_preferences)) },
                    onClick = {
                        menuExpanded = false
                        onPreferencesClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.report_issues)) },
                    onClick = {
                        menuExpanded = false
                        val url = context.getString(R.string.report_issue_url)
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                )
                updateOverflowActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.type.title(context)) },
                        enabled = action.enabled,
                        onClick = {
                            menuExpanded = false
                            onUpdateAction(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckForUpdateButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .height(ButtonHeight),
            shape = RoundedCornerShape(ButtonHeight / 2),
        ) {
            Text(
                text = stringResource(R.string.check_for_update),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DeviceInfoText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(context, configuration.locales) { StringUtil.getCurrentLocale(context) }
    val securityPatch = remember(locale) {
        runCatching {
            StringUtil.formatSecurityPatchFullDate(context, DeviceInfoUtils.buildSecurityPatch)
        }.getOrDefault(DeviceInfoUtils.buildSecurityPatch)
    }

    Column(modifier = modifier) {
        InfoLine(stringResource(R.string.header_lineage_version, DeviceInfoUtils.buildVersion))
        InfoLine(stringResource(R.string.header_android_version_full, DeviceInfoUtils.androidVersion))
        InfoLine(stringResource(R.string.header_security_update_full, securityPatch))
    }
}

@Composable
private fun LastCheckedText(
    timestampMillis: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateText = remember(timestampMillis) {
        val date = DateUtils.formatDateTime(
            context,
            timestampMillis,
            DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_MONTH or
                    DateUtils.FORMAT_NO_YEAR,
        )
        val time = DateFormat.getTimeFormat(context).format(Date(timestampMillis))
        "$date ($time)"
    }

    Column(modifier = modifier) {
        InfoLine(stringResource(R.string.header_last_check))
        InfoLine(dateText)
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@UiModePreviews
@Composable
private fun SystemUpdateScreenUpToDatePreview() {
    SettingsTheme {
        SystemUpdateScreen(
            headline = "Your system is up to date",
            supportingText = null,
            isBusy = false,
            canCheckForUpdates = true,
            showDeviceInfo = false,
            lastCheckedTimestamp = 1_754_000_000_000L,
            onBackClick = {},
            onCheckClick = {},
            onLocalUpdateClick = {},
            onPreferencesClick = {},
        )
    }
}

@UiModePreviews
@Composable
private fun SystemUpdateScreenCheckingPreview() {
    SettingsTheme {
        SystemUpdateScreen(
            headline = "Checking for update…",
            supportingText = null,
            isBusy = true,
            canCheckForUpdates = false,
            showDeviceInfo = false,
            lastCheckedTimestamp = 0L,
            onBackClick = {},
            onCheckClick = {},
            onLocalUpdateClick = {},
            onPreferencesClick = {},
        )
    }
}
