package com.akash.qrdrawer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Build

@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "QR Quick Drawer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Your payment QRs one tap away from any screen. Everything stays on this phone; the app has no internet access.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Step(
            number = 1,
            title = "Display over other apps",
            body = "Lets the floating bubble appear on top of any app. Without it, you can still open QRs from this app.",
            done = permissions.overlay,
            action = "Allow",
            onAction = onRequestOverlay,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            Step(
                number = 2,
                title = "Notifications",
                body = "Shows the bubble's status notification, where you can bring back a hidden bubble.",
                done = permissions.notifications,
                action = "Allow",
                onAction = onRequestNotifications,
            )
        }
        Step(
            number = if (Build.VERSION.SDK_INT >= 33) 3 else 2,
            title = "Keep the bubble running",
            body = "Some phones close background apps to save battery. Set this app's battery usage to " +
                "\"No restrictions\" and enable Autostart if available.",
            done = null,
            action = "Open settings",
            onAction = onOpenBatterySettings,
        )

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(if (permissions.overlay) "Get started" else "Continue without bubble")
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, done: Boolean?, action: String, onAction: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$number. $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (done == true) Text("✓ Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (done != true) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
