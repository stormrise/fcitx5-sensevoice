package com.fcitx5sensevoice

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class PaddingValues(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

// WindowInsetsCompat works from API 28 up and avoids the framework systemWindowInset* getters deprecated in API 30.
private fun WindowInsetsCompat.barInsets(): Insets =
    getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

fun View.applySystemBarPadding(base: PaddingValues = viewPadding()) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.barInsets()
        view.setPadding(
            base.left + bars.left,
            base.top + bars.top,
            base.right + bars.right,
            base.bottom + bars.bottom,
        )
        insets
    }
    requestApplyInsets()
}

fun View.applyImePanelInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.barInsets()
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
