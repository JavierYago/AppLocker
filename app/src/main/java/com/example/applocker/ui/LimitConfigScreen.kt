package com.example.applocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.applocker.R
import com.example.applocker.domain.model.AppLimit
import com.example.applocker.util.formatMinutes

private val SuggestedAlerts = listOf(5, 10, 15, 30)

private val IntListStateSaver = listSaver<MutableState<List<Int>>, Int>(
    save = { it.value },
    restore = { mutableStateOf(it) },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LimitConfigScreen(
    row: AppRow,
    onBack: () -> Unit,
    onSave: (dailyLimitMinutes: Int, alerts: List<Int>, enabled: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val existing = row.limit
    val initialMinutes = existing?.dailyLimitMinutes ?: 60

    var hoursText by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutesText by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var alerts by rememberSaveable(saver = IntListStateSaver) {
        mutableStateOf(existing?.alertThresholdsMinutes.orEmpty())
    }
    var newAlertText by rememberSaveable { mutableStateOf("") }

    val totalMinutes = (hoursText.toIntOrNull() ?: 0) * 60 + (minutesText.toIntOrNull() ?: 0)
    val limitValid = totalMinutes in 1..AppLimit.MAX_DAILY_LIMIT_MINUTES
    // Un aviso a "30 min restantes" no tiene sentido si el límite es de 20 min.
    val alertsValid = alerts.all { it < totalMinutes }
    val newAlert = newAlertText.toIntOrNull()
    val canAddAlert = newAlert != null && newAlert in 1 until totalMinutes && newAlert !in alerts

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row.app.label, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = remember(row.app.icon) { row.app.icon.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(row.app.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        row.app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.daily_limit), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = stringResource(R.string.hours),
                    maxDigits = 2,
                    isError = !limitValid,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = stringResource(R.string.minutes),
                    maxDigits = 2,
                    isError = !limitValid,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!limitValid) ErrorText(stringResource(R.string.error_limit_range))

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.limit_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.alerts_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (alerts.isEmpty()) {
                Text(
                    stringResource(R.string.alerts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.sortedDescending().forEach { alert ->
                        InputChip(
                            selected = alert < totalMinutes,
                            onClick = { alerts = alerts - alert },
                            label = { Text(formatMinutes(alert)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove_alert, formatMinutes(alert)),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            if (!alertsValid) ErrorText(stringResource(R.string.error_alert_range))

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumberField(
                    value = newAlertText,
                    onValueChange = { newAlertText = it },
                    label = stringResource(R.string.alert_minutes_left),
                    maxDigits = 4,
                    isError = newAlertText.isNotEmpty() && !canAddAlert,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    enabled = canAddAlert,
                    onClick = {
                        newAlert?.let { alerts = alerts + it }
                        newAlertText = ""
                    },
                ) { Text(stringResource(R.string.add)) }
            }

            val suggestions = SuggestedAlerts.filter { it < totalMinutes && it !in alerts }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = { alerts = alerts + suggestion },
                            label = { Text("+ ${formatMinutes(suggestion)}") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                enabled = limitValid && alertsValid,
                onClick = { onSave(totalMinutes, alerts, enabled) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }

            if (existing != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.delete_limit)) }
            }
        }
    }
}

/** Campo de texto que solo admite dígitos. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxDigits: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(maxDigits)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
    )
}
