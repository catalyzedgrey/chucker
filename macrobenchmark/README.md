# :macrobenchmark

Macrobenchmarks and the Baseline Profile generator for the Chucker UI. They run against `:sample` in
the `benchmark` build type. That build type is release-like, has R8 on, and bundles the real
`:library`, not `:library-no-op`.

Chucker is filled with deterministic, offline traffic by `BenchmarkTrafficActivity`
(`sample/src/benchmarkShared`). Requests go through the real `ChuckerInterceptor` and are answered
locally, so no network is needed.

The rows are paths like `/benchmark/item/N`, with mixed 2xx/3xx/4xx/5xx statuses. The newest row is
always `GET /benchmark/large-json`, which carries a large JSON body.

Run on a **physical device** with the screen unlocked and battery at 25% or more. Emulator numbers
aren't representative.

## Benchmarks

| Class | Test | Measures |
| --- | --- | --- |
| `ChuckerStartupBenchmark` | `chuckerListColdStartup[None / BaselineProfile]` | Cold start straight into the Chucker list (200 transactions) |
| `ChuckerListScrollBenchmark` | `scrollList` | Flinging the list (300 transactions), idle database |
| `ChuckerListScrollBenchmark` | `scrollListWithLiveTraffic` | Same fling, while a new transaction is recorded every 50 ms |
| `ChuckerTransactionBenchmark` | `swipeTransactionTabs` | Swiping Overview ↔ Request ↔ Response on the large JSON transaction |
| `ChuckerTransactionBenchmark` | `scrollLargeResponseBody` | Flinging the formatted large JSON response body |

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

Results are written as JSON to
`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/`.
Perfetto traces for each iteration are saved in the same folder.

To run one class, add `-Pandroid.testInstrumentationRunnerArguments.class=com.chuckerteam.chucker.macrobenchmark.<Class>`.
Pass one class per invocation. With a comma-separated list, only the first class ran on this setup.

To check that the journeys still work without measuring (1 iteration each), add
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true`.

## Baseline Profile

`:library` ships `library/src/main/baseline-prof.txt` in its AAR, so every host app gets it.
To regenerate it, run the generator on the non-minified variant so the rules keep their real
names, then copy the Chucker rules into the library:

```bash
./gradlew :macrobenchmark:connectedNonMinifiedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

```bash
./gradlew :macrobenchmark:copyChuckerBaselineProfile
```
