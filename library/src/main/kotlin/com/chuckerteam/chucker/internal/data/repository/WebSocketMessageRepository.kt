package com.chuckerteam.chucker.internal.data.repository

import androidx.lifecycle.LiveData
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.room.ChuckerDatabase

/**
 * Repository for the messages of captured WebSocket connections. The connections themselves
 * live in [HttpTransactionRepository].
 */
internal interface WebSocketMessageRepository {
    suspend fun insertMessages(messages: List<WebSocketMessage>)

    /** Deletes the oldest messages of [transactionId] so that at most [keep] remain. */
    suspend fun trimMessages(
        transactionId: Long,
        keep: Int,
    )

    fun getMessages(transactionId: Long): LiveData<List<WebSocketMessage>>
}

internal class WebSocketMessageDatabaseRepository(
    private val database: ChuckerDatabase,
) : WebSocketMessageRepository {
    private val dao get() = database.webSocketMessageDao()

    override suspend fun insertMessages(messages: List<WebSocketMessage>) {
        if (messages.isNotEmpty()) dao.insertAll(messages)
    }

    override suspend fun trimMessages(
        transactionId: Long,
        keep: Int,
    ) {
        dao.trim(transactionId, keep)
    }

    override fun getMessages(transactionId: Long): LiveData<List<WebSocketMessage>> = dao.getForTransaction(transactionId)
}
