package com.chuckerteam.chucker.internal.data.repository

import androidx.lifecycle.LiveData
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
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

    /**
     * Messages of the WebSocket connections among [transactions], keyed by transaction id, in
     * order. Blocking: call it off the main thread.
     */
    fun getMessagesForExport(transactions: List<HttpTransaction>): Map<Long, List<WebSocketMessage>>
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

    override fun getMessagesForExport(transactions: List<HttpTransaction>): Map<Long, List<WebSocketMessage>> =
        transactions
            .filter { it.isWebSocket }
            .map { it.id }
            // SQLite limits how many values an IN clause can bind.
            .chunked(MAX_QUERY_PARAMETERS)
            .flatMap(dao::getForTransactions)
            .groupBy { it.transactionId }

    private companion object {
        const val MAX_QUERY_PARAMETERS = 500
    }
}
