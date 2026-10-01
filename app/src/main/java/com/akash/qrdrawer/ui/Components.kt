package com.akash.qrdrawer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.qrdrawer.data.QR_COLORS
import com.akash.qrdrawer.qr.QrRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PermissionState(
    val overlay: Boolean = false,
    val notifications: Boolean = false,
)

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                Text("←", fontSize = 22.sp)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 0.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        action()
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            content()
        }
    }
}

/** QR thumbnail on a white tile: re-rendered payload, or the stored image when there is none. */
@Composable
fun QrThumbnail(payload: String, imageFile: File?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, payload, imageFile) {
        value = withContext(Dispatchers.Default) {
            if (payload.isNotBlank()) QrRenderer.render(payload)
            else imageFile?.let { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 }) }
        }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = if (payload.isNotBlank()) FilterQuality.None else FilterQuality.Medium,
            )
        }
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QR_COLORS.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (isSelected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(color) }
                    .semantics { contentDescription = if (isSelected) "Selected colour" else "Colour" },
            )
        }
    }
}

@Composable
fun AddSourceDialog(visible: Boolean, onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Import a screenshot of your bank's Receive QR, or scan a printed one.")
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text("Pick screenshot / photo") }
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Scan with camera") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ChoosePayloadDialog(payloads: List<String>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Several QRs found") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick the one to save:")
                payloads.forEach { payload ->
                    val upi = com.akash.qrdrawer.data.parseUpi(payload)
                    OutlinedButton(onClick = { onChoose(payload) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (upi.isUpi) listOf(upi.payeeName, upi.upiId).filter(String::isNotBlank).joinToString(" · ") else payload,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NoQrFoundDialog(onSaveImage: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No QR code found") },
        text = {
            Text(
                "Couldn't read a QR in that image. Try a sharper screenshot, or save the image as-is " +
                    "(it will be shown exactly as it is, without re-rendering).",
            )
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("Pick another") } },
        dismissButton = { TextButton(onClick = onSaveImage) { Text("Save image anyway") } },
    )
}
