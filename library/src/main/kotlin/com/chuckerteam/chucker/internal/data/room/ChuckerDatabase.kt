package com.chuckerteam.chucker.internal.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage

@Database(entities = [HttpTransaction::class, WebSocketMessage::class], version = 12, exportSchema = false)
internal abstract class ChuckerDatabase : RoomDatabase() {
    abstract fun transactionDao(): HttpTransactionDao

    abstract fun webSocketMessageDao(): WebSocketMessageDao

    companion object {
        private const val DB_NAME = "chucker.db"

        fun create(applicationContext: Context): ChuckerDatabase =
            Room
                .databaseBuilder(applicationContext, ChuckerDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
