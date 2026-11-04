package com.chuckerteam.chucker.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.chuckerteam.chucker.sample"

private const val TRAFFIC_ACTIVITY = "$TARGET_PACKAGE.benchmark.BenchmarkTrafficActivity"
private const val CHUCKER_LIST_ACTIVITY = "com.chuckerteam.chucker.internal.ui.MainActivity"
private const val SEED_DONE_TEXT = "seed-done"
private const val LARGE_JSON_ROW_TEXT = "/benchmark/large-json"
private const val SEEDED_ROW_TEXT = "/benchmark/"
private const val SEED_TIMEOUT_MS = 60_000L
private const val UI_TIMEOUT_MS = 10_000L
internal const val FLINGS = 3
private const val VISIBLE_TEXTS_IN_ERRORS = 12

/**
 * Wipes Chucker's database and records [seedCount] fake transactions (no network involved).
 * When [liveCount] > 0 the app keeps recording one transaction every [liveIntervalMs] in the
 * background, so the Chucker UI is measured while its database is being written to.
 * The newest row is always `GET /benchmark/large-json`.
 */
internal fun MacrobenchmarkScope.seedTraffic(
    seedCount: Int,
    liveCount: Int = 0,
    liveIntervalMs: Long = 100,
) {
    // Deleting the database is only safe while no process has it open.
    killProcess()
    // Not startActivityAndWait: the seeder keeps its main thread busy, so its
    // frame-based launch detection is unreliable for it. Completion is detected via its text.
    device.executeShellCommand(
        // -W: wait for the launch; -f NEW_TASK|CLEAR_TASK: always a fresh instance with these extras.
        "am start -W -f 0x10008000 -n $TARGET_PACKAGE/$TRAFFIC_ACTIVITY " +
            "--ez clear true --ei seedCount $seedCount " +
            "--ei liveCount $liveCount --el liveIntervalMs $liveIntervalMs",
    )
    checkNotNull(device.wait(Until.findObject(By.text(SEED_DONE_TEXT)), SEED_TIMEOUT_MS)) {
        "Seeding $seedCount transactions did not finish in time"
    }
}

// Chucker's list is singleTask with its own task affinity: clear that task so every launch
// creates a new activity instead of resurfacing the previous iteration's task.
internal fun chuckerListIntent(): Intent =
    Intent()
        .setClassName(TARGET_PACKAGE, CHUCKER_LIST_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

/** Opens the Chucker transaction list and waits until the seeded rows are drawn. */
internal fun MacrobenchmarkScope.openChuckerList() {
    startActivityAndWait(chuckerListIntent())
    waitForList()
}

internal fun MacrobenchmarkScope.waitForList(): UiObject2 {
    // Any seeded row: with live traffic the large JSON row is quickly pushed off screen.
    checkNotNull(device.wait(Until.findObject(By.textContains(SEEDED_ROW_TEXT)), UI_TIMEOUT_MS)) {
        "Chucker list did not show the seeded transactions. On screen: ${visibleTexts()}"
    }
    return checkNotNull(device.findObject(By.scrollable(true))) { "Transaction list is not scrollable" }
}

/** Flings the transaction list down [FLINGS] times, then back to the top. */
internal fun MacrobenchmarkScope.flingList() {
    val list = waitForList()
    list.setGestureMargin(device.widthAt(GESTURE_MARGIN))
    repeat(FLINGS) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(FLINGS) {
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** From the list, opens the large JSON transaction and waits for the detail screen. */
internal fun MacrobenchmarkScope.openLargeTransaction() {
    val row =
        checkNotNull(device.wait(Until.findObject(By.textContains(LARGE_JSON_ROW_TEXT)), UI_TIMEOUT_MS)) {
            "Large JSON transaction row not found"
        }
    row.click()
    checkNotNull(device.wait(Until.findObject(By.text("OVERVIEW")), UI_TIMEOUT_MS)) {
        "Transaction detail did not open"
    }
}

/** Selects the Response tab and waits for the large JSON body to be rendered. */
internal fun MacrobenchmarkScope.openResponseBody() {
    checkNotNull(device.findObject(By.text("RESPONSE"))) { "Response tab not found" }.click()
    checkNotNull(device.wait(Until.findObject(By.textContains("_id")), UI_TIMEOUT_MS)) {
        "Response body did not render"
    }
}

/** First texts currently on screen, to make UI-wait failures diagnosable. */
private fun MacrobenchmarkScope.visibleTexts(): List<String> =
    device.findObjects(By.textStartsWith("")).mapNotNull { it.text }.take(VISIBLE_TEXTS_IN_ERRORS)
