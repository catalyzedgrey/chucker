package com.chuckerteam.chucker.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame timing of the transaction detail screen, opened on a large JSON response. */
@RunWith(AndroidJUnit4::class)
class ChuckerTransactionBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Overview → Request → Response → Request → Overview, twice. Pays for any tab rebuilds. */
    @Test
    fun swipeTransactionTabs() =
        measureDetail(setup = {}) {
            repeat(2) {
                swipeTab(forward = true)
                swipeTab(forward = true)
                swipeTab(forward = false)
                swipeTab(forward = false)
            }
        }

    /** Flings the formatted large JSON response body. */
    @Test
    fun scrollLargeResponseBody() =
        measureDetail(setup = { openResponseBody() }) {
            flingBody()
        }

    private fun measureDetail(
        setup: MacrobenchmarkScope.() -> Unit,
        measure: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            seedTraffic(seedCount = 50)
            openChuckerList()
            openLargeTransaction()
            setup()
        },
        measureBlock = measure,
    )
}
