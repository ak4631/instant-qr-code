package com.akash.qrdrawer.bubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.akash.qrdrawer.R
import com.akash.qrdrawer.data.DrawerPreferences
import com.akash.qrdrawer.data.DrawerSettings
import com.akash.qrdrawer.data.QrCode
import com.akash.qrdrawer.data.QrRepository
import com.akash.qrdrawer.manager.ManagerActivity
import com.akash.qrdrawer.qr.QrRenderer
import com.akash.qrdrawer.viewer.QrViewerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private class BubbleView(context: Context) : ImageView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/**
 * Foreground service that draws the floating bubble, the QR drawer and the drag-to-hide
 * target as `TYPE_APPLICATION_OVERLAY` windows, and owns the persistent notification.
 */
class BubbleService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { DrawerPreferences(this) }
    private val repository by lazy { QrRepository(this) }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val drawerBuilder by lazy { DrawerPanelBuilder(this, ::dp) }
    private val closeTarget by lazy { CloseTarget(this, windowManager, ::dp, ::screenSize) }
    private var settingsJob: Job? = null
    private var qrJob: Job? = null
    private var settings = DrawerSettings()
    private var qrs: List<QrCode> = emptyList()
    private val thumbnails = HashMap<Long, Bitmap>()
    private var notifiedHidden: Boolean? = null

    private var bubbleView: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var drawerView: DrawerPanel? = null
    private var drawerParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private var drawerClosedAt = 0L

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var moved = false
    private var longPressFired = false
    private val longPress = Runnable {
        longPressFired = true
        bubbleView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        openDefaultQr()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Outlives serviceScope, which onDestroy cancels right after stopSelf().
                CoroutineScope(Dispatchers.IO).launch { preferences.setBubbleEnabled(false) }
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW -> serviceScope.launch { preferences.setBubbleHidden(false) }
            ACTION_HIDE -> serviceScope.launch { preferences.setBubbleHidden(true) }
        }

        createNotificationChannel()
        val notification = createNotification(hidden = settings.bubbleHidden)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (qrJob == null) {
            qrJob = serviceScope.launch {
                repository.qrCodes.collectLatest { list ->
                    qrs = list
                    preloadThumbnails(list)
                    if (drawerView != null) showDrawer()
                }
            }
        }
        if (settingsJob == null) {
            settingsJob = serviceScope.launch {
                preferences.settings.collectLatest(::applySettings)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun applySettings(newSettings: DrawerSettings) {
        settings = newSettings
        if (!newSettings.bubbleEnabled || !Settings.canDrawOverlays(this)) {
            removeOverlay()
            if (newSettings.bubbleEnabled) preferences.setBubbleEnabled(false)
            stopSelf()
            return
        }
        if (notifiedHidden != newSettings.bubbleHidden) {
            notifiedHidden = newSettings.bubbleHidden
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, createNotification(newSettings.bubbleHidden))
        }
        if (newSettings.bubbleHidden) removeOverlay() else renderBubble()
    }

    // region Bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun renderBubble() {
        val sizePx = dp(settings.bubbleSize.dp)
        val screen = screenSize()
        val maxX = (screen.x - sizePx).coerceAtLeast(0)
        val maxY = (screen.y - sizePx).coerceAtLeast(0)
        val x = if (settings.bubbleX >= 0) settings.bubbleX.coerceIn(0, maxX) else maxX
        val y = if (settings.bubbleY >= 0) settings.bubbleY.coerceIn(0, maxY) else screen.y / 3

        val existing = bubbleView
        val params = bubbleParams
        if (existing == null || params == null) {
            val bubble = BubbleView(this).apply {
                setImageResource(R.drawable.ic_bubble_qr)
                contentDescription = "Open QR drawer. Long-press for default QR."
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BUBBLE_COLOR)
                    setStroke(dp(2), 0xFFFFFFFF.toInt())
                }
                elevation = dp(6).toFloat()
                alpha = settings.bubbleOpacity
                setOnClickListener { toggleDrawer() }
                setOnTouchListener(::handleBubbleTouch)
            }
            val newParams = overlayParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            windowManager.addView(bubble, newParams)
            bubbleView = bubble
            bubbleParams = newParams
        } else {
            existing.alpha = settings.bubbleOpacity
            if (snapAnimator?.isRunning != true) {
                params.x = x
                params.y = y
            }
            params.width = sizePx
            params.height = sizePx
            runCatching { windowManager.updateViewLayout(existing, params) }
        }
        val padding = sizePx / 4
        bubbleView?.setPadding(padding, padding, padding, padding)
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        val params = bubbleParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialWindowX = params.x
                initialWindowY = params.y
                moved = false
                longPressFired = false
                view.alpha = 1f
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressFired) return true
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!moved && (abs(deltaX) > slop || abs(deltaY) > slop)) {
                    moved = true
                    handler.removeCallbacks(longPress)
                    hideDrawer(animate = false)
                    closeTarget.show()
                }
                if (moved) {
                    val screen = screenSize()
                    params.x = (initialWindowX + deltaX).coerceIn(0, (screen.x - params.width).coerceAtLeast(0))
                    params.y = (initialWindowY + deltaY).coerceIn(0, (screen.y - params.height).coerceAtLeast(0))
                    closeTarget.track(event.rawX, event.rawY)
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                val dropOnTarget = moved && closeTarget.isActive
                closeTarget.remove()
                view.alpha = settings.bubbleOpacity
                when {
                    longPressFired -> Unit
                    dropOnTarget -> serviceScope.launch { preferences.setBubbleHidden(true) }
                    moved -> snapToEdge()
                    event.actionMasked == MotionEvent.ACTION_UP -> view.performClick()
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val screen = screenSize()
        val targetX = if (params.x + params.width / 2 < screen.x / 2) 0 else (screen.x - params.width).coerceAtLeast(0)
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (view.isAttachedToWindow) runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
        serviceScope.launch { preferences.setPosition(targetX, params.y) }
    }

    // endregion

    // region Drawer

    private fun toggleDrawer() {
        // A tap on the bubble first arrives as an outside touch that already closed the drawer.
        if (SystemClock.uptimeMillis() - drawerClosedAt < REOPEN_GUARD_MS) return
        if (drawerView != null) hideDrawer() else showDrawer()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showDrawer() {
        val bubble = bubbleParams ?: return
        val wasOpen = drawerView != null
        drawerView?.let(::removeDrawerView)

        val panel = drawerBuilder.build(
            qrs = qrs,
            thumbnail = { thumbnails[it.id] },
            onQrClick = { qr -> openViewer(qr.id) },
            onManage = { openManager(null) },
            onAddFirst = { openManager(ManagerActivity.ACTION_ADD_QR) },
            onDismiss = { hideDrawer() },
        )
        val screen = screenSize()
        val width = min(dp(DRAWER_WIDTH_DP), screen.x - dp(24))
        val height = min(dp(drawerBuilder.contentHeightDp(qrs.size)), screen.y - dp(24))
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable so Back closes the drawer; ALT_FOCUSABLE_IM keeps the underlying keyboard alone.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val onLeft = bubble.x + bubble.width / 2 < screen.x / 2
            val gap = dp(8)
            x = if (onLeft) {
                (bubble.x + bubble.width + gap).coerceAtMost(screen.x - width)
            } else {
                (bubble.x - width - gap).coerceAtLeast(0)
            }
            y = (bubble.y + bubble.height / 2 - height / 2).coerceIn(0, (screen.y - height).coerceAtLeast(0))
        }
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                drawerClosedAt = SystemClock.uptimeMillis()
                hideDrawer()
                true
            } else {
                false
            }
        }
        val fromLeft = bubble.x + bubble.width / 2 < screen.x / 2
        if (!wasOpen) {
            panel.alpha = 0f
            panel.translationX = dp(if (fromLeft) -24 else 24).toFloat()
        }
        drawerView = panel
        drawerParams = params
        windowManager.addView(panel, params)
        panel.requestFocus()
        panel.animate().alpha(1f).translationX(0f).setDuration(DRAWER_IN_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideDrawer(animate: Boolean = true) {
        val view = drawerView ?: return
        drawerView = null
        drawerParams = null
        if (!animate || !view.isAttachedToWindow) {
            removeDrawerView(view)
            return
        }
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(DRAWER_OUT_MS).withEndAction { removeDrawerView(view) }.start()
    }

    private fun removeDrawerView(view: View) {
        view.animate().cancel()
        runCatching { if (view.isAttachedToWindow) windowManager.removeView(view) }
        if (drawerView === view) {
            drawerView = null
            drawerParams = null
        }
    }

    private suspend fun preloadThumbnails(list: List<QrCode>) {
        val rendered = withContext(Dispatchers.Default) {
            list.associate { qr ->
                qr.id to if (qr.hasPayload) QrRenderer.render(qr.payload) else loadImageThumbnail(qr)
            }
        }
        thumbnails.clear()
        rendered.forEach { (id, bitmap) -> if (bitmap != null) thumbnails[id] = bitmap }
    }

    private fun loadImageThumbnail(qr: QrCode): Bitmap? {
        val file = repository.imageFile(qr.originalImagePath) ?: return null
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        return BitmapFactory.decodeFile(file.path, options)
    }

    // endregion

    // region Navigation

    private fun openViewer(qrId: Long) {
        hideDrawer(animate = false)
        startActivity(QrViewerActivity.intent(this, qrId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openDefaultQr() {
        val qr = qrs.firstOrNull { it.isDefault } ?: qrs.firstOrNull()
        if (qr == null) openManager(ManagerActivity.ACTION_ADD_QR) else openViewer(qr.id)
    }

    private fun openManager(action: String?) {
        hideDrawer(animate = false)
        startActivity(
            Intent(this, ManagerActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    // endregion

    private fun removeOverlay() {
        snapAnimator?.cancel()
        handler.removeCallbacks(longPress)
        hideDrawer(animate = false)
        closeTarget.remove()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    )

    private fun screenSize(): Point = Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.bubble_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(hidden: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ManagerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = servicePendingIntent(if (hidden) ACTION_SHOW else ACTION_HIDE, REQUEST_TOGGLE)
        val stop = servicePendingIntent(ACTION_STOP, REQUEST_STOP)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qr)
            .setContentTitle(getString(if (hidden) R.string.bubble_notification_hidden_title else R.string.bubble_notification_title))
            .setContentText(getString(if (hidden) R.string.bubble_notification_hidden_text else R.string.bubble_notification_text))
            .setContentIntent(if (hidden) toggle else openApp)
            .addAction(0, if (hidden) "Show bubble" else "Hide bubble", toggle)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, BubbleService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_START = "com.akash.qrdrawer.action.START"
        const val ACTION_STOP = "com.akash.qrdrawer.action.STOP"
        const val ACTION_SHOW = "com.akash.qrdrawer.action.SHOW"
        const val ACTION_HIDE = "com.akash.qrdrawer.action.HIDE"
        private const val CHANNEL_ID = "qr_bubble"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_TOGGLE = 2002
        private const val REQUEST_STOP = 2003
        private const val BUBBLE_COLOR = 0xFF2166E8.toInt()
        private const val DRAWER_WIDTH_DP = 320
        private const val DRAWER_IN_MS = 150L
        private const val DRAWER_OUT_MS = 100L
        private const val SNAP_MS = 180L
        private const val REOPEN_GUARD_MS = 300L

        /** Starts (or re-shows) the bubble. Safe to call repeatedly, e.g. every time the app opens. */
        fun start(context: Context, action: String = ACTION_START) {
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java).setAction(action))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BubbleService::class.java).setAction(ACTION_STOP))
        }
    }
}
