package com.chuckerteam.chucker.internal.websocket

import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.support.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Where the writer persists WebSocket data. Implemented on top of [com.chuckerteam.chucker.api.ChuckerCollector]. */
internal interface WebSocketStore {
    /** Inserts the connection and sets its [HttpTransaction.id]. */
    suspend fun insertConnection(transaction: HttpTransaction)

    suspend fun updateConnection(transaction: HttpTransaction)

    suspend fun insertMessages(messages: List<WebSocketMessage>)

    suspend fun trimMessages(
        transactionId: Long,
        keep: Int,
    )
}

internal sealed class WebSocketEvent {
    abstract val connection: WebSocketConnection

    class Started(
        override val connection: WebSocketConnection,
        val transaction: HttpTransaction,
    ) : WebSocketEvent()

    /** [update] runs on the writer, which owns the connection's [HttpTransaction]. */
    class Updated(
        override val connection: WebSocketConnection,
        val update: HttpTransaction.() -> Unit,
    ) : WebSocketEvent()

    /** [message] has no transaction id yet; the writer fills it in. */
    class Message(
        override val connection: WebSocketConnection,
        val message: WebSocketMessage,
    ) : WebSocketEvent()
}

/**
 * Persists WebSocket events off the socket's threads.
 *
 * Callers never block: [offer] only queues. A single coroutine drains the queue and writes in
 * batches, which keeps ordering (a connection is always inserted before its messages) and
 * limits database writes for chatty sockets. Messages beyond [maxPendingMessages] are dropped
 * and counted rather than slowing the app down; connection events are never dropped.
 */
internal class WebSocketEventWriter(
    private val store: WebSocketStore,
    scope: CoroutineScope,
    private val maxMessagesPerConnection: Int,
    private val maxPendingMessages: Int = DEFAULT_MAX_PENDING_MESSAGES,
    private val batchDelayMs: Long = DEFAULT_BATCH_DELAY_MS,
) {
    private val channel = Channel<WebSocketEvent>(Channel.UNLIMITED)
    private val pendingMessages = AtomicInteger()

    init {
        scope.launch { run() }
    }

    /** Queues a connection event. Never dropped. */
    fun offerControl(event: WebSocketEvent) {
        channel.trySend(event)
    }

    /** Queues a message event. Returns false if it was dropped because the queue is full. */
    fun offerMessage(event: WebSocketEvent.Message): Boolean {
        if (pendingMessages.incrementAndGet() > maxPendingMessages) {
            pendingMessages.decrementAndGet()
            return false
        }
        channel.trySend(event)
        return true
    }

    private suspend fun run() {
        val batch = ArrayList<WebSocketEvent>()
        for (first in channel) {
            batch += first
            drainInto(batch)
            if (batch.size < BATCH_SIZE) {
                delay(batchDelayMs)
                drainInto(batch)
            }
            try {
                process(batch)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Logger.error("Failed to store WebSocket events", e)
            }
            batch.clear()
        }
    }

    private fun drainInto(batch: MutableList<WebSocketEvent>) {
        while (batch.size < MAX_BATCH_SIZE) {
            batch += channel.tryReceive().getOrNull() ?: return
        }
    }

    private suspend fun process(batch: List<WebSocketEvent>) {
        val messages = ArrayList<WebSocketMessage>()
        val touched = LinkedHashSet<WebSocketConnection>()

        suspend fun flushMessages() {
            if (messages.isEmpty()) return
            store.insertMessages(messages.toList())
            messages.clear()
        }

        for (event in batch) {
            val connection = event.connection
            when (event) {
                is WebSocketEvent.Started -> {
                    flushMessages()
                    connection.transaction = event.transaction
                    store.insertConnection(event.transaction)
                }
                is WebSocketEvent.Updated -> {
                    flushMessages()
                    val transaction = connection.transaction ?: continue
                    transaction.apply(event.update)
                    store.updateConnection(transaction)
                }
                is WebSocketEvent.Message -> {
                    pendingMessages.decrementAndGet()
                    val transactionId = connection.transaction?.id ?: continue
                    messages += event.message.copy(transactionId = transactionId)
                    touched += connection
                }
            }
        }
        flushMessages()

        for (connection in touched) {
            val transactionId = connection.transaction?.id ?: continue
            if (connection.messageCount > maxMessagesPerConnection) {
                store.trimMessages(transactionId, maxMessagesPerConnection)
            }
        }
    }

    internal companion object {
        const val DEFAULT_BATCH_DELAY_MS = 250L
        const val DEFAULT_MAX_PENDING_MESSAGES = 5_000
        private const val BATCH_SIZE = 50
        private const val MAX_BATCH_SIZE = 500
    }
}
