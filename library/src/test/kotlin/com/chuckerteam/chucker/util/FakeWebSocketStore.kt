package com.chuckerteam.chucker.util

import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.websocket.WebSocketStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** In-memory [WebSocketStore] that also logs the order of operations. */
internal class FakeWebSocketStore : WebSocketStore {
    private val nextId = AtomicLong(1)
    val connections = CopyOnWriteArrayList<HttpTransaction>()
    val messages = CopyOnWriteArrayList<WebSocketMessage>()
    val operations = CopyOnWriteArrayList<String>()

    override suspend fun insertConnection(transaction: HttpTransaction) {
        transaction.id = nextId.getAndIncrement()
        connections += transaction
        operations += "insert:${transaction.id}"
    }

    override suspend fun updateConnection(transaction: HttpTransaction) {
        operations += "update:${transaction.id}"
    }

    override suspend fun insertMessages(messages: List<WebSocketMessage>) {
        this.messages += messages
        operations += "messages:${messages.size}"
    }

    override suspend fun trimMessages(
        transactionId: Long,
        keep: Int,
    ) {
        val toDelete =
            messages
                .filter { it.transactionId == transactionId }
                .sortedBy { it.seq }
                .dropLast(keep)
        messages.removeAll(toDelete.toSet())
        operations += "trim:$transactionId"
    }

    fun messagesInOrder(): List<WebSocketMessage> = messages.sortedBy { it.seq }

    /** Polls until [condition] holds, since the writer runs on a background thread. */
    fun awaitUntil(
        timeoutMs: Long = 5_000,
        condition: FakeWebSocketStore.() -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out. Connections: $connections, messages: ${messagesInOrder()}")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10L
    }
}
