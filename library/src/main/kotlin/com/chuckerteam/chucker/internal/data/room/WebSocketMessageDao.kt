package com.chuckerteam.chucker.internal.data.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage

@Dao
internal interface WebSocketMessageDao {
    @Insert
    suspend fun insertAll(messages: List<WebSocketMessage>)

    @Query("SELECT * FROM ws_messages WHERE transactionId = :transactionId ORDER BY seq ASC")
    fun getForTransaction(transactionId: Long): LiveData<List<WebSocketMessage>>

    @Query("SELECT * FROM ws_messages WHERE transactionId = :transactionId ORDER BY seq ASC")
    suspend fun getAllForTransaction(transactionId: Long): List<WebSocketMessage>

    /** Keeps only the newest [keep] messages of a connection. */
    @Query(
        "DELETE FROM ws_messages WHERE transactionId = :transactionId AND id NOT IN " +
            "(SELECT id FROM ws_messages WHERE transactionId = :transactionId ORDER BY seq DESC LIMIT :keep)",
    )
    suspend fun trim(
        transactionId: Long,
        keep: Int,
    ): Int
}
