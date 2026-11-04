package com.chuckerteam.chucker.internal.ui.transaction

import android.content.Context
import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.support.calculateLuminance
import com.chuckerteam.chucker.internal.support.spannableChunked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a [HttpTransaction] into the list of rows shown in the request/response payload tab.
 * Moved verbatim from the former `TransactionPayloadFragment.processPayload`.
 */
internal object PayloadItemsBuilder {
    private const val LINE_LENGTH_THRESHOLD = 500

    @Suppress("LongMethod")
    suspend fun build(
        context: Context,
        type: PayloadType,
        transaction: HttpTransaction,
        formatRequestBody: Boolean,
    ): MutableList<TransactionPayloadItem> {
        return withContext(Dispatchers.Default) {
            val result = mutableListOf<TransactionPayloadItem>()

            val headersString: String
            val isBodyEncoded: Boolean
            val bodyString: CharSequence

            if (type == PayloadType.REQUEST) {
                headersString = transaction.getRequestHeadersString(true)
                isBodyEncoded = transaction.isRequestBodyEncoded
                bodyString =
                    if (formatRequestBody) {
                        transaction.getSpannedRequestBody(context)
                    } else {
                        transaction.requestBody ?: ""
                    }
            } else {
                headersString = transaction.getResponseHeadersString(true)
                isBodyEncoded = transaction.isResponseBodyEncoded
                bodyString = transaction.getSpannedResponseBody(context)
            }
            if (headersString.isNotBlank()) {
                result.add(
                    TransactionPayloadItem.HeaderItem(
                        HtmlCompat.fromHtml(
                            headersString,
                            HtmlCompat.FROM_HTML_MODE_LEGACY,
                        ),
                    ),
                )
            }

            // The body could either be an image, plain text, decoded binary or not decoded binary.
            val responseBitmap = transaction.responseImageBitmap

            if (type == PayloadType.RESPONSE && responseBitmap != null) {
                val bitmapLuminance = responseBitmap.calculateLuminance()
                result.add(TransactionPayloadItem.ImageItem(responseBitmap, bitmapLuminance))
                return@withContext result
            }

            when {
                isBodyEncoded -> {
                    val text = context.getString(R.string.chucker_body_omitted)
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(text)))
                }

                bodyString.isBlank() -> {
                    val text = context.getString(R.string.chucker_body_empty)
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(text)))
                }

                else -> {
                    // adding copy item
                    result.add(TransactionPayloadItem.CopyItem(context.getString(R.string.chucker_copy_response)))

                    bodyString.lines().forEach {
                        result.add(
                            TransactionPayloadItem.BodyLineItem(
                                if (it is SpannableStringBuilder) {
                                    it
                                } else {
                                    SpannableStringBuilder.valueOf(it)
                                },
                            ),
                        )
                    }
                }
            }
            return@withContext result
        }
    }

    private fun CharSequence.lines(): List<CharSequence> {
        val linesList = this.lineSequence().toList()
        val result = mutableListOf<CharSequence>()
        var lineIndex = 0
        for (index in linesList.indices) {
            val line = subSequence(lineIndex, lineIndex + linesList[index].length)
            if (line.length > LINE_LENGTH_THRESHOLD) {
                if (line is SpannableStringBuilder) {
                    result.addAll(line.spannableChunked(LINE_LENGTH_THRESHOLD))
                } else {
                    result.addAll(line.chunked(LINE_LENGTH_THRESHOLD))
                }
            } else {
                result.add(line)
            }

            lineIndex += linesList[index].length + 1
        }
        if (result.isEmpty()) {
            result.add(subSequence(0, length))
        }
        return result
    }
}
