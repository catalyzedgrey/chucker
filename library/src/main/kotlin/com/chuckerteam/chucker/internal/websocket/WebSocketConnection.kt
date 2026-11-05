package com.chuckerteam.chucker.internal.websocket

import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.support.redact
import okhttp3.Request
import okhttp3.Response
import okio.ByteString
import okio.utf8Size
import java.util.concurrent.atomic.AtomicLong

/**
 * Records one WebSocket connection. Called from the app's threads and OkHttp's reader thread;
 * it only builds events and hands them to the [writer].
 */
internal class WebSocketConnection(
    private val writer: WebSocketEventWriter,
    private val maxContentLength: Long,
    private val headersToRedact: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seq = AtomicLong()
    private val dropped = AtomicLong()

    /** Owned by the writer: only read or modified on the writer's coroutine. */
    var transaction: HttpTransaction? = null

    /** Number of sequence numbers handed out so far, i.e. an upper bound on stored messages. */
    val messageCount: Long get() = seq.get()

    fun nextSeq(): Long = seq.incrementAndGet()

    fun onStart(request: Request) {
        val transaction =
            HttpTransaction().apply {
                isWebSocket = true
                requestDate = clock()
                method = request.method
                populateUrl(request.url)
                requestHeadersSize = request.headers.byteCount()
                setRequestHeaders(request.headers.redact(headersToRedact))
                requestPayloadSize = 0
            }
        writer.offerControl(WebSocketEvent.Started(this, transaction))
    }

    fun onOpen(response: Response) {
        val now = clock()
        writer.offerControl(WebSocketEvent.Updated(this) { applyResponse(response, now) })
    }

    fun onText(
        direction: Direction,
        text: String,
        seq: Long = nextSeq(),
        isDropped: Boolean = false,
    ) {
        val isTruncated = text.length > maxContentLength
        val payload = if (isTruncated) text.take(maxContentLength.toInt()) else text
        message(seq, direction, Type.TEXT, payload, text.utf8Size(), isTruncated, isDropped = isDropped)
    }

    fun onBinary(
        direction: Direction,
        bytes: ByteString,
        seq: Long = nextSeq(),
        isDropped: Boolean = false,
    ) {
        // Each byte becomes two hex characters.
        val maxBytes = (maxContentLength / 2).toInt()
        val isTruncated = bytes.size > maxBytes
        val payload = (if (isTruncated) bytes.substring(0, maxBytes) else bytes).hex()
        message(seq, direction, Type.BINARY, payload, bytes.size.toLong(), isTruncated, isDropped = isDropped)
    }

    fun onClose(
        direction: Direction,
        code: Int,
        reason: String,
        seq: Long = nextSeq(),
    ) {
        message(seq, direction, Type.CLOSE, reason, reason.utf8Size(), closeCode = code)
    }

    fun onClosed() {
        val now = clock()
        writer.offerControl(WebSocketEvent.Updated(this) { tookMs = now - (requestDate ?: now) })
    }

    fun onFailure(
        throwable: Throwable,
        response: Response?,
    ) {
        val now = clock()
        val error = throwable.toString()
        message(nextSeq(), Direction.RECEIVED, Type.FAILURE, error, 0)
        writer.offerControl(
            WebSocketEvent.Updated(this) {
                if (response != null && responseCode == null) applyResponse(response, now)
                this.error = error
                tookMs = now - (requestDate ?: now)
            },
        )
    }

    private fun HttpTransaction.applyResponse(
        response: Response,
        receivedAt: Long,
    ) {
        responseDate = receivedAt
        protocol = response.protocol.toString()
        responseCode = response.code
        responseMessage = response.message
        // The upgrade request carries headers OkHttp added (Upgrade, Sec-WebSocket-Key, ...).
        requestHeadersSize = response.request.headers.byteCount()
        setRequestHeaders(response.request.headers.redact(headersToRedact))
        responseHeadersSize = response.headers.byteCount()
        setResponseHeaders(response.headers.redact(headersToRedact))
        responsePayloadSize = 0
        response.handshake?.let {
            responseTlsVersion = it.tlsVersion.javaName
            responseCipherSuite = it.cipherSuite.javaName
        }
    }

    @Suppress("LongParameterList")
    private fun message(
        seq: Long,
        direction: Direction,
        type: Type,
        payload: String?,
        payloadSize: Long,
        isTruncated: Boolean = false,
        closeCode: Int? = null,
        isDropped: Boolean = false,
    ) {
        var messageSeq = seq
        val droppedSoFar = dropped.get()
        if (droppedSoFar > 0 && dropped.compareAndSet(droppedSoFar, 0)) {
            // The marker takes this message's place in the timeline and the message moves after it.
            val marker = build(seq, direction, Type.DROPPED, null, droppedSoFar)
            if (writer.offerMessage(WebSocketEvent.Message(this, marker))) {
                messageSeq = nextSeq()
            } else {
                dropped.addAndGet(droppedSoFar)
            }
        }
        val message =
            build(messageSeq, direction, type, payload, payloadSize, isTruncated, closeCode, isDropped)
        if (!writer.offerMessage(WebSocketEvent.Message(this, message))) dropped.incrementAndGet()
    }

    @Suppress("LongParameterList")
    private fun build(
        seq: Long,
        direction: Direction,
        type: Type,
        payload: String?,
        payloadSize: Long,
        isTruncated: Boolean = false,
        closeCode: Int? = null,
        isDropped: Boolean = false,
    ) = WebSocketMessage(
        transactionId = 0,
        seq = seq,
        timestamp = clock(),
        direction = direction,
        type = type,
        payload = payload,
        payloadSize = payloadSize,
        isTruncated = isTruncated,
        closeCode = closeCode,
        isDropped = isDropped,
    )
}
