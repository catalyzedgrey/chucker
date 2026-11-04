# Compose Performance Audit — 2026-11-04 — `:library` (Compose UI port, `e83a86f`)

Status: **Phase 1 (Measure) and Phase 2 (Diagnose) are done.** No UI code has been changed.

Measurement harness: the `:macrobenchmark` module plus the `benchmark` build type of `:sample` (R8 on, real `:library`). See [macrobenchmark/README.md](../macrobenchmark/README.md).

## Environment
- Compose BOM: 2026.06.01 (Compose UI / Foundation / Material3 from the BOM)
- Compose Compiler: bundled with Kotlin 2.4.21 (Strong Skipping ON, Pausable Composition ON, OptimizeNonSkippingGroups ON)
- Kotlin: 2.4.21
- AGP: 9.4.1, Macrobenchmark 1.5.0
- Device: Samsung SM-S947B, API 37, physical, not rooted (`cpuLocked: false`)
- Conditions: USB fast-charging, battery 27–50%, battery temperature 34.1–40.0 °C (per run in the table below). That's warm enough that some thermal throttling is possible. Phase 3/4 comparisons should be run under the same conditions.

## Baseline (Phase 1)
Raw JSON: [docs/perf-baselines/](perf-baselines/). Per-frame samples and trace paths are stripped; percentiles and per-iteration metrics are kept. Frame metrics are `frameDurationCpuMs`. Overrun is `frameOverrunMs`, where a negative value means the frame was under budget.

| Journey | Metric | Run 2 (37.6–38.6 °C) | Follow-up | Run 1 (35.8–38.2 °C) |
| --- | --- | --- | --- | --- |
| Cold start → Chucker list (200 tx), `CompilationMode.None` | TTID median (min–max) | 148.5 ms (132–169) | 150.4 ms (143–184), run 6 | 151.2 ms (138–184) |
| Cold start → Chucker list, `Partial(BaselineProfile)`* | TTID median (min–max) | (failed) | 141.4 ms (137–180), run 6 | 140.5 ms (131–180) |
| `scrollList`: fling 300 tx, idle DB | CPU P50 / P90 / P99 | 3.4 / 4.8 / 6.7 ms | 3.2 / 4.7 / 6.6 ms, run 4 | (failed) |
| | Overrun P90 / P99 | −2.0 / 0.2 ms | −2.2 / 0.2 ms | |
| `scrollListWithLiveTraffic`: same fling, 1 new tx every 50 ms | CPU P50 / P90 / P99 | (failed) | **3.3 / 5.5 / 8.4 ms**, run 4 | (failed) |
| | Overrun P95 / P99 | | −0.6 / **1.5 ms** | |
| `swipeTransactionTabs`: Overview ↔ Request ↔ Response ×2 | CPU P50 / P90 / P99 | 3.9 / 5.4 / **19.1** ms | | 3.5 / 5.2 / 17.8 ms |
| | Overrun P95 / P99 | 1.0 / **14.4** ms | | 1.5 / 11.8 ms |
| `scrollLargeResponseBody`: fling large JSON | CPU P50 / P90 / P99 | 4.9 / 8.3 / 12.6 ms | | 4.8 / 8.8 / 17.3 ms |
| | Overrun P90 / P99 | 1.7 / 5.1 ms | | 2.2 / 11.3 ms |

Run 4 (list) was at 34.1–37.5 °C. Run 6 (startup) was at 39.5–40.0 °C.

\* Only the Baseline Profiles shipped by dependencies (Compose, etc.) are present. Chucker ships none yet, so this row is the "before" for the Phase 4 profile.

How to read this:
- **Tab swiping has the worst tail:** P99 CPU of 18–19 ms, overrunning the frame budget by 12–14 ms, while P50 is about 4 ms. That fits hotspot #2 (payload tabs rebuilt on each swipe), so it's the first fix to make.
- **Scrolling the large response body** runs close to budget at P90 (+1.7–2.2 ms overrun), which fits hotspot #3 (one `SelectionContainer` per line).
- **Live traffic makes list scrolling measurably worse, but only a little on this device.** Compared with the idle run in the same session: P90 +0.8 ms (4.7 → 5.5), P99 +1.8 ms (6.6 → 8.4), and P99 overrun goes from 0.2 to 1.5 ms. That confirms hotspot #1 (every visible row recomposes on each DB write), but on a flagship it's a small cost. It would matter more on low-end devices or with heavier traffic. Fix #1 is still worth doing because it's a one-line change with no behaviour change, but it's second in priority after #2.
- **Cold start** is about 150 ms, and dependency profiles already save about 9 ms. The remaining gain from Chucker's own profile is measured in Phase 4.

