package com.fcitx5sensevoice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @author stormrise
 * @date 2026/09/30
 */
class SystemUiTest {
    @Test
    fun paddingValuesStoresInsets() {
        val padding = PaddingValues(12, 24, 12, 40)
        assertEquals(12, padding.left)
        assertEquals(24, padding.top)
        assertEquals(40, padding.bottom)
    }
}
