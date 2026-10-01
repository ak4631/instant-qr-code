package com.akash.qrdrawer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akash.qrdrawer.data.DrawerSettings
import com.akash.qrdrawer.data.QrCode
import com.akash.qrdrawer.data.maskUpiId
import com.akash.qrdrawer.data.moveItem
import com.akash.qrdrawer.manager.ManagerViewModel

private val RowHeight = 76.dp
private val RowGap = 8.dp
private const val SOFT_LIMIT = 20

@Composable
fun ManagerScreen(
    viewModel: ManagerViewModel,
    settings: DrawerSettings,
    permissions: PermissionState,
    onAdd: () -> Unit,
    onEdit: (QrCode) -> Unit,
    onShow: (QrCode) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestOverlay: () -> Unit,
) {
    val qrs by viewModel.qrs.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<QrCode?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
                .navigationBarsPadding(),
        ) {
            ScreenHeader("QR Quick Drawer") {
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BubbleCard(settings, permissions, viewModel::setBubbleEnabled, onRequestOverlay)
                val list = qrs
                when {
                    list == null -> Unit
                    list.isEmpty() -> EmptyState(onAdd)
                    else -> {
                        QrList(
                            qrs = list,
                            imageFile = viewModel::imageFile,
                            onReorder = viewModel::reorder,
                            onEdit = onEdit,
                            onShow = onShow,
                            onToggleDefault = { viewModel.setDefault(it, !it.isDefault) },
                            onDelete = { pendingDelete = it },
                        )
                        Text(
                            if (list.size > SOFT_LIMIT) {
                                "You have ${list.size} QRs. The drawer scrolls after 5; consider removing ones you no longer use."
                            } else {
                                "Drag ≡ to reorder. The default QR (★) is always first and opens on a long-press of the bubble."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Text("+  Add QR", fontWeight = FontWeight.Bold) }
    }

    pendingDelete?.let { qr ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${qr.label}?") },
            text = { Text("This removes the QR from this phone. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(qr)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BubbleCard(
    settings: DrawerSettings,
    permissions: PermissionState,
    onEnabledChange: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Floating bubble", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !permissions.overlay -> "Needs \"Display over other apps\" permission."
                        settings.bubbleEnabled -> "On. Tap it from any app to see your QRs."
                        else -> "Off. The app still works without it."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.bubbleEnabled && permissions.overlay,
                onCheckedChange = { if (permissions.overlay) onEnabledChange(it) else onRequestOverlay() },
            )
        }
        if (!permissions.overlay) {
            Button(onClick = onRequestOverlay) { Text("Allow display over other apps") }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    SectionCard {
        Text("No QR codes yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Add each bank's UPI Receive QR once. Screenshots work best: open the bank app's Receive screen, " +
                "take a screenshot, then pick it here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd) { Text("Add your first QR") }
    }
}

@Composable
private fun QrList(
    qrs: List<QrCode>,
    imageFile: (String?) -> java.io.File?,
    onReorder: (List<Long>) -> Unit,
    onEdit: (QrCode) -> Unit,
    onShow: (QrCode) -> Unit,
    onToggleDefault: (QrCode) -> Unit,
    onDelete: (QrCode) -> Unit,
) {
    // The default QR is pinned first by the database; only the rest can be dragged.
    val pinned = qrs.filter(QrCode::isDefault)
    var order by remember(qrs) { mutableStateOf(qrs.filterNot(QrCode::isDefault)) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val stepPx = with(LocalDensity.current) { (RowHeight + RowGap).toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        pinned.forEach { qr ->
            key(qr.id) { QrRow(qr, imageFile, null, false, onEdit, onShow, onToggleDefault, onDelete) }
        }
        order.forEach { qr ->
            key(qr.id) {
                val dragging = draggingId == qr.id
                val handle = Modifier.pointerInput(qr.id, qrs) {
                    detectDragGestures(
                        onDragStart = {
                            draggingId = qr.id
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val index = order.indexOfFirst { it.id == qr.id }
                            if (dragOffset > stepPx / 2 && index < order.lastIndex) {
                                order = moveItem(order, index, index + 1)
                                dragOffset -= stepPx
                            } else if (dragOffset < -stepPx / 2 && index > 0) {
                                order = moveItem(order, index, index - 1)
                                dragOffset += stepPx
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            dragOffset = 0f
                            onReorder(pinned.map(QrCode::id) + order.map(QrCode::id))
                        },
                        onDragCancel = {
                            draggingId = null
                            dragOffset = 0f
                        },
                    )
                }
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                ) {
                    QrRow(qr, imageFile, handle, dragging, onEdit, onShow, onToggleDefault, onDelete)
                }
            }
        }
    }
}

@Composable
private fun QrRow(
    qr: QrCode,
    imageFile: (String?) -> java.io.File?,
    dragHandle: Modifier?,
    dragging: Boolean,
    onEdit: (QrCode) -> Unit,
    onShow: (QrCode) -> Unit,
    onToggleDefault: (QrCode) -> Unit,
    onDelete: (QrCode) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onEdit(qr) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Color(qr.color)))
            if (dragHandle != null) {
                Box(
                    dragHandle.size(40.dp, RowHeight).semantics { contentDescription = "Drag to reorder" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("≡", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }
            QrThumbnail(qr.payload, imageFile(qr.originalImagePath), 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (qr.isDefault) "★ ${qr.label}" else qr.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOf(qr.bank, maskUpiId(qr.upiId)).filter(String::isNotBlank).joinToString(" · ")
                Text(
                    subtitle.ifEmpty { if (qr.hasPayload) "QR code" else "Image only" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { onShow(qr) }) { Text("Show") }
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "More options for ${qr.label}" },
                ) { Text("⋮", fontSize = 20.sp) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                        menuOpen = false
                        onEdit(qr)
                    })
                    DropdownMenuItem(text = { Text(if (qr.isDefault) "Remove default" else "Make default") }, onClick = {
                        menuOpen = false
                        onToggleDefault(qr)
                    })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                        menuOpen = false
                        onDelete(qr)
                    })
                }
            }
        }
    }
}
