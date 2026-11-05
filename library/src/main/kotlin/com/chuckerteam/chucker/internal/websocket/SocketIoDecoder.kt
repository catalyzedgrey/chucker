package com.chuckerteam.chucker.internal.websocket

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A text frame decoded as Engine.IO, and Socket.IO when it carries a Socket.IO packet.
 *
 * Frames look like `42/chat,7["message",{"text":"hi"}]`: Engine.IO type `4` (message), Socket.IO
 * type `2` (event), namespace `/chat`, ack id `7`, then the JSON data.
 *
 * @property dataStart Index in the raw payload where the JSON data starts, or the payload length
 * if there is none. Kept as an index so decoding never copies large payloads.
 */
internal data class SocketIoFrame(
    val packet: Packet,
    val namespace: String? = null,
    val ackId: Long? = null,
    val attachments: Int? = null,
    val event: String? = null,
    val dataStart: Int,
) {
    enum class Packet(
        val isEngineIoControl: Boolean = false,
    ) {
        // Engine.IO
        OPEN,
        CLOSE,
        PING(isEngineIoControl = true),
        PONG(isEngineIoControl = true),
        MESSAGE,
        UPGRADE,
        NOOP(isEngineIoControl = true),

        // Socket.IO, inside an Engine.IO MESSAGE
        CONNECT,
        DISCONNECT,
        EVENT,
        ACK,
        CONNECT_ERROR,
        BINARY_EVENT,
        BINARY_ACK,
    }

    /** Short label for the timeline, e.g. `EVENT chat`, `ACK 7` or `CONNECT /admin`. */
    val label: String
        get() =
            buildString {
                append(packet.name)
                event?.let { append(' ').append(it) }
                ackId?.let { append(" #").append(it) }
                namespace?.let { append(' ').append(it) }
            }

    /**
     * The data to show for [payload]: the event's arguments without its name for events, the raw
     * data otherwise. Arguments stay a JSON array, so `42["chat",{"a":1}]` shows `[{"a":1}]`.
     */
    fun data(payload: String): String {
        if (dataStart >= payload.length) return ""
        if (event == null) return payload.substring(dataStart)
        val argsStart = SocketIoDecoder.skipFirstArrayElement(payload, dataStart) ?: return payload.substring(dataStart)
        val args = payload.substring(argsStart).trimStart()
        return if (args.startsWith("]")) "" else "[$args"
    }
}

/**
 * Decodes Engine.IO / Socket.IO text frames (protocol versions 3 and 4 share the same text format).
 * Pure and allocation-light, so it can run for every row of the timeline.
 */
internal object SocketIoDecoder {
    private const val ENGINE_IO_QUERY_PARAMETER = "EIO"

    private val enginePackets =
        mapOf(
            '0' to SocketIoFrame.Packet.OPEN,
            '1' to SocketIoFrame.Packet.CLOSE,
            '2' to SocketIoFrame.Packet.PING,
            '3' to SocketIoFrame.Packet.PONG,
            '4' to SocketIoFrame.Packet.MESSAGE,
            '5' to SocketIoFrame.Packet.UPGRADE,
            '6' to SocketIoFrame.Packet.NOOP,
        )

    private val socketPackets =
        mapOf(
            '0' to SocketIoFrame.Packet.CONNECT,
            '1' to SocketIoFrame.Packet.DISCONNECT,
            '2' to SocketIoFrame.Packet.EVENT,
            '3' to SocketIoFrame.Packet.ACK,
            '4' to SocketIoFrame.Packet.CONNECT_ERROR,
            '5' to SocketIoFrame.Packet.BINARY_EVENT,
            '6' to SocketIoFrame.Packet.BINARY_ACK,
        )

    /**
     * Engine.IO clients always send the `EIO` query parameter, whatever path the server mounts
     * Socket.IO on, so it's a more reliable signal than the default `/socket.io/` path.
     */
    fun isEngineIoUrl(url: String?): Boolean = url?.toHttpUrlOrNull()?.queryParameter(ENGINE_IO_QUERY_PARAMETER) != null

