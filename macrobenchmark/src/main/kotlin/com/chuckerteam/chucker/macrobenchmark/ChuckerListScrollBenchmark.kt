package com.chuckerteam.chucker.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame timing of the Chucker transaction list. */
@RunWith(AndroidJUnit4::class)
class ChuckerListScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Idle database: pure list scroll cost. */
    @Test
    fun scrollList() = measureListFling(liveCount = 0)

    /**
     * The app keeps recording a transaction every 50 ms while the list is flung. Every write
     * makes Room emit a new list, which is where unstable row parameters hurt.
     */
    @Test
    fun scrollListWithLiveTraffic() = measureListFling(liveCount = 300)

    private fun measureListFling(liveCount: Int) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 5,
            setupBlock = {
                seedTraffic(seedCount = 300, liveCount = liveCount, liveIntervalMs = 50)
                openChuckerList()
            },
        ) {
            flingList()
        }
}
