package com.chuckerteam.chucker.internal.support

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.buffer
import okio.sink

/**
 * Longest text that is shared inline as `EXTRA_TEXT`. The share intent has to fit in the
 * ~1 MB Binder transaction buffer, and the chooser carries the text several times over:
 * 250 KB of cURL text produced a 768 KB parcel that failed to send, and 800 KB crashed the
 * host app with a TransactionTooLargeException. Anything longer is shared as a file instead.
 */
internal const val MAX_INLINE_SHARE_TEXT_LENGTH = 100_000

internal interface Sharable {
    fun toSharableContent(context: Context): Source
}

internal fun Sharable.toSharableUtf8Content(context: Context) =
    toSharableContent(context)
        .buffer()
        .use(BufferedSource::readUtf8)

/**
 * Shares this content as plain text, or as a text file named [fallbackFileName] when it is
 * longer than [MAX_INLINE_SHARE_TEXT_LENGTH]. Returns null if the fallback file can't be created.
 */
internal suspend fun Sharable.shareAsUtf8Text(
    activity: Activity,
    intentTitle: String,
    intentSubject: String,
    fallbackFileName: String,
    clipDataLabel: String,
): Intent? {
    val content = withContext(Dispatchers.Default) { toSharableUtf8Content(activity) }
    if (content.length > MAX_INLINE_SHARE_TEXT_LENGTH) {
        val uri =
            withContext(Dispatchers.IO) {
                writeSourceToFile(activity, fallbackFileName, Buffer().writeUtf8(content))
            } ?: return null
        return fileShareIntent(activity, uri, intentTitle, intentSubject, clipDataLabel)
    }
    return ShareCompat
        .IntentBuilder(activity)
        .setType("text/plain")
        .setChooserTitle(intentTitle)
        .setSubject(intentSubject)
        .setText(content)
        .createChooserIntent()
}

internal fun Sharable.writeToFile(
    context: Context,
    fileName: String,
): Uri? = writeSourceToFile(context, fileName, toSharableContent(context))

private fun writeSourceToFile(
    context: Context,
    fileName: String,
    fileContent: Source,
): Uri? {
    val cache = context.cacheDir
    if (cache == null) {
        Logger.warn("Failed to obtain a valid cache directory for file export")
        return null
    }

    val file = FileFactory.create(cache, fileName)
    if (file == null) {
        Logger.warn("Failed to create an export file")
        return null
    }

    file.sink().buffer().use { it.writeAll(fileContent) }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.com.chuckerteam.chucker.provider",
        file,
    )
}

internal fun Sharable.shareAsFile(
    activity: Activity,
    fileName: String,
    intentTitle: String,
    intentSubject: String,
    clipDataLabel: String,
): Intent? {
    val uri = writeToFile(activity, fileName) ?: return null
    return fileShareIntent(activity, uri, intentTitle, intentSubject, clipDataLabel)
}

private fun fileShareIntent(
    activity: Activity,
    uri: Uri,
    intentTitle: String,
    intentSubject: String,
    clipDataLabel: String,
): Intent {
    val shareIntent =
        ShareCompat
            .IntentBuilder(activity)
            .setType(activity.contentResolver.getType(uri))
            .setChooserTitle(intentTitle)
            .setSubject(intentSubject)
            .setStream(uri)
            .intent
    shareIntent.apply {
        clipData = ClipData.newRawUri(clipDataLabel, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(shareIntent, intentTitle)
}