    /** Returns null if [payload] isn't an Engine.IO frame. */
    @Suppress("ReturnCount")
    fun decode(payload: String): SocketIoFrame? {
        val enginePacket = payload.firstOrNull()?.let(enginePackets::get) ?: return null
        if (enginePacket != SocketIoFrame.Packet.MESSAGE) {
            return SocketIoFrame(packet = enginePacket, dataStart = 1)
        }
        return decodeSocketIo(payload) ?: SocketIoFrame(packet = enginePacket, dataStart = 1)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun decodeSocketIo(payload: String): SocketIoFrame? {
        var i = 1
        val packet = payload.getOrNull(i)?.let(socketPackets::get) ?: return null
        i++

        var attachments: Int? = null
        if (packet == SocketIoFrame.Packet.BINARY_EVENT || packet == SocketIoFrame.Packet.BINARY_ACK) {
            val dash = payload.indexOf('-', i)
            if (dash == -1) return null
            attachments = payload.substring(i, dash).toIntOrNull() ?: return null
            i = dash + 1
        }

        var namespace: String? = null
        if (payload.getOrNull(i) == '/') {
            val comma = payload.indexOf(',', i)
            val end = if (comma == -1) payload.length else comma
            namespace = payload.substring(i, end)
            i = if (comma == -1) end else comma + 1
        }

        val ackStart = i
        while (payload.getOrNull(i)?.isDigit() == true) i++
        val ackId = if (i > ackStart) payload.substring(ackStart, i).toLongOrNull() else null

        val isEvent = packet == SocketIoFrame.Packet.EVENT || packet == SocketIoFrame.Packet.BINARY_EVENT
        val event = if (isEvent) readFirstArrayString(payload, i) else null

        return SocketIoFrame(
            packet = packet,
            namespace = namespace,
            ackId = ackId,
            attachments = attachments,
            event = event,
            dataStart = i,
        )
    }

    /** Reads the first element of the JSON array at [start] if it's a string: the event name. */
    private fun readFirstArrayString(
        payload: String,
        start: Int,
    ): String? {
        var i = skipWhitespace(payload, start)
        if (payload.getOrNull(i) != '[') return null
        i = skipWhitespace(payload, i + 1)
        return readString(payload, i)?.first
    }

    /** Index just after the first element and its comma, for an array whose first element is a string. */
    internal fun skipFirstArrayElement(
        payload: String,
        start: Int,
    ): Int? {
        var i = skipWhitespace(payload, start)
        if (payload.getOrNull(i) != '[') return null
        i = skipWhitespace(payload, i + 1)
        val end = readString(payload, i)?.second ?: return null
        i = skipWhitespace(payload, end)
        return if (payload.getOrNull(i) == ',') i + 1 else i
    }

    /** Reads a JSON string literal at [start]; returns its value and the index after the closing quote. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun readString(
        payload: String,
        start: Int,
    ): Pair<String, Int>? {
        if (payload.getOrNull(start) != '"') return null
        val value = StringBuilder()
        var i = start + 1
        while (i < payload.length) {
            when (val c = payload[i]) {
                '"' -> return value.toString() to i + 1
                '\\' -> {
                    val escaped = payload.getOrNull(i + 1) ?: return null
                    when (escaped) {
                        'n' -> value.append('\n')
                        't' -> value.append('\t')
                        'r' -> value.append('\r')
                        'b' -> value.append('\b')
                        'f' -> value.append('\u000C')
                        'u' -> {
                            if (i + UNICODE_ESCAPE_END > payload.length) return null
                            val hex = payload.substring(i + 2, i + UNICODE_ESCAPE_END)
                            value.append(hex.toIntOrNull(HEX_RADIX)?.toChar() ?: return null)
                            i += UNICODE_ESCAPE_DIGITS
                        }
                        else -> value.append(escaped)
                    }
                    i += 2
                }
                else -> {
                    value.append(c)
                    i++
                }
            }
        }
        return null
    }

    private fun skipWhitespace(
        payload: String,
        start: Int,
    ): Int {
        var i = start
        while (payload.getOrNull(i)?.isWhitespace() == true) i++
        return i
    }

    private const val HEX_RADIX = 16
    private const val UNICODE_ESCAPE_DIGITS = 4
    private const val UNICODE_ESCAPE_END = 6
}
