package com.akash.qrdrawer.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.akash.qrdrawer.data.DrawerPreferences
import com.akash.qrdrawer.data.QrCode
import com.akash.qrdrawer.data.QrRepository
import com.akash.qrdrawer.manager.AppLock
import com.akash.qrdrawer.qr.QrRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen QR (brightened if the screen is dim) for the payer to scan. Swipe sideways between QRs, down to close. */
class QrViewerActivity : FragmentActivity() {
    private val repository by lazy { QrRepository(this) }
    private var requestedId by mutableStateOf(0L)
    private var qrs by mutableStateOf<List<QrCode>?>(null)
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedId = intent.getLongExtra(EXTRA_QR_ID, 0L)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Brightness is a per-window override, so it reverts on its own when this window closes.
        if (systemBrightness() < MIN_BRIGHTNESS) {
            window.attributes = window.attributes.apply { screenBrightness = MIN_BRIGHTNESS }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = true
        }

        lifecycleScope.launch {
            qrs = repository.all()
            val lockViewer = DrawerPreferences(this@QrViewerActivity).settings.first().lockViewer
            if (!lockViewer) {
                unlocked = true
            } else {
                AppLock.authenticate(this@QrViewerActivity, "Unlock to show QR") { ok ->
                    if (ok) unlocked = true else finish()
                }
            }
        }

        setContent {
            val list = qrs
            Box(Modifier.fillMaxSize().background(Color.White)) {
                if (list != null && unlocked) {
                    if (list.isEmpty()) {
                        Text("No QR codes saved yet.", Modifier.align(Alignment.Center), color = Color(0xFF626B78))
                    } else {
                        ViewerPager(list, requestedId, repository, onClose = ::finish)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { qrs = repository.all() }
        requestedId = intent.getLongExtra(EXTRA_QR_ID, requestedId)
    }

    /**
     * Current brightness as 0..1. Some OEMs use a scale above 255; those read as bright,
     * so the screen is left untouched rather than dimmed.
     */
    private fun systemBrightness(): Float =
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, MAX_SYSTEM_BRIGHTNESS) /
            MAX_SYSTEM_BRIGHTNESS.toFloat()

    companion object {
        private const val EXTRA_QR_ID = "qr_id"
        /** Only dim screens are brightened, and only up to this level. */
        private const val MIN_BRIGHTNESS = 0.5f
        private const val MAX_SYSTEM_BRIGHTNESS = 255

        fun intent(context: Context, qrId: Long): Intent =
            Intent(context, QrViewerActivity::class.java).putExtra(EXTRA_QR_ID, qrId)
    }
}

@Composable
private fun ViewerPager(qrs: List<QrCode>, requestedId: Long, repository: QrRepository, onClose: () -> Unit) {
    val initialPage = qrs.indexOfFirst { it.id == requestedId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { qrs.size }
    LaunchedEffect(requestedId, qrs) {
        val page = qrs.indexOfFirst { it.id == requestedId }
        if (page >= 0 && page != pagerState.currentPage) pagerState.scrollToPage(page)
    }

    val closeDistance = with(LocalDensity.current) { 140.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var releasing by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (releasing) 0f else dragOffset,
        label = "swipeDown",
        finishedListener = { releasing = false },
    )
    val offset = if (releasing) animatedOffset else dragOffset

    Box(
        Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) },
                onDragStopped = {
                    if (dragOffset > closeDistance) onClose()
                    else {
                        releasing = true
                        dragOffset = 0f
                    }
                },
            )
            .graphicsLayer {
                translationY = offset
                alpha = 1f - (offset / (closeDistance * 3)).coerceIn(0f, 0.6f)
            },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            QrPage(qrs[page], repository)
        }
        Box(
            Modifier
                .safeDrawingPadding()
                .padding(12.dp)
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F4F8))
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) {
            Text("×", fontSize = 28.sp, color = Color(0xFF1B2029))
        }
        if (qrs.size > 1) {
            Row(
                Modifier.safeDrawingPadding().padding(bottom = 24.dp).align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(qrs.size) { index ->
                    Box(
                        Modifier
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) Color(qrs[index].color) else Color(0xFFD4D9E0)),
                    )
                }
            }
        }
    }
}

@Composable
private fun QrPage(qr: QrCode, repository: QrRepository) {
    val bitmap by produceState<Bitmap?>(null, qr.id, qr.payload, qr.originalImagePath) {
        value = withContext(Dispatchers.Default) {
            if (qr.hasPayload) QrRenderer.render(qr.payload)
            else repository.imageFile(qr.originalImagePath)?.let { BitmapFactory.decodeFile(it.path) }
        }
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 48.dp)) {
            Box(Modifier.width(8.dp).height(32.dp).clip(RoundedCornerShape(4.dp)).background(Color(qr.color)))
            Spacer(Modifier.width(12.dp))
            Text(
                qr.label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B2029),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (qr.bank.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(qr.bank, fontSize = 20.sp, color = Color(0xFF3B4452), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR code for ${qr.label}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    // Nearest-neighbour keeps module edges razor sharp for scanners.
                    filterQuality = if (qr.hasPayload) FilterQuality.None else FilterQuality.Medium,
                )
            }
        }
        if (qr.upiId.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(qr.upiId, fontSize = 18.sp, color = Color(0xFF3B4452), textAlign = TextAlign.Center)
        }
        if (!qr.hasPayload) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Showing the original image",
                fontSize = 13.sp,
                color = Color(0xFF8A93A0),
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}
