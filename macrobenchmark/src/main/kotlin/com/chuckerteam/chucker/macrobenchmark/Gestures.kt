package com.chuckerteam.chucker.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.UiDevice

/** Swipes the detail pager one page to the next (`forward`) or previous tab. */
internal fun MacrobenchmarkScope.swipeTab(forward: Boolean) {
    val y = device.heightAt(TAB_SWIPE_Y)
    val left = device.widthAt(TAB_SWIPE_LEFT)
    val right = device.widthAt(TAB_SWIPE_RIGHT)
    if (forward) device.swipe(right, y, left, y, SWIPE_STEPS) else device.swipe(left, y, right, y, SWIPE_STEPS)
    device.waitForIdle()
}

/** Flings the visible payload body down [FLINGS] times, then back up. */
internal fun MacrobenchmarkScope.flingBody() {
    val x = device.widthAt(CENTER)
    val bottom = device.heightAt(BODY_FLING_BOTTOM)
    val top = device.heightAt(BODY_FLING_TOP)
    repeat(FLINGS) {
        device.swipe(x, bottom, x, top, FLING_STEPS)
        device.waitForIdle()
    }
    repeat(FLINGS) {
        device.swipe(x, top, x, bottom, FLING_STEPS)
        device.waitForIdle()
    }
}

internal fun UiDevice.widthAt(fraction: Float): Int = (displayWidth * fraction).toInt()

internal fun UiDevice.heightAt(fraction: Float): Int = (displayHeight * fraction).toInt()

// device.swipe moves ~5 ms per step: few steps = fast fling, more steps = a page swipe.
private const val FLING_STEPS = 5
private const val SWIPE_STEPS = 20

// Gesture positions as fractions of the screen size.
internal const val GESTURE_MARGIN = 0.2f
private const val CENTER = 0.5f
private const val TAB_SWIPE_Y = 0.6f
private const val TAB_SWIPE_LEFT = 0.1f
private const val TAB_SWIPE_RIGHT = 0.9f
private const val BODY_FLING_TOP = 0.25f
private const val BODY_FLING_BOTTOM = 0.8f
