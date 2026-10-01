package com.akash.qrdrawer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akash.qrdrawer.data.QrRepository
import com.akash.qrdrawer.data.parseUpi
import com.akash.qrdrawer.manager.QrDraft
import java.io.File

@Composable
fun EditQrScreen(
    initial: QrDraft,
    imageFile: (String?) -> File?,
    onSave: (QrDraft) -> String?,
    onCancel: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val decodedUpi = remember(draft.payload) { parseUpi(draft.payload) }
    // The QR pays whatever `pa` is inside the payload, not what is typed here; flag any mismatch.
    val upiMismatch = decodedUpi.isUpi && draft.upiId.isNotBlank() &&
        !draft.upiId.trim().equals(decodedUpi.upiId, ignoreCase = true)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        ScreenHeader(if (initial.id == 0L) "New QR" else "Edit QR", onBack = onCancel)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrThumbnail(draft.payload, imageFile(draft.originalImagePath), 96.dp)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(
                            when {
                                decodedUpi.isUpi -> "UPI QR"
                                draft.payload.isNotBlank() -> "QR code (not UPI)"
                                else -> "Image only"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                decodedUpi.isUpi -> "Pays to ${decodedUpi.upiId}"
                                draft.payload.isNotBlank() -> draft.payload
                                else -> "Couldn't decode; the original image will be shown."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it.take(QrRepository.MAX_FIELD_LENGTH)) },
                label = { Text("Label *") },
                placeholder = { Text("e.g. HDFC Salary") },
                singleLine = true,
                isError = error != null && draft.label.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.bank,
                onValueChange = { draft = draft.copy(bank = it.take(QrRepository.MAX_FIELD_LENGTH)) },
                label = { Text("Bank") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.upiId,
                onValueChange = { draft = draft.copy(upiId = it.take(QrRepository.MAX_FIELD_LENGTH)) },
                label = { Text("UPI ID") },
                singleLine = true,
                isError = upiMismatch,
                supportingText = if (upiMismatch) {
                    { Text("Doesn't match the QR, which pays to ${decodedUpi.upiId}.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(QrRepository.MAX_NOTE_LENGTH)) },
                label = { Text("Note (e.g. last 4 digits of account)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionCard("Colour") {
                ColorPicker(draft.color) { draft = draft.copy(color = it) }
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Default QR", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pinned first; long-press the bubble to open it in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = draft.isDefault, onCheckedChange = { draft = draft.copy(isDefault = it) })
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { error = onSave(draft) }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}
