package com.chuckerteam.chucker.internal.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider.initialize
import com.chuckerteam.chucker.internal.data.room.ChuckerDatabase

/**
 * A singleton to hold the [HttpTransactionRepository] instance.
 * Make sure you call [initialize] before accessing the stored instance.
 */
internal object RepositoryProvider {
    private var transactionRepository: HttpTransactionRepository? = null
    private var webSocketMessageRepository: WebSocketMessageRepository? = null

    fun transaction(): HttpTransactionRepository =
        checkNotNull(transactionRepository) {
            "You can't access the transaction repository if you don't initialize it!"
        }

    fun webSocketMessages(): WebSocketMessageRepository =
        checkNotNull(webSocketMessageRepository) {
            "You can't access the WebSocket message repository if you don't initialize it!"
        }

    /**
     * Idempotent method. Must be called before accessing the repositories.
     */
    fun initialize(applicationContext: Context) {
        if (transactionRepository == null) {
            val db = ChuckerDatabase.create(applicationContext)
            transactionRepository = HttpTransactionDatabaseRepository(db)
            webSocketMessageRepository = WebSocketMessageDatabaseRepository(db)
        }
    }

    /**
     * Cleanup stored singleton objects
     */
    @VisibleForTesting
    fun close() {
        transactionRepository = null
        webSocketMessageRepository = null
    }
}
