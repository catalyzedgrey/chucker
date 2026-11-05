package com.chuckerteam.chucker.internal.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single event on a WebSocket connection. The connection itself (handshake, open/close
 * times, failure) is stored as an [HttpTransaction] with [HttpTransaction.isWebSocket] set,
 * and messages are deleted together with it.
 */
@Entity(
    tableName = "ws_messages",
    foreignKeys = [
        ForeignKey(
            entity = HttpTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["transactionId", "seq"])],
)
internal data class WebSocketMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "transactionId") val transactionId: Long,
    /** Per-connection sequence number. Timestamps can tie, so ordering relies on this. */
    @ColumnInfo(name = "seq") val seq: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "direction") val direction: Direction,
    @ColumnInfo(name = "type") val type: Type,
    /** Text payload, hex for binary payloads, close reason or failure message. */
    @ColumnInfo(name = "payload") val payload: String?,
    /** Original payload size in bytes, or the number of dropped messages for [Type.DROPPED]. */
    @ColumnInfo(name = "payloadSize") val payloadSize: Long,
    @ColumnInfo(name = "isTruncated") val isTruncated: Boolean = false,
    @ColumnInfo(name = "closeCode") val closeCode: Int? = null,
    /** True when [okhttp3.WebSocket.send] returned false and the message never left the app. */
    @ColumnInfo(name = "isDropped") val isDropped: Boolean = false,
) {
    enum class Direction {
        SENT,
        RECEIVED,
    }

    enum class Type {
        TEXT,
        BINARY,
        CLOSE,
        FAILURE,

        /** Chucker could not keep up and skipped messages; [payloadSize] holds how many. */
        DROPPED,
    }

    val isData: Boolean get() = type == Type.TEXT || type == Type.BINARY
}
