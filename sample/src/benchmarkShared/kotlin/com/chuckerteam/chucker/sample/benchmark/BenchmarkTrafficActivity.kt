package com.chuckerteam.chucker.sample.benchmark

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.sample.LARGE_JSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Benchmark-only entry point that fills Chucker with deterministic traffic, with no network.
 * Requests go through the real [ChuckerInterceptor] and are answered by [FakeServer].
 *
 * Extras:
 * - [EXTRA_CLEAR]: delete the Chucker database first. The process must be fresh, so the
 *   benchmark kills it before launching this activity.
 * - [EXTRA_SEED_COUNT]: number of transactions recorded before [DONE_TEXT] is shown.
 *   The last (newest, top of the list) is always `GET /benchmark/large-json` with a large JSON body.
 * - [EXTRA_LIVE_COUNT] / [EXTRA_LIVE_INTERVAL_MS]: keep recording this many extra transactions
 *   in the background after seeding, one per interval, to simulate a busy host app while
 *   the Chucker UI is on screen.
 */
class BenchmarkTrafficActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "seeding" }
        setContentView(status)

        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) {
            deleteDatabase(CHUCKER_DB_NAME)
        }
        val seedCount = intent.getIntExtra(EXTRA_SEED_COUNT, DEFAULT_SEED_COUNT)
        val liveCount = intent.getIntExtra(EXTRA_LIVE_COUNT, 0)
        val liveIntervalMs = intent.getLongExtra(EXTRA_LIVE_INTERVAL_MS, DEFAULT_LIVE_INTERVAL_MS)
        val client = createClient()

        processScope.launch {
            withContext(Dispatchers.IO) {
                repeat(seedCount - 1) { client.call("/benchmark/item/$it?page=${it % PAGES}") }
                // Recorded last so it is the newest row, at the top of the list.
                client.call(LARGE_JSON_PATH)
                // Chucker persists asynchronously; give the last writes time to land.
                delay(SETTLE_DELAY_MS)
            }
            status.text = DONE_TEXT
            withContext(Dispatchers.IO) {
                repeat(liveCount) {
                    delay(liveIntervalMs)
                    client.call("/benchmark/live/$it")
                }
            }
        }
    }

    private fun createClient(): OkHttpClient {
        val collector = ChuckerCollector(applicationContext, showNotification = false)
        return OkHttpClient
            .Builder()
            .addInterceptor(
                ChuckerInterceptor
                    .Builder(applicationContext)
                    .collector(collector)
                    .createShortcut(false)
                    .build(),
            ).addInterceptor(FakeServer)
            .build()
    }

    private fun OkHttpClient.call(path: String) {
        val request = Request.Builder().url("https://benchmark.chucker.test$path").build()
        newCall(request).execute().use { it.body.string() }
    }

    /** Terminal interceptor answering every request locally with a varied status and JSON body. */
    private object FakeServer : Interceptor {
        private val json = "application/json".toMediaType()
        private val statusCodes = intArrayOf(200, 200, 200, 201, 204, 301, 404, 500)

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val path = request.url.encodedPath
            val isLarge = path == LARGE_JSON_PATH
            val code = if (isLarge) 200 else statusCodes[path.hashCode().mod(statusCodes.size)]
            val body =
                if (isLarge) {
                    LARGE_JSON
                } else {
                    """{"path":"$path","ok":${code < 400},"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}]}"""
                }
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(code)
                .message("Benchmark")
                .header("Content-Type", json.toString())
                .body(body.toResponseBody(json))
                .build()
        }
    }

    companion object {
        const val EXTRA_CLEAR = "clear"
        const val EXTRA_SEED_COUNT = "seedCount"
        const val EXTRA_LIVE_COUNT = "liveCount"
        const val EXTRA_LIVE_INTERVAL_MS = "liveIntervalMs"
        const val DONE_TEXT = "seed-done"
        const val LARGE_JSON_PATH = "/benchmark/large-json"

        private const val CHUCKER_DB_NAME = "chucker.db"
        private const val DEFAULT_SEED_COUNT = 200
        private const val DEFAULT_LIVE_INTERVAL_MS = 100L
        private const val SETTLE_DELAY_MS = 500L
        private const val PAGES = 5

        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
