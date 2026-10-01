package com.akash.qrdrawer.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.akash.qrdrawer.data.QrCode
import com.akash.qrdrawer.data.maskUpiId

/** Root of the drawer overlay window; closes itself on Back since the window is focusable. */
@SuppressLint("ViewConstructor")
class DrawerPanel(context: Context, private val onDismiss: () -> Unit) : LinearLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/** Colours and sizes for the drawer, resolved once per build for the current light/dark mode. */
private class DrawerStyle(context: Context) {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val surface = if (dark) 0xFF1B2430.toInt() else 0xFFFFFFFF.toInt()
    val card = if (dark) 0xFF243140.toInt() else 0xFFF3F5F8.toInt()
    val ink = if (dark) 0xFFE8EDF5.toInt() else 0xFF1B2029.toInt()
    val muted = if (dark) 0xFF9AA7B6.toInt() else 0xFF626B78.toInt()
    val accent = if (dark) 0xFF8DB4FF.toInt() else 0xFF2166E8.toInt()
    val ripple = if (dark) 0x33FFFFFF else 0x22000000
}

/**
 * Builds the drawer contents: a header with a "Manage" link and one card per QR
 * (colour stripe, label, bank · masked UPI ID, thumbnail).
 */
class DrawerPanelBuilder(
    private val context: Context,
    private val dp: (Int) -> Int,
) {
    /** Card height in dp; the drawer shows [VISIBLE_CARDS] before scrolling. */
    val cardHeightDp = 64
    val cardGapDp = 6
    val headerHeightDp = 40
    val paddingDp = 10

    fun contentHeightDp(cardCount: Int): Int {
        val visible = cardCount.coerceIn(1, VISIBLE_CARDS)
        return headerHeightDp + visible * cardHeightDp + (visible - 1) * cardGapDp + paddingDp * 2
    }

    fun build(
        qrs: List<QrCode>,
        thumbnail: (QrCode) -> Bitmap?,
        onQrClick: (QrCode) -> Unit,
        onManage: () -> Unit,
        onAddFirst: () -> Unit,
        onDismiss: () -> Unit,
    ): DrawerPanel {
        val style = DrawerStyle(context)
        val panel = DrawerPanel(context, onDismiss).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(paddingDp), dp(paddingDp - 4), dp(paddingDp), dp(paddingDp))
            background = GradientDrawable().apply {
                setColor(style.surface)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(10).toFloat()
            isFocusableInTouchMode = true
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        header.addView(TextView(context).apply {
            text = "My QRs"
            setTextColor(style.muted)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "Manage"
            setTextColor(style.accent)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(8), 0)
            background = rippleBackground(style, 0, dp(12).toFloat())
            isClickable = true
            contentDescription = "Manage QR codes"
            setOnClickListener { onManage() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)))
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(headerHeightDp)))

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (qrs.isEmpty()) {
            list.addView(
                emptyCard(style, onAddFirst),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(cardHeightDp)),
            )
        } else {
            qrs.forEachIndexed { index, qr ->
                list.addView(
                    qrCard(style, qr, thumbnail(qr)) { onQrClick(qr) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(cardHeightDp)).apply {
                        if (index > 0) topMargin = dp(cardGapDp)
                    },
                )
            }
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = qrs.size > VISIBLE_CARDS
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(list)
        }
        val visible = qrs.size.coerceIn(1, VISIBLE_CARDS)
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(visible * cardHeightDp + (visible - 1) * cardGapDp),
            ),
        )
        return panel
    }

    private fun qrCard(style: DrawerStyle, qr: QrCode, thumb: Bitmap?, onClick: () -> Unit): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rippleBackground(style, style.card, dp(14).toFloat())
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "Show ${qr.label} QR"
            setOnClickListener { onClick() }
        }
        card.addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(qr.color)
                cornerRadius = dp(3).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(6), dp(40)).apply {
            marginStart = dp(8)
            marginEnd = dp(10)
        })

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        texts.addView(TextView(context).apply {
            text = if (qr.isDefault) "★ ${qr.label}" else qr.label
            setTextColor(style.ink)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val subtitle = listOf(qr.bank, maskUpiId(qr.upiId)).filter(String::isNotBlank).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                setTextColor(style.muted)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val thumbFrame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        thumbFrame.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Module-sized QR bitmaps must scale without smoothing to stay sharp.
            thumb?.let { setImageDrawable(BitmapDrawable(resources, it).apply { isFilterBitmap = !qr.hasPayload }) }
            contentDescription = null
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        card.addView(thumbFrame, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        return card
    }

    private fun emptyCard(style: DrawerStyle, onClick: () -> Unit): View = TextView(context).apply {
        text = "+  Add your first QR"
        setTextColor(style.accent)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = rippleBackground(style, style.card, dp(14).toFloat())
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun rippleBackground(style: DrawerStyle, color: Int, radius: Float): RippleDrawable {
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = radius
        }
        return RippleDrawable(ColorStateList.valueOf(style.ripple), shape, mask)
    }

    companion object {
        const val VISIBLE_CARDS = 5
    }
}
