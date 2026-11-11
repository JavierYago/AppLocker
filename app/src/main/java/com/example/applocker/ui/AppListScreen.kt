package com.example.applocker.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.applocker.R
import com.example.applocker.permissions.PermissionState
import com.example.applocker.permissions.PermissionUtils
import com.example.applocker.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    state: MainUiState,
    onConfigure: (packageName: String) -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleApps = remember(state.apps, query) {
        val q = query.trim()
        if (q.isEmpty()) state.apps else state.apps.filter { it.app.label.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            if (!state.permissions.allGranted) {
                item(key = "permissions") {
                    PermissionCards(state.permissions, onPermissionsChanged)
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (visibleApps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(visibleApps, key = { it.app.packageName }) { row ->
                AppRowItem(row, onConfigure = { onConfigure(row.app.packageName) })
                HorizontalDivider(Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, onConfigure: () -> Unit) {
    val limit = row.limit
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = remember(row.app.icon) { row.app.icon.asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    limit == null -> stringResource(R.string.usage_today, formatDuration(row.usedTodayMs))
                    else -> stringResource(
                        R.string.usage_today_with_limit,
                        formatDuration(row.usedTodayMs),
                        formatDuration(limit.dailyLimitMs),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (limit != null && limit.enabled) {
                val progress = (row.usedTodayMs.toFloat() / limit.dailyLimitMs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            } else if (limit != null) {
                Text(
                    text = stringResource(R.string.limit_paused),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(
            onClick = onConfigure,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(if (limit == null) R.string.configure_limit else R.string.edit_limit),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PermissionCards(permissions: PermissionState, onPermissionsChanged: () -> Unit) {
    val context = LocalContext.current

    // Diálogo estándar del permiso POST_NOTIFICATIONS (Android 13+).
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context as? Activity
        // Si se deniega y el sistema ya no va a mostrar más el diálogo ("no volver a
        // preguntar"), el único camino que queda son los ajustes de la app.
        if (!granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            PermissionUtils.openNotificationSettings(context)
        }
        onPermissionsChanged()
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!permissions.usageAccess) {
            PermissionCard(
                title = stringResource(R.string.permission_usage_title),
                body = stringResource(R.string.permission_usage_body),
                onGrant = { PermissionUtils.openUsageAccessSettings(context) },
            )
        }
        if (!permissions.notifications) {
            PermissionCard(
                title = stringResource(R.string.permission_notifications_title),
                body = stringResource(R.string.permission_notifications_body),
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PermissionUtils.openNotificationSettings(context)
                    }
                },
            )
        }
        if (!permissions.overlay) {
            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                body = stringResource(R.string.permission_overlay_body),
                onGrant = { PermissionUtils.openOverlaySettings(context) },
            )
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.grant))
            }
        }
    }
}
