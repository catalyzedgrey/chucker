package com.chuckerteam.chucker.internal.websocket

import com.chuckerteam.chucker.internal.websocket.SocketIoFrame.Packet
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SocketIoDecoderTest {
    private companion object {
        const val POLLING_V4 = "https://example.com/socket.io/?EIO=4&transport=polling"
        const val POLLING_V3 = "https://example.com/socket.io/?EIO=3&transport=polling"
    }

    private fun decode(payload: String) = requireNotNull(SocketIoDecoder.decode(payload)) { "Not decoded: $payload" }

    @ParameterizedTest
    @CsvSource(
        "'0{\"sid\":\"abc\"}', OPEN, '{\"sid\":\"abc\"}'",
        "1, CLOSE, ''",
        "2, PING, ''",
        "3probe, PONG, probe",
        "5, UPGRADE, ''",
        "6, NOOP, ''",
    )
    fun `decodes Engine IO packets`(
        payload: String,
        packet: Packet,
        data: String,
    ) {
        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(packet)
        assertThat(frame.data(payload)).isEqualTo(data)
    }

    @Test
    fun `decodes an event, dropping its name from the data`() {
        val payload = """42["chat",{"text":"hi"},2]"""

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.EVENT)
        assertThat(frame.event).isEqualTo("chat")
        assertThat(frame.namespace).isNull()
        assertThat(frame.ackId).isNull()
        assertThat(frame.data(payload)).isEqualTo("""[{"text":"hi"},2]""")
        assertThat(frame.label).isEqualTo("EVENT chat")
    }

    @Test
    fun `decodes an event without arguments`() {
        val payload = """42["ping-me"]"""

        val frame = decode(payload)

        assertThat(frame.event).isEqualTo("ping-me")
        assertThat(frame.data(payload)).isEmpty()
    }

    @Test
    fun `decodes namespace and ack id`() {
        val payload = """42/admin,17["stats",{"users":3}]"""

        val frame = decode(payload)

        assertThat(frame.namespace).isEqualTo("/admin")
        assertThat(frame.ackId).isEqualTo(17)
        assertThat(frame.event).isEqualTo("stats")
        assertThat(frame.data(payload)).isEqualTo("""[{"users":3}]""")
        assertThat(frame.label).isEqualTo("EVENT stats #17 /admin")
    }

    @Test
    fun `decodes an ack`() {
        val payload = """4317["ok"]"""

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.ACK)
        assertThat(frame.ackId).isEqualTo(17)
        assertThat(frame.event).isNull()
        assertThat(frame.data(payload)).isEqualTo("""["ok"]""")
    }

    @Test
    fun `decodes connect to a namespace with auth data`() {
        val payload = """40/admin,{"token":"x"}"""

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.CONNECT)
        assertThat(frame.namespace).isEqualTo("/admin")
        assertThat(frame.data(payload)).isEqualTo("""{"token":"x"}""")
    }

    @Test
    fun `decodes a binary event with its attachment count`() {
        val payload = """451-["upload",{"_placeholder":true,"num":0}]"""

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.BINARY_EVENT)
        assertThat(frame.attachments).isEqualTo(1)
        assertThat(frame.event).isEqualTo("upload")
    }

    @Test
    fun `handles escapes in event names`() {
        val payload = """42["say \"hi\" é",1]"""

        assertThat(decode(payload).event).isEqualTo("say \"hi\" é")
    }

    @Test
    fun `falls back to a raw Engine IO message`() {
        val payload = "4hello"

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.MESSAGE)
        assertThat(frame.data(payload)).isEqualTo("hello")
    }

    @Test
    fun `ignores text that isn't an Engine IO frame`() {
        assertThat(SocketIoDecoder.decode("hello")).isNull()
        assertThat(SocketIoDecoder.decode("")).isNull()
    }

    @Test
    fun `tolerates malformed event data`() {
        val payload = """42["unterminated"""

        val frame = decode(payload)

        assertThat(frame.packet).isEqualTo(Packet.EVENT)
        assertThat(frame.event).isNull()
        assertThat(frame.data(payload)).isEqualTo("""["unterminated""")
    }

    @Test
    fun `formats an Engine IO 4 polling payload`() {
        val payload = "0{\"sid\":\"a\"}\u001e40\u001e42[\"chat\",1]\u001ebAQI="

        assertThat(SocketIoDecoder.formatPollingPayload(payload, POLLING_V4))
            .isEqualTo("OPEN {\"sid\":\"a\"}\nCONNECT\nEVENT chat [1]\nBINARY AQI=")
    }

    @Test
    fun `formats an Engine IO 3 length-prefixed polling payload`() {
        val payload = "2:40" + "12:42[\"chat\",1]"

        assertThat(SocketIoDecoder.formatPollingPayload(payload, POLLING_V3)).isEqualTo("CONNECT\nEVENT chat [1]")
    }

    @Test
    fun `leaves bodies that aren't polling payloads alone`() {
        assertThat(SocketIoDecoder.formatPollingPayload("ok", POLLING_V4)).isNull()
        assertThat(SocketIoDecoder.formatPollingPayload("", POLLING_V4)).isNull()
        assertThat(SocketIoDecoder.formatPollingPayload("5:40", POLLING_V3)).isNull()
    }

    @Test
    fun `detects the polling transport`() {
        assertThat(SocketIoDecoder.isEngineIoPollingUrl(POLLING_V4)).isTrue()
        assertThat(SocketIoDecoder.isEngineIoPollingUrl("https://example.com/socket.io/?EIO=4&transport=websocket")).isFalse()
        assertThat(SocketIoDecoder.isEngineIoPollingUrl("https://example.com/poll?transport=polling")).isFalse()
    }

    @Test
    fun `detects Engine IO connections from the EIO query parameter`() {
        assertThat(SocketIoDecoder.isEngineIoUrl("https://example.com/socket.io/?EIO=4&transport=websocket")).isTrue()
        assertThat(SocketIoDecoder.isEngineIoUrl("https://example.com/custom-path/?EIO=3")).isTrue()
        assertThat(SocketIoDecoder.isEngineIoUrl("https://example.com/chat")).isFalse()
        assertThat(SocketIoDecoder.isEngineIoUrl(null)).isFalse()
    }
}
