package com.akash.qrdrawer.bubble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.akash.qrdrawer.data.DrawerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts the bubble after reboot when both the bubble and "Start on boot" are on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_QUICKBOOT) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = DrawerPreferences(context).settings.first()
                if (settings.bubbleEnabled && settings.startOnBoot && Settings.canDrawOverlays(context)) {
                    BubbleService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
