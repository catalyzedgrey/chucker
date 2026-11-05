package com.chuckerteam.chucker.internal.ui.transaction

import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class WebSocketMessageFilterTest {
    private fun message(
        seq: Long,
        direction: Direction,
        payload: String,
        type: Type = Type.TEXT,
    ) = WebSocketMessage(
        id = seq,
        transactionId = 1,
        seq = seq,
        timestamp = seq,
        direction = direction,
        type = type,
        payload = payload,
        payloadSize = payload.length.toLong(),
    )

    private val socketIo =
        listOf(
            message(1, Direction.SENT, """42["chat",{"text":"hi"}]"""),
            message(2, Direction.RECEIVED, "2"),
            message(3, Direction.SENT, "3"),
            message(4, Direction.RECEIVED, """42["typing"]"""),
            message(5, Direction.RECEIVED, "bye", Type.CLOSE),
        ).toMessageItems(isEngineIo = true)

    private fun List<WebSocketMessageItem>.seqs() = map { it.message.seq }

    @Test
    fun `hides ping and pong by default`() {
        assertThat(socketIo.filterMessages(WebSocketMessageFilter(), "").seqs()).containsExactly(1L, 4L, 5L).inOrder()
    }

    @Test
    fun `shows ping and pong when enabled`() {
        val filter = WebSocketMessageFilter(showPingPong = true)

        assertThat(socketIo.filterMessages(filter, "").seqs()).containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
    }

    @Test
    fun `filters by direction but keeps connection events`() {
        val filter = WebSocketMessageFilter(showSent = false, showPingPong = true)

        assertThat(socketIo.filterMessages(filter, "").seqs()).containsExactly(2L, 4L, 5L).inOrder()
    }

    @Test
    fun `searches event names and payloads case-insensitively`() {
        assertThat(socketIo.filterMessages(WebSocketMessageFilter(), "TYPING").seqs()).containsExactly(4L)
        assertThat(socketIo.filterMessages(WebSocketMessageFilter(), "hi").seqs()).containsExactly(1L)
    }

    @Test
    fun `plain WebSocket messages are not decoded`() {
        val items = listOf(message(1, Direction.SENT, "2")).toMessageItems(isEngineIo = false)

        assertThat(items.single().frame).isNull()
        assertThat(items.filterMessages(WebSocketMessageFilter(), "").seqs()).containsExactly(1L)
    }

    @Test
    fun `links binary frames to the binary event that announced them`() {
        val items =
            listOf(
                message(1, Direction.SENT, """452-["upload",{"_placeholder":true,"num":0},{"_placeholder":true,"num":1}]"""),
                message(2, Direction.RECEIVED, "01", Type.BINARY),
                message(3, Direction.SENT, "02", Type.BINARY),
                message(4, Direction.SENT, "03", Type.BINARY),
                message(5, Direction.SENT, "04", Type.BINARY),
            ).toMessageItems(isEngineIo = true)

        assertThat(items.map { it.attachment?.let { a -> "${a.index}/${a.count} ${a.parentLabel}" } })
            .containsExactly(
                null,
                // Attachments follow in the sender's direction only.
                null,
                "1/2 BINARY_EVENT upload",
                "2/2 BINARY_EVENT upload",
                // All announced attachments arrived; later binary frames stand alone.
                null,
            ).inOrder()
    }

    @Test
    fun `a text message cancels attachments that never arrived`() {
        val items =
            listOf(
                message(1, Direction.SENT, """451-["upload",{"_placeholder":true,"num":0}]"""),
                message(2, Direction.SENT, """42["chat"]"""),
                message(3, Direction.SENT, "01", Type.BINARY),
            ).toMessageItems(isEngineIo = true)

        assertThat(items.last().attachment).isNull()
    }

    @Test
    fun `detects JSON in decoded Socket IO data and raw payloads`() {
        val items =
            listOf(
                message(1, Direction.SENT, """42["chat",{"text":"hi"}]"""),
                message(2, Direction.SENT, """42["typing"]"""),
                message(3, Direction.SENT, "  {\"plain\":true}"),
                message(4, Direction.SENT, "hello"),
                message(5, Direction.SENT, "7b7d", Type.BINARY),
            ).toMessageItems(isEngineIo = true)

        // The event without arguments has no data left to format; binary is never JSON.
        assertThat(items.map { it.isJson }).containsExactly(true, false, true, false, false).inOrder()
    }

    @Test
    fun `shows event arguments without the event name`() {
        assertThat(socketIo.first().displayPayload).isEqualTo("""[{"text":"hi"}]""")
    }
}
