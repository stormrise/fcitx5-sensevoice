package com.fcitx5sensevoice

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager

data class PaddingValues(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun WindowInsets.systemBarPadding(): PaddingValues {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        val insets = getInsets(types)
        return PaddingValues(insets.left, insets.top, insets.right, insets.bottom)
    }
    @Suppress("DEPRECATION")
    return PaddingValues(
        systemWindowInsetLeft,
        systemWindowInsetTop,
        systemWindowInsetRight,
        systemWindowInsetBottom,
    )
}

fun View.applySystemBarPadding(base: PaddingValues = viewPadding()) {
    val initial = base
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.systemBarPadding()
        view.setPadding(
            initial.left + bars.left,
            initial.top + bars.top,
            initial.right + bars.right,
            initial.bottom + bars.bottom,
        )
        insets
    }
    requestApplyInsets()
}

fun View.applyImePanelInsets() {
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.systemBarPadding()
        view.setPadding(bars.left, 0, bars.right, bars.bottom)
        insets
    }
    requestApplyInsets()
}

fun View.viewPadding(): PaddingValues =
    PaddingValues(paddingLeft, paddingTop, paddingRight, paddingBottom)

// enableEdgeToEdge() handles window flags and transparent system bars on every API level,
// so the bars keep the page background instead of the theme default.
fun ComponentActivity.configureEdgeToEdge(rootView: View) {
    enableEdgeToEdge()
    window.configureDisplayCutout()
    rootView.applySystemBarPadding(rootView.viewPadding())
}

fun Window.configureDisplayCutout() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
