package com.chuckerteam.chucker.internal.support

import android.content.Context
import com.chuckerteam.chucker.R.string
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import okio.Buffer
import okio.Source

/** @param webSocketMessages Messages of the WebSocket connections among [transactions], by transaction id. */
internal class TransactionListDetailsSharable(
    transactions: List<HttpTransaction>,
    encodeUrls: Boolean,
    webSocketMessages: Map<Long, List<WebSocketMessage>> = emptyMap(),
) : Sharable {
    private val transactions =
        transactions.map { TransactionDetailsSharable(it, encodeUrls, webSocketMessages[it.id].orEmpty()) }

    override fun toSharableContent(context: Context): Source =
        Buffer().writeUtf8(
            transactions.joinToString(
                separator = "\n${context.getString(string.chucker_export_separator)}\n",
                prefix = "${context.getString(string.chucker_export_prefix)}\n",
                postfix = "\n${context.getString(string.chucker_export_postfix)}\n",
            ) { it.toSharableUtf8Content(context) },
        )
}
