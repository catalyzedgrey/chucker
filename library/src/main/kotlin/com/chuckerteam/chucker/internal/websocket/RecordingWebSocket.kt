package com.chuckerteam.chucker.internal.websocket

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.support.Logger
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Marks requests opened through [com.chuckerteam.chucker.api.ChuckerWebSocketFactory], so that
 * [com.chuckerteam.chucker.api.ChuckerInterceptor] doesn't record the handshake a second time.
 */
internal object ChuckerWebSocketTag

/** Runs [block] and logs instead of throwing, so a Chucker bug never breaks the app's socket. */
private inline fun safely(block: () -> Unit) {
    try {
        block()
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Logger.error("Failed to record WebSocket event", e)
    }
}

/**
 * The [WebSocket] handed to the app. The same instance is returned from `newWebSocket` and
 * passed to every listener callback, so sends made from inside callbacks are recorded too.
 */
internal class RecordingWebSocket(
    private val connection: WebSocketConnection,
) : WebSocket {
    @Volatile
    private var delegate: WebSocket? = null

    /** Attaches the real socket. Called from `newWebSocket` and from the first listener callback, whichever comes first. */
    fun bind(webSocket: WebSocket) {
        if (delegate == null) delegate = webSocket
    }

    private val socket: WebSocket get() = checkNotNull(delegate) { "WebSocket not bound yet" }

    override fun request(): Request = socket.request()

    override fun queueSize(): Long = socket.queueSize()

    override fun send(text: String): Boolean {
        // Take the sequence number before sending, so a fast echo can't be ordered first.
        val seq = connection.nextSeq()
        val sent = socket.send(text)
        safely { connection.onText(Direction.SENT, text, seq, isDropped = !sent) }
        return sent
    }

    override fun send(bytes: ByteString): Boolean {
        val seq = connection.nextSeq()
        val sent = socket.send(bytes)
        safely { connection.onBinary(Direction.SENT, bytes, seq, isDropped = !sent) }
        return sent
    }

    override fun close(
        code: Int,
        reason: String?,
    ): Boolean {
        val seq = connection.nextSeq()
        val initiated = socket.close(code, reason)
        if (initiated) safely { connection.onClose(Direction.SENT, code, reason.orEmpty(), seq) }
        return initiated
    }

    override fun cancel() {
        socket.cancel()
    }
}

/** Records every callback, then forwards it to the app's listener with the [RecordingWebSocket]. */
internal class RecordingWebSocketListener(
    private val delegate: WebSocketListener,
    private val socket: RecordingWebSocket,
    private val connection: WebSocketConnection,
) : WebSocketListener() {
    override fun onOpen(
        webSocket: WebSocket,
        response: Response,
    ) {
        socket.bind(webSocket)
        safely { connection.onOpen(response) }
        delegate.onOpen(socket, response)
    }

    override fun onMessage(
        webSocket: WebSocket,
        text: String,
    ) {
        socket.bind(webSocket)
        safely { connection.onText(Direction.RECEIVED, text) }
        delegate.onMessage(socket, text)
    }

    override fun onMessage(
        webSocket: WebSocket,
        bytes: ByteString,
    ) {
        socket.bind(webSocket)
        safely { connection.onBinary(Direction.RECEIVED, bytes) }
        delegate.onMessage(socket, bytes)
    }

    override fun onClosing(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        socket.bind(webSocket)
        safely { connection.onClose(Direction.RECEIVED, code, reason) }
        delegate.onClosing(socket, code, reason)
    }

    override fun onClosed(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        socket.bind(webSocket)
        safely { connection.onClosed() }
        delegate.onClosed(socket, code, reason)
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        socket.bind(webSocket)
        safely { connection.onFailure(t, response) }
        delegate.onFailure(socket, t, response)
    }
}
