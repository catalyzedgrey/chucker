package com.chuckerteam.chucker.api

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference

/**
 * Interceptors and collectors usually live as long as the app, so building them with an Activity
 * must not keep that Activity alive (LeakCanary caught the database holding one).
 */
@RunWith(RobolectricTestRunner::class)
internal class ContextRetentionTest {
    @After
    fun tearDown() = RepositoryProvider.close()

    @Test
    fun `interceptor, collector and database keep only the application context`() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val (interceptor, collector, shortLivedContext) = buildWithShortLivedContext(application)

        awaitCollected(shortLivedContext)

        assertThat(shortLivedContext.get()).isNull()
        // Keep both reachable until after the check.
        assertThat(interceptor).isNotNull()
        assertThat(collector).isNotNull()
    }

    /** Built in its own frame so no local variable keeps the wrapper reachable. */
    private fun buildWithShortLivedContext(application: Context): Triple<ChuckerInterceptor, ChuckerCollector, WeakReference<Context>> {
        // Stands in for an Activity: a separate Context whose applicationContext is the app.
        val activityLike = ContextWrapper(application)
        val collector = ChuckerCollector(activityLike, showNotification = false)
        val interceptor =
            ChuckerInterceptor
                .Builder(activityLike)
                .collector(collector)
                .createShortcut(false)
                .build()
        return Triple(interceptor, collector, WeakReference(activityLike))
    }

    private fun awaitCollected(reference: WeakReference<*>) {
        repeat(GC_ATTEMPTS) {
            if (reference.get() == null) return
            Runtime.getRuntime().gc()
            Thread.sleep(GC_PAUSE_MS)
        }
    }

    private companion object {
        const val GC_ATTEMPTS = 10
        const val GC_PAUSE_MS = 50L
    }
}
