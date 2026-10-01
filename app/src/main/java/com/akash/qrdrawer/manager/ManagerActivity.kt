package com.akash.qrdrawer.manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.akash.qrdrawer.ui.AddSourceDialog
import com.akash.qrdrawer.ui.ChoosePayloadDialog
import com.akash.qrdrawer.ui.EditQrScreen
import com.akash.qrdrawer.ui.ManagerScreen
import com.akash.qrdrawer.ui.NoQrFoundDialog
import com.akash.qrdrawer.ui.OnboardingScreen
import com.akash.qrdrawer.ui.PermissionState
import com.akash.qrdrawer.ui.QrDrawerTheme
import com.akash.qrdrawer.ui.ScannerScreen
import com.akash.qrdrawer.ui.SettingsScreen
import com.akash.qrdrawer.viewer.QrViewerActivity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface Route {
    data object List : Route
    data class Edit(val draft: QrDraft) : Route
    data object Scan : Route
    data object Settings : Route
}

/** Add, edit, reorder and delete QRs; settings; onboarding. Optionally behind the app lock. */
class ManagerActivity : FragmentActivity() {
    private lateinit var viewModel: ManagerViewModel
    private var route by mutableStateOf<Route>(Route.List)
    private var permissions by mutableStateOf(PermissionState())
    private var showAddSource by mutableStateOf(false)
    private var payloadChoices by mutableStateOf<kotlin.collections.List<String>?>(null)
    private var undecodableImage by mutableStateOf<Uri?>(null)
    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L
    /** The device-credential fallback is its own activity, so onStart can run again mid-prompt. */
    private var authInProgress = false
    /** True while a picker, camera prompt or system screen we launched is on top; don't relock for it. */
    private var awaitingExternal = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        awaitingExternal = false
        if (uri != null) viewModel.decodeImage(uri, ::onImageDecoded)
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        awaitingExternal = false
        if (uri != null) viewModel.export(uri)
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        awaitingExternal = false
        if (uri != null) viewModel.import(uri)
    }
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingExternal = false
        if (granted) route = Route.Scan else viewModel.message("Camera permission is needed to scan a QR.")
    }
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        awaitingExternal = false
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, ManagerViewModel.Factory(application))[ManagerViewModel::class.java]

        lifecycleScope.launch {
            viewModel.messages.collect { Toast.makeText(this@ManagerActivity, it, Toast.LENGTH_LONG).show() }
        }
        lifecycleScope.launch {
            viewModel.settings.filterNotNull().collect { settings ->
                if (settings.secureManager) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            QrDrawerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val settings by viewModel.settings.collectAsStateWithLifecycle()
                    val current = settings
                    when {
                        current == null -> Box(Modifier.fillMaxSize())
                        current.appLock && !unlocked -> Box(Modifier.fillMaxSize())
                        !current.onboardingDone -> OnboardingScreen(
                            permissions = permissions,
                            onRequestOverlay = ::openOverlaySettings,
                            onRequestNotifications = ::askNotifications,
                            onOpenBatterySettings = ::openBatterySettings,
                            onDone = {
                                viewModel.finishOnboarding()
                                if (permissions.overlay) viewModel.setBubbleEnabled(true)
                            },
                        )
                        else -> Router(current)
                    }
                    AddSourceDialog(
                        visible = showAddSource,
                        onDismiss = { showAddSource = false },
                        onGallery = {
                            showAddSource = false
                            pickFromGallery()
                        },
                        onCamera = {
                            showAddSource = false
                            startScan()
                        },
                    )
                    payloadChoices?.let { choices ->
                        ChoosePayloadDialog(
                            payloads = choices,
                            onChoose = {
                                payloadChoices = null
                                route = Route.Edit(viewModel.draftFromPayload(it))
                            },
                            onDismiss = { payloadChoices = null },
                        )
                    }
                    undecodableImage?.let { uri ->
                        NoQrFoundDialog(
                            onSaveImage = {
                                undecodableImage = null
                                viewModel.storeImage(uri) { name -> route = Route.Edit(viewModel.draftFromImage(name)) }
                            },
                            onRetry = {
                                undecodableImage = null
                                pickFromGallery()
                            },
                            onDismiss = { undecodableImage = null },
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    @androidx.compose.runtime.Composable
    private fun Router(settings: com.akash.qrdrawer.data.DrawerSettings) {
        val current = route
        BackHandler(enabled = current != Route.List) { route = Route.List }
        when (current) {
            Route.List -> ManagerScreen(
                viewModel = viewModel,
                settings = settings,
                permissions = permissions,
                onAdd = { showAddSource = true },
                onEdit = { route = Route.Edit(QrDraft.from(it)) },
                onShow = { startActivity(QrViewerActivity.intent(this, it.id)) },
                onOpenSettings = { route = Route.Settings },
                onRequestOverlay = ::openOverlaySettings,
            )
            is Route.Edit -> EditQrScreen(
                initial = current.draft,
                imageFile = viewModel::imageFile,
                onSave = { draft -> viewModel.save(draft) { route = Route.List } },
                onCancel = { route = Route.List },
            )
            Route.Scan -> ScannerScreen(
                onScanned = { payload -> route = Route.Edit(viewModel.draftFromPayload(payload)) },
                onPickImage = ::pickFromGallery,
                onCancel = { route = Route.List },
            )
            Route.Settings -> SettingsScreen(
                viewModel = viewModel,
                settings = settings,
                permissions = permissions,
                lockAvailable = AppLock.isAvailable(this),
                onBack = { route = Route.List },
                onRequestOverlay = ::openOverlaySettings,
                onOpenBatterySettings = ::openBatterySettings,
                onExport = {
                    awaitingExternal = true
                    exportBackup.launch("qr-quick-drawer-backup.json")
                },
                onImport = {
                    awaitingExternal = true
                    importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val relock = !awaitingExternal && !authInProgress && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS
        if (relock) unlocked = false
        awaitingExternal = false
        lifecycleScope.launch {
            val settings = viewModel.settings.filterNotNull().first()
            if (!settings.appLock) {
                unlocked = true
            } else if (!unlocked && !authInProgress) {
                authInProgress = true
                AppLock.authenticate(this@ManagerActivity, "Unlock QR Quick Drawer") { ok ->
                    authInProgress = false
                    if (ok) unlocked = true else finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        viewModel.ensureBubbleRunning()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ADD_QR) {
            route = Route.List
            showAddSource = true
        }
    }

    private fun onImageDecoded(result: ImageDecodeResult) {
        when (result) {
            is ImageDecodeResult.Single -> route = Route.Edit(viewModel.draftFromPayload(result.payload))
            is ImageDecodeResult.Several -> payloadChoices = result.payloads
            is ImageDecodeResult.NoneFound -> undecodableImage = result.uri
        }
    }

    private fun pickFromGallery() {
        awaitingExternal = true
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun startScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            route = Route.Scan
        } else {
            awaitingExternal = true
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun refreshPermissions() {
        permissions = PermissionState(
            overlay = Settings.canDrawOverlays(this),
            notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            awaitingExternal = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openOverlaySettings() {
        awaitingExternal = true
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }

    /** OEM battery savers kill overlay services; the app details page is the one screen every OEM has. */
    private fun openBatterySettings() {
        awaitingExternal = true
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        val launched = intents.any { runCatching { startActivity(it) }.isSuccess }
        if (!launched) awaitingExternal = false
    }

    companion object {
        const val ACTION_ADD_QR = "com.akash.qrdrawer.action.ADD_QR"
        private const val RELOCK_AFTER_MS = 30_000L
    }
}
