package com.zai.callerid.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.zai.callerid.data.ContactsRepo
import com.zai.callerid.data.NumberUtils
import com.zai.callerid.data.SpamDb
import com.zai.callerid.data.SpamNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Truecaller-style floating popup shown over the incoming call screen.
 * Shows number, contact name, spam verdict; can reject / block / dismiss.
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_NUMBER = "extra_number"

        fun show(context: Context, number: String?) {
            context.startService(
                Intent(context, OverlayService::class.java)
                    .putExtra(EXTRA_NUMBER, number ?: "")
            )
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rootView: LinearLayout? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        var number = intent?.getStringExtra(EXTRA_NUMBER).orEmpty()
        if (number.isBlank()) number = IncomingHolder.fresh() ?: ""
        lookupAndShow(number)
        return START_NOT_STICKY
    }

    private fun lookupAndShow(rawNumber: String) {
        val appContext = applicationContext
        scope.launch {
            val key = NumberUtils.matchKey(rawNumber)
            val spam = if (key.isNotBlank()) {
                SpamDb.get(appContext).dao().getByKeySync(key)
            } else null
            val name = if (rawNumber.isNotBlank()) ContactsRepo.nameFor(appContext, rawNumber) else null
            withContext(Dispatchers.Main) { attachOverlay(rawNumber, name, spam) }
        }
    }

    private fun attachOverlay(rawNumber: String, name: String?, spam: SpamNumber?) {
        if (rootView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val ctx = this
        val pad = dp(18)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF7FFFFFF.toInt())
                setStroke(dp(1), 0x22000000)
            }
            setPadding(pad, pad, pad, pad)
            elevation = dp(10).toFloat()
        }

        val title = TextView(ctx).apply {
            text = if (spam != null && spam.blocked) "⚠ Incoming call — SPAM ALERT" else "Incoming call"
            setTextColor(if (spam != null && spam.blocked) 0xFFD32F2F.toInt() else 0xFF666666.toInt())
            textSize = 14f
        }
        card.addView(title)

        val numberView = TextView(ctx).apply {
            text = NumberUtils.normalize(rawNumber).ifBlank { "Unknown number" }
            setTextColor(0xFF111111.toInt())
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(numberView)

        val subtitle = TextView(ctx).apply {
            text = when {
                spam != null -> "🚫 Reported as spam — \"${spam.label}\""
                name != null -> name
                else -> "No caller information"
            }
            setTextColor(if (spam != null) 0xFFD32F2F.toInt() else 0xFF128C7E.toInt())
            textSize = 16f
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(subtitle)

        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)

            fun button(label: String, bgColor: Int, action: (String) -> Unit): Button =
                Button(ctx).apply {
                    text = label
                    setBackgroundColor(bgColor)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginEnd = dp(6) }
                    setOnClickListener { action(rawNumber) }
                }

            addView(button("Reject", 0xFFD32F2F.toInt()) { _ -> endCall() })
            addView(button("Block", 0xFF7B1FA2.toInt()) { n -> blockAndEnd(n) })
            addView(button("Dismiss", 0xFF757575.toInt()) { _ -> dismiss() })
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(60)
            horizontalMargin = 0.05f
        }

        try {
            wm.addView(card, params)
            rootView = card
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun blockAndEnd(rawNumber: String) {
        val key = NumberUtils.matchKey(rawNumber)
        if (key.isNotBlank()) {
            scope.launch {
                SpamDb.get(applicationContext).dao().upsert(
                    SpamNumber(number = key, label = "Blocked from popup", blocked = true)
                )
            }
        }
        endCall()
    }

    private fun endCall() {
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        } catch (e: SecurityException) {
            // ANSWER_PHONE_CALLS not granted — user can reject manually
        } catch (e: Exception) {
            // no active call
        }
        dismiss()
    }

    private fun dismiss() {
        main.post {
            rootView?.let { v ->
                try {
                    windowManager?.removeView(v)
                } catch (e: Exception) {
                    // already removed
                }
            }
            rootView = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        rootView?.let { v ->
            try {
                windowManager?.removeView(v)
            } catch (e: Exception) {
                // already removed
            }
        }
        rootView = null
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