Baseline Profile present: only dependency profiles. Chucker's own `baseline-prof.txt` hasn't been generated yet.

## Diagnosis (Phase 2)

Source: Compose Compiler reports for `:library:compileReleaseKotlin`. They were generated with a throwaway init script, and nothing was committed.

| Metric | Value |
| --- | --- |
| Named (non-lambda) composables | 22. All restartable ones (18) are skippable thanks to Strong Skipping |
| Restartable-not-skippable composables | 0 |
| Parameters with unstable or uncertain types | 12 (listed below) |
| Unstable / uncertain classes in module | 27 / 17 (most are non-UI: OkHttp sinks, Room impls, HAR models) |
| Memoized lambdas | 108 / 108 |

The skip rate looks perfect, but with Strong Skipping an unstable parameter is compared with `===` (identity) instead of `equals`. The question that matters is whether the instances stay the same across emissions. For the list screen they don't.

Unstable or uncertain parameters reported:

| Composable | Parameter | Matters? |
| --- | --- | --- |
| `TransactionListItem` | `transaction: HttpTransactionTuple` | **Yes, hot path** (see #1) |
| `PayloadList` | `items: List<TransactionPayloadItem>`, `rowVersions: Map<Int,Int>` | Low. Same instances across recompositions |
| `PayloadImage` | `item: ImageItem` | Low |
| `SearchSummaryBar` | `summary: SearchPosition` | No. Stable class; reported due to nesting |
| `TransactionListScreen`, `TransactionScreen`, `TransactionOverviewScreen`, `TransactionPayloadScreen` | `viewModel` | No. Screen roots, same instance |
| `TransactionListScreen` / `linkedText` | `CharSequence` | No. One-off |
| `ChuckerConfirmDialog` | `dialogData: DialogData` | No |

### Top hotspots (ranked by frequency × cost)

1. **Every visible list row recomposes on every DB write.** `TransactionListScreen.kt` / `TransactionListItem.kt`
   - `HttpTransactionTuple` is a `data class` made of `var`s, so the compiler treats it as unstable and compares it by identity.
   - Room's `LiveData<List<HttpTransactionTuple>>` re-queries and emits brand-new instances on every table change. Chucker writes at least twice per HTTP call (request, then response), so an app making N calls per second re-runs all visible rows about 2N times per second, even though only one row changed.
   - The old `ListAdapter` + `TransactionDiffCallback` re-bound only the rows that changed, so this is a regression from the View version.
   - Each recomposition of a row also re-parses the URL (`getFormattedPath` → `toHttpUrlOrNull` + `FormattedUrl`), allocates a new `DateFormat.getTimeInstance()`, formats the byte count, and resolves 6–8 `colorResource` lookups.
   - The tuple is never mutated anywhere in the codebase. Changing `var` to `val` makes it stable, so Strong Skipping falls back to the data-class `equals` and unchanged rows skip. → `stability/stabilizing-compose-types`

2. **The detail pager recreates payload tabs on every swipe.** `TransactionScreen.kt` (`HorizontalPager`)
   - The old `ViewPager` kept one page on each side alive (default `offscreenPageLimit = 1`).
   - `HorizontalPager` defaults to `beyondViewportPageCount = 0`. All of `TransactionPayloadScreen`'s state lives in `remember` (items, the search controller, `rowVersions`), so swiping away and back throws it away. The screen then re-runs `PayloadItemsBuilder.build`: JSON/XML formatting, span creation and line splitting of the whole body. For large bodies the spinner shows again on every swipe.
   - Fix: `beyondViewportPageCount = 1` restores the old behaviour. The longer-term fix is to hoist the built items into `TransactionViewModel`. → `lists/optimizing-lazy-layouts` (Pager is a lazy layout)

3. **One `SelectionContainer` per body line.** `TransactionPayloadScreen.kt` `PayloadList`
   - Every `BodyLineItem` and `HeaderItem` row is wrapped in its own `SelectionContainer`. Each one registers a selection registrar and selection modifiers, which adds composition and layout cost to every row of a long body during scroll.
   - This needs measuring before changing anything. One container around the `LazyColumn` is cheaper, but selection across lazy items has known limits, so it's a behaviour trade-off, not a free win.

4. **Each search keystroke invalidates every visible body row.** `TransactionPayloadScreen.kt`
   - `rowVersions` is a `SnapshotStateMap`, and each row reads `rowVersions[index]` inside the `items` lambda. Snapshot reads on a map are tracked for the whole map, so any write recomposes every visible row.
   - `remember(item, version)` keeps the `toAnnotatedString()` conversion limited to rows that changed, so the cost is restart and skip, not conversion. Medium-low priority.
   - Separately, `PayloadSearchController.onQueryChanged` highlights all lines on the main thread before the 600 ms debounce. The code was moved verbatim and is unchanged from the fragment, so this isn't a regression, but it is the most likely input-lag source for multi-MB bodies.

5. **Small allocations per row.** `TransactionListItem.kt`
   - `DateFormat.getTimeInstance()` runs on every composition, and the status colour helper reads 6 colour resources per row.
   - Mostly made irrelevant once #1 is fixed (rows stop recomposing). Optional: hoist the formatter into a `remember`.

Phase-misplaced reads (animation or scroll state read in composition): **0 found**. No `offset(x.dp)` / `alpha(state.value)` patterns. The pager page is read as `currentPage` in composition, but that changes rarely.

Other things checked and found fine:
- List `key = { it.id }` is present.
- `contentType` isn't needed in the transaction list (one row type).
- The payload list mixes 4 item types with no `contentType`. That's minor, since header, copy and image appear at most once.
- Image bitmaps are converted with `remember(bitmap)`.
- `PayloadItemsBuilder` runs on `Dispatchers.Default`.
- Flows use `snapshotFlow` correctly.
- Effects are keyed correctly.

## Fixes applied (Phase 3)
Each change was measured back-to-back against the code just before it, on the same device in the same session. Perfetto traces were analysed with the Perfetto trace processor.

| # | Skill | Change | Files | Macrobench delta | Outcome |
| - | ----- | ------ | ----- | ---------------- | ------- |
| 2 | lists/optimizing-lazy-layouts | `HorizontalPager(beyondViewportPageCount = 1)` | ui/transaction/TransactionScreen.kt | Swipe: frames > 16 ms per iteration 4,4,2,4,3 → 1,4,4,4,4; janky median 22 → 22; P99 19.5 → 20.8 ms (37.0–39.0 °C) | **Rejected:** no measurable gain |
| 3 | (trace-driven) | One `SelectionContainer` around the payload `LazyColumn`, instead of one per row; `DisableSelection` on the copy row | ui/transaction/TransactionPayloadScreen.kt | Swipe P99 21.0 → **16.4 ms**; frames > 16 ms per iteration 3,4,5,4,4 → 2,2,3,4,3; tab-entry measure pass 18.0–33.6 → **14.4–16.4 ms**; row composition 6.2–10.2 → **4.1–4.5 ms**; body-fling P50 / P90 4.5 / 7.8 → 3.9 / 7.5 ms (37.6–39.3 °C) | **Applied** |
| 1 | stability/stabilizing-compose-types | `HttpTransactionTuple` `var` → `val` | data/entity/HttpTransactionTuple.kt | pending | next |
| 4 | recomposition/deferring-state-reads | per-row version state instead of shared map | ui/transaction/TransactionPayloadScreen.kt | pending | low priority |

**Why fix #2 didn't work.** The traces show each slow swipe frame is a payload tab's `LazyColumn` composing its whole first screen inside one measure pass: about 23 rows (`Compose:recompose` ×23) and 22 text layouts (`TextAnnotatedStringNode:measure`). Keeping the neighbouring tab alive only moves that pass. The neighbour gets built in one go as soon as it becomes adjacent, which is still mid-swipe. The hypothesis in Diagnosis #2 (payload rebuilt on every swipe) doesn't explain the jank.

**Fix #3 changes behaviour.** Selection can now span multiple lines, which wasn't possible with one container per line. Checked on the device:
- long-press selection works
- dragging a handle across rows works
- Copy / Select all appear
- the copy-payload button still works

Lazy-list limitation: a selection can be lost when its rows scroll off screen.

**What's still slow on tab swipes.**
- Text measurement of the first screen: 3–7 ms, unchanged by fix #3.
- One RenderThread frame drawing the new text: `flush commands`, up to about 24 ms.

The next lever is to precompose the first rows before the tab appears: hoist the built payload items above the pager and raise the nested prefetch count (`lists/configuring-lazy-prefetch`).

## Verification (Phase 4)
Not started.

## Open items / follow-ups
1. Phase 3: #3 is applied and #2 was rejected (see the table). Next: #1 (tuple `val`), then precomposing payload rows for tab swipes.
2. For cleaner Phase 3/4 comparisons, run with the battery at 80% or more, or over wireless adb with the cable unplugged, and run before and after back-to-back.
3. Generate Chucker's Baseline Profile (`connectedNonMinifiedBenchmarkAndroidTest` + `copyChuckerBaselineProfile`) in Phase 4, after the fixes.
4. CI stability gate (`compose-stability-analyzer` → `stabilityDump` + `stabilityCheck`) after fix #1, so the tuple can't regress to unstable.
