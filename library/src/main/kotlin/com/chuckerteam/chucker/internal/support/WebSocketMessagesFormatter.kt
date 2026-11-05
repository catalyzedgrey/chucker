package com.chuckerteam.chucker.internal.support

import android.content.Context
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.ui.transaction.WebSocketMessageItem
import com.chuckerteam.chucker.internal.ui.transaction.formatOffset
import com.chuckerteam.chucker.internal.ui.transaction.toMessageItems
import com.chuckerteam.chucker.internal.websocket.SocketIoDecoder

/**
 * Plain-text timeline of a WebSocket connection for text exports, one message per line:
 *
 * ```
 * +0.937s ↑ EVENT chat [{"user":"chucker"}]
 * +5.002s ↑ Close 1000 Sample finished
 * ```
 *
 * Socket.IO frames are decoded the same way as in the Messages tab.
 */
internal object WebSocketMessagesFormatter {
    fun format(
        context: Context,
        messages: List<WebSocketMessage>,
        openedAt: Long?,
        connectionUrl: String?,
    ): String {
        val start = openedAt ?: messages.firstOrNull()?.timestamp ?: 0L
        return messages
            .toMessageItems(SocketIoDecoder.isEngineIoUrl(connectionUrl))
            .joinToString(separator = "\n") { item -> formatLine(context, item, start) }
    }

    private fun formatLine(
        context: Context,
        item: WebSocketMessageItem,
        openedAt: Long,
    ): String {
        val message = item.message
        val arrow = if (message.direction == Direction.SENT) "↑" else "↓"
        val label =
            item.attachment?.let {
                context.getString(R.string.chucker_websocket_attachment, it.index, it.count, it.parentLabel) +
                    " · " + typeLabel(context, message)
            } ?: item.frame?.label ?: typeLabel(context, message)
        val data = item.displayPayload
        return buildString {
            append(formatOffset(message.timestamp - openedAt)).append(' ').append(arrow)
            if (label.isNotEmpty()) append(' ').append(label)
            if (data.isNotEmpty()) append(' ').append(data)
            if (message.isTruncated) append(' ').append(context.getString(R.string.chucker_body_content_truncated).trim())
            if (message.isDropped) append(" (").append(context.getString(R.string.chucker_websocket_not_sent)).append(')')
        }
    }

    private fun typeLabel(
        context: Context,
        message: WebSocketMessage,
    ): String =
        when (message.type) {
            Type.TEXT -> ""
            Type.BINARY ->
                context.getString(
                    R.string.chucker_websocket_binary,
                    FormatUtils.formatByteCount(message.payloadSize, true),
                )
            Type.CLOSE -> context.getString(R.string.chucker_websocket_close, message.closeCode ?: 0)
            Type.FAILURE -> context.getString(R.string.chucker_websocket_failure)
            Type.DROPPED -> context.getString(R.string.chucker_websocket_messages_dropped, message.payloadSize)
        }
}
