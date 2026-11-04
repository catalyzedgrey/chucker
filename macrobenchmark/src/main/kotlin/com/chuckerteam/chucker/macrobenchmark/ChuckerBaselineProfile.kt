package com.chuckerteam.chucker.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile for the Chucker UI. Run on the `nonMinifiedBenchmark`
 * variant, then `./gradlew :macrobenchmark:copyChuckerBaselineProfile` to keep only the
 * `com.chuckerteam.chucker` rules and write them to `library/src/main/baseline-prof.txt`.
 */
@RunWith(AndroidJUnit4::class)
class ChuckerBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            seedTraffic(seedCount = 100)
            openChuckerList()
            flingList()
            openLargeTransaction()
            swipeTab(forward = true)
            swipeTab(forward = true)
            flingBody()
            swipeTab(forward = false)
            swipeTab(forward = false)
        }
}
