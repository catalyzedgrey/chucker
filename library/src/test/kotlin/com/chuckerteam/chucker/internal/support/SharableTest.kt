package com.chuckerteam.chucker.internal.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class SharableTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun sharableOf(text: String) =
        object : Sharable {
            override fun toSharableContent(context: Context): Source = Buffer().writeUtf8(text)
        }

    private suspend fun share(text: String): Intent? =
        sharableOf(text).shareAsUtf8Text(
            activity = activity,
            intentTitle = "title",
            intentSubject = "subject",
            fallbackFileName = "shared.txt",
            clipDataLabel = "label",
        )

    private fun Intent.target(): Intent {
        val target = IntentCompat.getParcelableExtra(this, Intent.EXTRA_INTENT, Intent::class.java)
        return checkNotNull(target) { "Not a chooser intent" }
    }

    @Test
    fun `short text is shared inline`() =
        runTest {
            val text = "curl -X GET http://localhost/getUsers"

            val target = checkNotNull(share(text)).target()

            assertThat(target.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text)
            assertThat(IntentCompat.getParcelableExtra(target, Intent.EXTRA_STREAM, Uri::class.java)).isNull()
        }

    @Test
    fun `text at the inline limit is still shared inline`() =
        runTest {
            val text = "a".repeat(MAX_INLINE_SHARE_TEXT_LENGTH)

            val target = checkNotNull(share(text)).target()

            assertThat(target.getStringExtra(Intent.EXTRA_TEXT)).hasLength(MAX_INLINE_SHARE_TEXT_LENGTH)
        }

    @Test
    fun `text over the inline limit is shared as a file with the same content`() =
        runTest {
            val text = "b".repeat(MAX_INLINE_SHARE_TEXT_LENGTH + 1)

            val target = checkNotNull(share(text)).target()

            assertThat(target.getStringExtra(Intent.EXTRA_TEXT)).isNull()
            val uri = checkNotNull(IntentCompat.getParcelableExtra(target, Intent.EXTRA_STREAM, Uri::class.java))
            assertThat(uri.lastPathSegment).isEqualTo("shared.txt")
            assertThat(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            val shared = activity.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            assertThat(shared).isEqualTo(text)
        }
}
