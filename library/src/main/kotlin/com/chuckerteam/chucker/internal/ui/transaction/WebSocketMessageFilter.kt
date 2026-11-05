package com.chuckerteam.chucker.internal.ui.transaction

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.websocket.SocketIoDecoder
import com.chuckerteam.chucker.internal.websocket.SocketIoFrame

/** A timeline row: the stored message plus its Socket.IO decoding, when the connection is Engine.IO. */
internal data class WebSocketMessageItem(
    val message: WebSocketMessage,
    val frame: SocketIoFrame?,
) {
    /** What the row shows: the decoded data for Socket.IO frames, the raw payload otherwise. */
    val displayPayload: String
        get() {
            val payload = message.payload.orEmpty()
            return frame?.data(payload) ?: payload
        }
}

internal data class WebSocketMessageFilter(
    val showSent: Boolean = true,
    val showReceived: Boolean = true,
    /** Engine.IO ping, pong and noop frames. Hidden by default: they're mostly noise. */
    val showPingPong: Boolean = false,
)

internal fun List<WebSocketMessage>.toMessageItems(isEngineIo: Boolean): List<WebSocketMessageItem> =
    map { message ->
        val frame =
            if (isEngineIo && message.type == Type.TEXT) {
                message.payload?.let(SocketIoDecoder::decode)
            } else {
                null
            }
        WebSocketMessageItem(message, frame)
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
