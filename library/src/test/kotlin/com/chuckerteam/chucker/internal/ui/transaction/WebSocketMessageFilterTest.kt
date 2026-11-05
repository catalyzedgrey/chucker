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
    fun `shows event arguments without the event name`() {
        assertThat(socketIo.first().displayPayload).isEqualTo("""[{"text":"hi"}]""")
    }
}
