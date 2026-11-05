package com.chuckerteam.chucker.internal.data.har.log.entry

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.google.gson.annotations.SerializedName
import okio.ByteString.Companion.decodeHex

/**
 * A WebSocket message in Chrome DevTools' non-standard `_webSocketMessages` HAR extension, so
 * exported files open with their messages in DevTools and other HAR viewers that follow Chrome.
 */
internal data class WebSocketFrame(
    /** `send` or `receive`. */
    @SerializedName("type") val type: String,
    /** Seconds since the epoch. */
    @SerializedName("time") val time: Double,
    /** 1 for text, 2 for binary. */
    @SerializedName("opcode") val opcode: Int,
    /** Text as is; binary as base64. */
    @SerializedName("data") val data: String,
) {
    companion object {
        private const val OPCODE_TEXT = 1
        private const val OPCODE_BINARY = 2
        private const val MILLIS_PER_SECOND = 1000.0

        private fun hexToBase64(hex: String): String = runCatching { hex.decodeHex().base64() }.getOrDefault("")

        /** Only text and binary messages have a HAR form; close and failure events are dropped. */
        fun from(message: WebSocketMessage): WebSocketFrame? {
            val (opcode, data) =
                when (message.type) {
                    WebSocketMessage.Type.TEXT -> OPCODE_TEXT to message.payload.orEmpty()
                    // Binary payloads are stored as hex.
                    WebSocketMessage.Type.BINARY -> OPCODE_BINARY to hexToBase64(message.payload.orEmpty())
                    else -> return null
                }
            return WebSocketFrame(
                type = if (message.direction == WebSocketMessage.Direction.SENT) "send" else "receive",
                time = message.timestamp / MILLIS_PER_SECOND,
                opcode = opcode,
                data = data,
            )
        }
    }
}
