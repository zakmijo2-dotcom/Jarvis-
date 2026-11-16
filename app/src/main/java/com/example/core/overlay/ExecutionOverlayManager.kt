package com.example.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.core.tools.AccessibilityBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExecutionHudState(
    val active: Boolean = false,
    val stepIndex: Int = 0,
    val maxSteps: Int = 0,
    val label: String = "",
    val detail: String = "",
    val overlayAttached: Boolean = false
)

/**
 * Non-intrusive autonomous task execution HUD inspired by OpenPhoneUse OverlayManager and Juyu OverlayController.
 * Uses TYPE_ACCESSIBILITY_OVERLAY when AccessibilityService is bound (requiring zero SYSTEM_ALERT_WINDOW permission),
 * hides itself cleanly around screenshot capture, and exposes live StateFlow updates for the in-app Chat HUD.
 */
class ExecutionOverlayManager(
    private val context: Context
) {
    private val _hudState = MutableStateFlow(ExecutionHudState())
    val hudState: StateFlow<ExecutionHudState> = _hudState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: LinearLayout? = null
    private var titleTextView: TextView? = null
    private var stopCallback: (() -> Unit)? = null

    fun showStep(
        stepIndex: Int,
        maxSteps: Int,
        label: String,
        detail: String = "",
        enableFloatingWindow: Boolean = true,
        onStopRequested: (() -> Unit)? = null
    ) {
        if (onStopRequested != null) {
            stopCallback = onStopRequested
        }
        val text = if (maxSteps > 0) "JARVIS · Step $stepIndex/$maxSteps · $label" else "JARVIS · $label"
        _hudState.value = ExecutionHudState(
            active = true,
            stepIndex = stepIndex,
            maxSteps = maxSteps,
            label = label,
            detail = detail,
            overlayAttached = enableFloatingWindow && AccessibilityBridge.getAttachedService() != null
        )

        if (!enableFloatingWindow) {
            removeFloatingWindow()
            return
        }
        val a11yService = AccessibilityBridge.getAttachedService() ?: return
        mainHandler.post {
            runCatching {
                val wm = a11yService.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                if (overlayView == null) {
                    val created = buildOverlayView(a11yService)
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                        },
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = dpToPx(36)
                    }
                    wm.addView(created, params)
                    overlayView = created
                }
                titleTextView?.text = text.take(56)
                overlayView?.visibility = View.VISIBLE
            }
        }
    }

    fun setHiddenForCapture(hidden: Boolean) {
        mainHandler.post {
            overlayView?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
    }

    fun hide() {
        _hudState.value = ExecutionHudState(active = false)
        stopCallback = null
        removeFloatingWindow()
    }

    private fun removeFloatingWindow() {
        val view = overlayView ?: return
        val a11yService = AccessibilityBridge.getAttachedService()
        overlayView = null
        titleTextView = null
        mainHandler.post {
            runCatching {
                val wm = (a11yService ?: context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeViewImmediate(view)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildOverlayView(hostContext: Context): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#E6151922"))
            cornerRadius = dpToPx(20).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
        }
        val container = LinearLayout(hostContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(8), dpToPx(6))
        }
        val label = TextView(hostContext).apply {
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "JARVIS · Running"
        }
        titleTextView = label
        val stopBtn = TextView(hostContext).apply {
            setTextColor(Color.parseColor("#F87171"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "  STOP"
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                stopCallback?.invoke()
                hide()
            }
        }
        container.addView(label)
        container.addView(stopBtn)
        return container
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
