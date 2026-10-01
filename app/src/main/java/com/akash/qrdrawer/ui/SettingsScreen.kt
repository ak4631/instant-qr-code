package com.akash.qrdrawer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akash.qrdrawer.data.BubbleSize
import com.akash.qrdrawer.data.DrawerSettings
import com.akash.qrdrawer.data.MIN_BUBBLE_OPACITY
import com.akash.qrdrawer.manager.ManagerViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: ManagerViewModel,
    settings: DrawerSettings,
    permissions: PermissionState,
    lockAvailable: Boolean,
    onBack: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var opacity by remember(settings.bubbleOpacity) { mutableFloatStateOf(settings.bubbleOpacity) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
        ScreenHeader("Settings", onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Bubble") {
                ToggleRow(
                    title = "Show floating bubble",
                    subtitle = if (permissions.overlay) null else "Needs \"Display over other apps\".",
                    checked = settings.bubbleEnabled && permissions.overlay,
                    onChange = { if (permissions.overlay) viewModel.setBubbleEnabled(it) else onRequestOverlay() },
                )
                Text("Idle opacity: ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = { viewModel.setOpacity(opacity) },
                    valueRange = MIN_BUBBLE_OPACITY..1f,
                )
                Text("Size", style = MaterialTheme.typography.bodyMedium)
                // Wraps so four chips still fit on narrow phones.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BubbleSize.entries.forEach { size ->
                        FilterChip(
                            selected = settings.bubbleSize == size,
                            onClick = { viewModel.setSize(size) },
                            label = { Text("${size.label} · ${size.dp}dp") },
                        )
                    }
                }
                OutlinedButton(onClick = { viewModel.resetPosition() }) { Text("Reset bubble position") }
                ToggleRow(
                    title = "Start on phone boot",
                    subtitle = "Brings the bubble back after a restart.",
                    checked = settings.startOnBoot,
                    onChange = viewModel::setStartOnBoot,
                )
            }

            SectionCard("Keep the bubble alive") {
                Text(
                    "Xiaomi, Oppo, Vivo, Realme and Samsung may close the bubble to save battery. " +
                        "Open the app's settings and set Battery to \"No restrictions\" / \"Unrestricted\", " +
                        "and turn on Autostart if your phone has it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenBatterySettings) { Text("Open app battery settings") }
            }

            SectionCard("Security") {
                ToggleRow(
                    title = "App lock",
                    subtitle = if (lockAvailable) {
                        "Fingerprint or screen lock to open this manager, so nobody can swap a QR."
                    } else {
                        "Set up a screen lock on this phone to use this."
                    },
                    checked = settings.appLock,
                    enabled = lockAvailable || settings.appLock,
                    onChange = viewModel::setAppLock,
                )
                ToggleRow(
                    title = "Also lock showing QRs",
                    subtitle = "Slower: asks before showing a QR from the drawer.",
                    checked = settings.lockViewer,
                    enabled = settings.appLock,
                    onChange = viewModel::setLockViewer,
                )
                ToggleRow(
                    title = "Block screenshots of this screen",
                    subtitle = "Hides the manager from screenshots and the recent-apps preview.",
                    checked = settings.secureManager,
                    onChange = viewModel::setSecureManager,
                )
            }

            SectionCard("Backup") {
                Text(
                    "Export all QRs (payloads, labels, images) to a JSON file you keep yourself. " +
                        "Nothing is backed up to the cloud automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
                }
            }

            SectionCard("Privacy") {
                Text(
                    "This app has no internet permission. Your QRs never leave this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
