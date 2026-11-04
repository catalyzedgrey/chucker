package com.chuckerteam.chucker.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Cold start of the host process straight into the Chucker transaction list (200 transactions). */
@RunWith(Parameterized::class)
class ChuckerStartupBenchmark(
    private val compilationMode: CompilationMode,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun chuckerListColdStartup() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { seedTraffic(seedCount = 200) },
        ) {
            startActivityAndWait(chuckerListIntent())
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<CompilationMode> =
            listOf(
                CompilationMode.None(),
                CompilationMode.Partial(BaselineProfileMode.Require),
            )
    }
}
