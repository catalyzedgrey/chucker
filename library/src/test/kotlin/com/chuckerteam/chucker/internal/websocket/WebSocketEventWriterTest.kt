package com.chuckerteam.chucker.internal.websocket

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.util.FakeWebSocketStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class WebSocketEventWriterTest {
    private val store = FakeWebSocketStore()
    private val request = Request.Builder().url("https://example.com/socket").build()

    private fun TestScope.writer(
        maxMessagesPerConnection: Int = 100,
        maxPendingMessages: Int = 100,
    ) = WebSocketEventWriter(
        store = store,
        scope = backgroundScope as CoroutineScope,
        maxMessagesPerConnection = maxMessagesPerConnection,
        maxPendingMessages = maxPendingMessages,
        batchDelayMs = BATCH_DELAY_MS,
    )

    private fun connection(writer: WebSocketEventWriter) =
        WebSocketConnection(writer, maxContentLength = 1_000, headersToRedact = emptySet(), clock = { 1_000L })

    @Test
    fun `waits for the batch window, then inserts the connection before its messages`() =
        runTest {
            val connection = connection(writer())
            connection.onStart(request)
            connection.onText(Direction.SENT, "a")
            connection.onText(Direction.RECEIVED, "b")

            runCurrent()
            assertThat(store.operations).isEmpty()

            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()

            assertThat(store.operations).containsExactly("insert:1", "messages:2").inOrder()
            assertThat(store.messagesInOrder().map { it.transactionId }).containsExactly(1L, 1L)
            assertThat(store.messagesInOrder().map { it.payload }).containsExactly("a", "b").inOrder()
        }

    @Test
    fun `connection updates run in order with messages`() =
        runTest {
            val connection = connection(writer())
            connection.onStart(request)
            connection.onText(Direction.SENT, "a")
            connection.onClosed()

            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()

            assertThat(store.operations).containsExactly("insert:1", "messages:1", "update:1").inOrder()
            assertThat(store.connections.single().tookMs).isEqualTo(0L)
        }

    @Test
    fun `keeps only the newest messages per connection`() =
        runTest {
            val connection = connection(writer(maxMessagesPerConnection = 2))
            connection.onStart(request)
            repeat(5) { connection.onText(Direction.RECEIVED, "m$it") }

            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()

            assertThat(store.messagesInOrder().map { it.payload }).containsExactly("m3", "m4").inOrder()
        }

    @Test
    fun `drops messages when the queue is full and records how many`() =
        runTest {
            val connection = connection(writer(maxPendingMessages = 2))
            connection.onStart(request)
            repeat(4) { connection.onText(Direction.RECEIVED, "m$it") }
            // Connection events are never dropped, even with a full queue.
            connection.onClosed()

            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()
            connection.onText(Direction.RECEIVED, "after")
            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()

            val messages = store.messagesInOrder()
            assertThat(messages.map { it.type })
                .containsExactly(Type.TEXT, Type.TEXT, Type.DROPPED, Type.TEXT)
                .inOrder()
            assertThat(messages.map { it.payload }).containsExactly("m0", "m1", null, "after").inOrder()
            assertThat(messages[2].payloadSize).isEqualTo(2)
            assertThat(store.connections.single().tookMs).isNotNull()
        }

    @Test
    fun `truncates long text and binary payloads`() =
        runTest {
            val connection =
                WebSocketConnection(writer(), maxContentLength = 4, headersToRedact = emptySet())
            connection.onStart(request)
            connection.onText(Direction.SENT, "abcdefgh")
            connection.onBinary(Direction.RECEIVED, okio.ByteString.of(1, 2, 3, 4))

            advanceTimeBy(BATCH_DELAY_MS + 1)
            runCurrent()

            val (text, binary) = store.messagesInOrder()
            assertThat(text.payload).isEqualTo("abcd")
            assertThat(text.payloadSize).isEqualTo(8)
            assertThat(text.isTruncated).isTrue()
            assertThat(binary.payload).isEqualTo("0102")
            assertThat(binary.payloadSize).isEqualTo(4)
            assertThat(binary.isTruncated).isTrue()
        }

    private companion object {
        const val BATCH_DELAY_MS = 250L
    }
}
