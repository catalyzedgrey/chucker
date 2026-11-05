package com.chuckerteam.chucker.internal.ui.transaction

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.websocket.SocketIoDecoder
import com.chuckerteam.chucker.internal.websocket.SocketIoFrame

/**
 * A timeline row: the stored message plus its Socket.IO decoding, when the connection is Engine.IO.
 *
 * @property attachment Set on binary frames that carry an attachment of a Socket.IO binary event or ack.
 */
internal data class WebSocketMessageItem(
    val message: WebSocketMessage,
    val frame: SocketIoFrame?,
    val attachment: Attachment? = null,
) {
    /** Attachment [index] (1-based) of [count] for the packet labelled [parentLabel]. */
    data class Attachment(
        val index: Int,
        val count: Int,
        val parentLabel: String,
    )

    /** Whether [displayPayload] is JSON that "Format JSON" can pretty-print. */
    val isJson: Boolean
        get() = message.type == Type.TEXT && looksLikeJson(displayPayload)

    /** What the row shows: the decoded data for Socket.IO frames, the raw payload otherwise. */
    val displayPayload: String
        get() {
            val payload = message.payload.orEmpty()
            return frame?.data(payload) ?: payload
        }
}

/** Cheap check before pretty-printing: an object or array, ignoring leading whitespace. */
internal fun looksLikeJson(text: String): Boolean {
    val trimmed = text.trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[")
}

internal data class WebSocketMessageFilter(
    val showSent: Boolean = true,
    val showReceived: Boolean = true,
    /** Engine.IO ping, pong and noop frames. Hidden by default: they're mostly noise. */
    val showPingPong: Boolean = false,
)

/**
 * Decodes Socket.IO frames and links binary attachments to their packet. A Socket.IO binary event
 * (`451-["upload",{"_placeholder":true,"num":0}]`) is followed, in the same direction, by as many
 * binary frames as it announces; those frames become its attachments.
 */
internal fun List<WebSocketMessage>.toMessageItems(isEngineIo: Boolean): List<WebSocketMessageItem> {
    // Attachments still expected, per direction: the parent's label, how many, and how many seen.
    val pending = mutableMapOf<Direction, Triple<String, Int, Int>>()
    return map { message ->
        val frame =
            if (isEngineIo && message.type == Type.TEXT) {
                message.payload?.let(SocketIoDecoder::decode)
            } else {
                null
            }
        var attachment: WebSocketMessageItem.Attachment? = null
        when {
            frame?.attachments != null && frame.attachments > 0 ->
                pending[message.direction] = Triple(frame.label, frame.attachments, 0)
            message.type == Type.BINARY -> {
                pending[message.direction]?.let { (label, count, seen) ->
                    attachment = WebSocketMessageItem.Attachment(seen + 1, count, label)
                    if (seen + 1 == count) {
                        pending.remove(message.direction)
                    } else {
                        pending[message.direction] = Triple(label, count, seen + 1)
                    }
                }
            }
            // Any other data message means the attachments are not coming.
            message.isData -> pending.remove(message.direction)
        }
        WebSocketMessageItem(message, frame, attachment)
    }
}

/**
 * Applies [filter] and a case-insensitive [query] over the raw payload and the event name.
 * Close, failure and dropped markers are always kept, except when a query is set.
 */
internal fun List<WebSocketMessageItem>.filterMessages(
    filter: WebSocketMessageFilter,
    query: String,
): List<WebSocketMessageItem> =
    filter { item ->
        val message = item.message
        val passesDirection =
            !message.isData ||
                when (message.direction) {
                    Direction.SENT -> filter.showSent
                    Direction.RECEIVED -> filter.showReceived
                }
        val passesPingPong = filter.showPingPong || item.frame?.packet?.isEngineIoControl != true
        val passesQuery =
            query.isBlank() ||
                message.payload?.contains(query, ignoreCase = true) == true ||
                item.frame?.event?.contains(query, ignoreCase = true) == true
        passesDirection && passesPingPong && passesQuery
    }
