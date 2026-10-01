package com.akash.qrdrawer.manager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.akash.qrdrawer.bubble.BubbleService
import com.akash.qrdrawer.data.BubbleSize
import com.akash.qrdrawer.data.DrawerPreferences
import com.akash.qrdrawer.data.DrawerSettings
import com.akash.qrdrawer.data.QR_COLORS
import com.akash.qrdrawer.data.QrCode
import com.akash.qrdrawer.data.QrRepository
import com.akash.qrdrawer.data.parseUpi
import com.akash.qrdrawer.qr.QrImageDecoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Editable copy of a [QrCode]; `id == 0` means a new QR. */
data class QrDraft(
    val id: Long = 0,
    val label: String = "",
    val bank: String = "",
    val upiId: String = "",
    val payload: String = "",
    val color: Int = QR_COLORS.first(),
    val note: String = "",
    val isDefault: Boolean = false,
    val originalImagePath: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toQrCode() = QrCode(
        id = id,
        label = label.trim(),
        bank = bank.trim(),
        upiId = upiId.trim(),
        payload = payload,
        color = color,
        note = note.trim(),
        isDefault = isDefault,
        sortOrder = sortOrder,
        originalImagePath = originalImagePath,
        createdAt = createdAt,
    )

    companion object {
        fun from(qr: QrCode) = QrDraft(
            id = qr.id,
            label = qr.label,
            bank = qr.bank,
            upiId = qr.upiId,
            payload = qr.payload,
            color = qr.color,
            note = qr.note,
            isDefault = qr.isDefault,
            originalImagePath = qr.originalImagePath,
            sortOrder = qr.sortOrder,
            createdAt = qr.createdAt,
        )
    }
}

/** What picking an image produced; the UI decides the follow-up. */
sealed interface ImageDecodeResult {
    data class Single(val payload: String) : ImageDecodeResult
    data class Several(val payloads: List<String>) : ImageDecodeResult
    data class NoneFound(val uri: Uri) : ImageDecodeResult
}

class ManagerViewModel(private val application: Application) : ViewModel() {
    private val repository = QrRepository(application)
    private val preferences = DrawerPreferences(application)

    val qrs: StateFlow<List<QrCode>?> = repository.qrCodes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<DrawerSettings?> = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = mutableMessages

    fun imageFile(name: String?) = repository.imageFile(name)

    /** New draft pre-filled from a decoded payload (UPI fields when it is a UPI QR). */
    fun draftFromPayload(payload: String): QrDraft {
        val upi = parseUpi(payload)
        return QrDraft(
            label = upi.payeeName,
            bank = upi.bank,
            upiId = upi.upiId,
            payload = payload,
            color = nextColor(),
        )
    }

    fun draftFromImage(imageName: String) = QrDraft(originalImagePath = imageName, color = nextColor())

    private fun nextColor(): Int {
        val used = qrs.value.orEmpty().map(QrCode::color).toSet()
        return QR_COLORS.firstOrNull { it !in used } ?: QR_COLORS[qrs.value.orEmpty().size % QR_COLORS.size]
    }

    fun decodeImage(uri: Uri, onResult: (ImageDecodeResult) -> Unit) {
        viewModelScope.launch {
            val payloads = runCatching { QrImageDecoder.decode(application, uri) }.getOrElse { emptyList() }
            onResult(
                when (payloads.size) {
                    0 -> ImageDecodeResult.NoneFound(uri)
                    1 -> ImageDecodeResult.Single(payloads.single())
                    else -> ImageDecodeResult.Several(payloads)
                },
            )
        }
    }

    fun storeImage(uri: Uri, onStored: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.storeImage(uri) }
                .onSuccess(onStored)
                .onFailure { mutableMessages.tryEmit("Could not read that image.") }
        }
    }

    /** Returns a validation error, or null after saving. */
    fun save(draft: QrDraft, onSaved: () -> Unit): String? {
        val label = draft.label.trim()
        when {
            label.isEmpty() -> return "Give this QR a label, e.g. \"HDFC Salary\"."
            label.length > QrRepository.MAX_FIELD_LENGTH -> return "Label is too long."
            draft.payload.isBlank() && draft.originalImagePath == null -> return "This QR has no content."
            draft.payload.length > QrRepository.MAX_PAYLOAD_LENGTH -> return "QR content is too long."
        }
        viewModelScope.launch {
            repository.save(draft.toQrCode())
            onSaved()
        }
        return null
    }

    fun delete(qr: QrCode) {
        viewModelScope.launch {
            repository.delete(qr)
            mutableMessages.tryEmit("Deleted ${qr.label}")
        }
    }

    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorder(orderedIds) }
    }

    fun setDefault(qr: QrCode, isDefault: Boolean) {
        viewModelScope.launch { repository.save(qr.copy(isDefault = isDefault)) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.exportTo(uri) }
                .onSuccess { mutableMessages.tryEmit("Exported $it QR code(s).") }
                .onFailure { mutableMessages.tryEmit("Export failed: ${it.message}") }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.importFrom(uri) }
                .onSuccess { mutableMessages.tryEmit(if (it == 0) "Nothing new to import." else "Imported $it QR code(s).") }
                .onFailure { mutableMessages.tryEmit("Import failed: ${it.message}") }
        }
    }

    fun setBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBubbleEnabled(enabled)
            if (enabled) BubbleService.start(application) else BubbleService.stop(application)
        }
    }

    /** Restarts the service if the OS killed it, and brings a hidden bubble back (opening the app restores it). */
    fun ensureBubbleRunning() {
        viewModelScope.launch {
            val current = preferences.settings.first()
            if (!current.bubbleEnabled) return@launch
            if (current.bubbleHidden) preferences.setBubbleHidden(false)
            BubbleService.start(application)
        }
    }

    fun setOpacity(opacity: Float) = viewModelScope.launch { preferences.setOpacity(opacity) }
    fun setSize(size: BubbleSize) = viewModelScope.launch { preferences.setSize(size) }
    fun resetPosition() = viewModelScope.launch { preferences.resetPosition() }
    fun setStartOnBoot(enabled: Boolean) = viewModelScope.launch { preferences.setStartOnBoot(enabled) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { preferences.setAppLock(enabled) }
    fun setLockViewer(enabled: Boolean) = viewModelScope.launch { preferences.setLockViewer(enabled) }
    fun setSecureManager(enabled: Boolean) = viewModelScope.launch { preferences.setSecureManager(enabled) }
    fun finishOnboarding() = viewModelScope.launch { preferences.setOnboardingDone() }

    fun message(text: String) {
        mutableMessages.tryEmit(text)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagerViewModel(application) as T
    }
}
