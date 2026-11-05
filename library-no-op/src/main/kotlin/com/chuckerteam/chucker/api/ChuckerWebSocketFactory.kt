package com.chuckerteam.chucker.api

import android.content.Context
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * No-op implementation. Opens sockets with the delegate without recording anything.
 */
public class ChuckerWebSocketFactory private constructor(
    private val delegate: WebSocket.Factory,
) : WebSocket.Factory {
    override fun newWebSocket(
        request: Request,
        listener: WebSocketListener,
    ): WebSocket = delegate.newWebSocket(request, listener)

    @Suppress("UnusedPrivateMember", "UNUSED_PARAMETER")
    public class Builder(
        context: Context,
        private val delegate: WebSocket.Factory,
    ) {
        public fun collector(collector: ChuckerCollector): Builder = this

        public fun maxContentLength(length: Long): Builder = this

        public fun maxMessagesPerConnection(count: Int): Builder = this

        public fun redactHeaders(headerNames: Iterable<String>): Builder = this

        public fun redactHeaders(vararg headerNames: String): Builder = this

        public fun build(): ChuckerWebSocketFactory = ChuckerWebSocketFactory(delegate)
    }
}
