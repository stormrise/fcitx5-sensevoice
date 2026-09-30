package com.fcitx5sensevoice

import android.app.Activity
import android.graphics.Color
import android.os.Build
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

fun Activity.configureEdgeToEdge(rootView: View) {
    window.configureDisplayCutout()
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        rootView.applySystemBarPadding(rootView.viewPadding())
    } else {
        @Suppress("DEPRECATION")
        rootView.fitsSystemWindows = true
    }
}

fun Window.configureDisplayCutout() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
