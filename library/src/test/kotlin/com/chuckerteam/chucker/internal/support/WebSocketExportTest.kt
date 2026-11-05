package com.chuckerteam.chucker.internal.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.data.har.log.Creator
import com.chuckerteam.chucker.util.TestTransactionFactory
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class WebSocketExportTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun connection(url: String) =
        HttpTransaction().apply {
            id = 7
            isWebSocket = true
            requestDate = OPENED_AT
            method = "GET"
            responseCode = 101
            populateUrl(url.toHttpUrl())
        }

    private fun message(
        seq: Long,
        direction: Direction,
        payload: String?,
        type: Type = Type.TEXT,
        closeCode: Int? = null,
    ) = WebSocketMessage(
        id = seq,
        transactionId = 7,
        seq = seq,
        timestamp = OPENED_AT + seq * 100,
        direction = direction,
        type = type,
        payload = payload,
        payloadSize = payload?.length?.toLong() ?: 0,
        closeCode = closeCode,
    )

    @Test
    fun `formats a Socket IO timeline with decoded packets and attachments`() {
        val messages =
            listOf(
                message(1, Direction.SENT, """42["chat",{"text":"hi"}]"""),
                message(2, Direction.SENT, """451-["upload",{"_placeholder":true,"num":0}]"""),
                message(3, Direction.SENT, "0102", Type.BINARY),
                message(4, Direction.RECEIVED, "bye", Type.CLOSE, closeCode = 1000),
            )

        val text =
            WebSocketMessagesFormatter.format(
                context = context,
                messages = messages,
                openedAt = OPENED_AT,
                connectionUrl = "https://example.com/socket.io/?EIO=4&transport=websocket",
            )

        assertThat(text.lines())
            .containsExactly(
                """+0.100s ↑ EVENT chat [{"text":"hi"}]""",
                """+0.200s ↑ BINARY_EVENT upload [{"_placeholder":true,"num":0}]""",
                "+0.300s ↑ Attachment 1/1 of BINARY_EVENT upload · Binary, 4 B 0102",
                "+0.400s ↓ Close 1000 bye",
            ).inOrder()
    }

    @Test
    fun `text export appends the messages of a WebSocket connection`() {
        val transaction = connection("https://example.com/chat")
        val messages = listOf(message(1, Direction.SENT, "hello"))

        val content = TransactionDetailsSharable(transaction, encodeUrls = false, messages).toSharableUtf8Content(context)

        assertThat(content).endsWith("---------- Messages ----------\n\n+0.100s ↑ hello")
    }

    @Test
    fun `text export of a connection without messages says so`() {
        val content =
            TransactionDetailsSharable(connection("https://example.com/chat"), encodeUrls = false)
                .toSharableUtf8Content(context)

        assertThat(content).endsWith("---------- Messages ----------\n\nNo messages yet")
    }

    @Test
    fun `text export of an HTTP transaction has no messages section`() {
        val transaction = TestTransactionFactory.createTransaction("GET")

        val content = TransactionDetailsSharable(transaction, encodeUrls = false).toSharableUtf8Content(context)

        assertThat(content).isEqualTo(TestTransactionFactory.expectedGetHttpTransaction)
    }

    @Test
    fun `HAR export adds Chrome's WebSocket fields to WebSocket entries only`() {
        val webSocket = connection("https://example.com/chat")
        val http = TestTransactionFactory.createTransaction("GET").apply { id = 8 }
        val messages =
            listOf(
                message(1, Direction.SENT, "hello"),
                message(2, Direction.RECEIVED, "0102", Type.BINARY),
                message(3, Direction.RECEIVED, "bye", Type.CLOSE, closeCode = 1000),
            )

        val har = HarUtils.fromHttpTransactions(listOf(webSocket, http), Creator("Chucker", "1"), mapOf(7L to messages))
        val entries =
            JsonParser
                .parseString(JsonConverter.nonNullSerializerInstance.toJson(har))
                .asJsonObject
                .getAsJsonObject("log")
                .getAsJsonArray("entries")

        val webSocketEntry = entries[0].asJsonObject
        assertThat(webSocketEntry.get("_resourceType").asString).isEqualTo("websocket")
        val frames = webSocketEntry.getAsJsonArray("_webSocketMessages").map { it.asJsonObject }
        // Close frames have no HAR form, so only the two data messages remain.
        assertThat(frames.map { it.get("type").asString }).containsExactly("send", "receive").inOrder()
        assertThat(frames.map { it.get("opcode").asInt }).containsExactly(1, 2).inOrder()
        assertThat(frames[0].get("data").asString).isEqualTo("hello")
        assertThat(frames[1].get("data").asString).isEqualTo("AQI=")
        assertThat(frames[0].get("time").asDouble).isEqualTo((OPENED_AT + 100) / 1000.0)

        val httpEntry = entries[1].asJsonObject
        assertThat(httpEntry.has("_resourceType")).isFalse()
        assertThat(httpEntry.has("_webSocketMessages")).isFalse()
    }

    @Test
    fun `formats Engine IO polling bodies as decoded packets`() {
        val transaction =
            HttpTransaction().apply {
                populateUrl("https://example.com/socket.io/?EIO=4&transport=polling&sid=abc".toHttpUrl())
                responseContentType = "text/plain; charset=UTF-8"
                responseBody = "40{\"sid\":\"x\"}\u001e42[\"chat\",{\"text\":\"hi\"}]\u001e2"
            }

        assertThat(transaction.getFormattedResponseBody())
            .isEqualTo("CONNECT {\"sid\":\"x\"}\nEVENT chat [{\"text\":\"hi\"}]\nPING")
    }

    private companion object {
        const val OPENED_AT = 1_700_000_000_000L
    }
}
