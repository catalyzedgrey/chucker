package com.chuckerteam.chucker.api

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.chuckerteam.chucker.internal.websocket.ChuckerWebSocketTag
import com.chuckerteam.chucker.internal.websocket.RecordingWebSocket
import com.chuckerteam.chucker.internal.websocket.RecordingWebSocketListener
import com.chuckerteam.chucker.internal.websocket.WebSocketConnection
import com.chuckerteam.chucker.internal.websocket.WebSocketEventWriter
import com.chuckerteam.chucker.internal.websocket.WebSocketStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * A [WebSocket.Factory] that records WebSocket connections and their messages so they can be
 * inspected in Chucker. Wrap the factory you already use (usually your `OkHttpClient`) and open
 * sockets through this one instead:
 *
 * ```
 * val webSocketFactory = ChuckerWebSocketFactory.Builder(context, okHttpClient).build()
 * webSocketFactory.newWebSocket(request, listener)
 * ```
 *
 * For Socket.IO, pass it as `IO.Options.webSocketFactory`.
 *
 * Interceptors can't see WebSocket messages, which is why this is separate from
 * [ChuckerInterceptor]. Handshakes opened through this factory are skipped by
 * [ChuckerInterceptor], so they aren't recorded twice.
 */
public class ChuckerWebSocketFactory private constructor(
    builder: Builder,
) : WebSocket.Factory {
    private val delegate = builder.delegate
    private val maxContentLength = builder.maxContentLength
    private val headersToRedact = builder.headersToRedact.toSet()

    private val writer =
        WebSocketEventWriter(
            store = builder.store ?: (builder.collector ?: ChuckerCollector(builder.context)).webSocketStore,
            scope = builder.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO),
            maxMessagesPerConnection = builder.maxMessagesPerConnection,
            batchDelayMs = builder.batchDelayMs,
        )

    override fun newWebSocket(
        request: Request,
        listener: WebSocketListener,
    ): WebSocket {
        val connection = WebSocketConnection(writer, maxContentLength, headersToRedact)
        val taggedRequest =
            request
                .newBuilder()
                .tag(ChuckerWebSocketTag::class.java, ChuckerWebSocketTag)
                .build()
        connection.onStart(taggedRequest)

        val socket = RecordingWebSocket(connection)
        socket.bind(delegate.newWebSocket(taggedRequest, RecordingWebSocketListener(listener, socket, connection)))
        return socket
    }

    /**
     * Assembles a new [ChuckerWebSocketFactory].
     *
     * @param context An Android [Context].
     * @param delegate The factory that actually opens sockets, usually your `OkHttpClient`.
     */
    public class Builder(
        internal val context: Context,
        internal val delegate: WebSocket.Factory,
    ) {
        internal var collector: ChuckerCollector? = null
        internal var maxContentLength = MAX_CONTENT_LENGTH
        internal var maxMessagesPerConnection = MAX_MESSAGES_PER_CONNECTION
        internal var headersToRedact = emptySet<String>()
        internal var store: WebSocketStore? = null
        internal var scope: CoroutineScope? = null
        internal var batchDelayMs = WebSocketEventWriter.DEFAULT_BATCH_DELAY_MS

        /** Sets the [ChuckerCollector] to customize data retention and notifications. */
        public fun collector(collector: ChuckerCollector): Builder =
            apply {
                this.collector = collector
            }

        /**
         * Sets the maximum length of a message payload. Longer payloads are truncated.
         * Binary payloads are stored as hex, so they keep half as many bytes.
         * Default is 250kb.
         */
        public fun maxContentLength(length: Long): Builder =
            apply {
                require(length > 0) { "maxContentLength must be positive" }
                this.maxContentLength = length
            }

        /**
         * Sets how many messages are kept per connection. Once exceeded, the oldest ones are
         * deleted. Default is 1000.
         */
        public fun maxMessagesPerConnection(count: Int): Builder =
            apply {
                require(count > 0) { "maxMessagesPerConnection must be positive" }
                this.maxMessagesPerConnection = count
            }

        /** Sets handshake headers that will be redacted if their names match. */
        public fun redactHeaders(headerNames: Iterable<String>): Builder =
            apply {
                this.headersToRedact = headerNames.toSet()
            }

        /** Sets handshake headers that will be redacted if their names match. */
        public fun redactHeaders(vararg headerNames: String): Builder =
            apply {
                this.headersToRedact = headerNames.toSet()
            }

        @VisibleForTesting
        internal fun store(
            store: WebSocketStore,
            scope: CoroutineScope,
            batchDelayMs: Long,
        ): Builder =
            apply {
                this.store = store
                this.scope = scope
                this.batchDelayMs = batchDelayMs
            }

        /** Creates a new [ChuckerWebSocketFactory] with the values defined in this builder. */
        public fun build(): ChuckerWebSocketFactory = ChuckerWebSocketFactory(this)
    }

    private companion object {
        private const val MAX_CONTENT_LENGTH = 250_000L
        private const val MAX_MESSAGES_PER_CONNECTION = 1_000
    }
}
