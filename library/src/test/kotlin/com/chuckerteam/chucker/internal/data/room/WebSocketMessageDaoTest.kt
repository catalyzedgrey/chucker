package com.chuckerteam.chucker.internal.data.room

import android.content.Context
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.internal.data.entity.HttpTransactionTuple
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.data.entity.createRequest
import com.chuckerteam.chucker.internal.data.repository.WebSocketMessageDatabaseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class WebSocketMessageDaoTest {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: ChuckerDatabase
    private lateinit var transactionDao: HttpTransactionDao
    private lateinit var messageDao: WebSocketMessageDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, ChuckerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        transactionDao = db.transactionDao()
        messageDao = db.webSocketMessageDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertConnection(): Long = transactionDao.insert(createRequest("socket").apply { isWebSocket = true })!!

    private fun message(
        transactionId: Long,
        seq: Long,
        direction: Direction = Direction.RECEIVED,
        type: Type = Type.TEXT,
    ) = WebSocketMessage(
        transactionId = transactionId,
        seq = seq,
        timestamp = seq,
        direction = direction,
        type = type,
        payload = "m$seq",
        payloadSize = 2,
    )

    @Test
    fun `messages are returned in sequence order`() =
        runBlocking {
            val id = insertConnection()
            messageDao.insertAll(listOf(message(id, 3), message(id, 1), message(id, 2)))

            assertThat(messageDao.getAllForTransaction(id).map { it.seq }).containsExactly(1L, 2L, 3L).inOrder()
        }

    @Test
    fun `export query returns the messages of several connections in order`() =
        runBlocking {
            val first = insertConnection()
            val second = insertConnection()
            val http = transactionDao.insert(createRequest("http"))!!
            messageDao.insertAll(listOf(message(second, 2), message(first, 1), message(second, 1)))

            val repository = WebSocketMessageDatabaseRepository(db)
            val transactions = transactionDao.getSelectedTransactions(listOf(first, second, http))
            val byConnection = repository.getMessagesForExport(transactions)

            assertThat(byConnection.keys).containsExactly(first, second)
            assertThat(byConnection.getValue(second).map { it.seq }).containsExactly(1L, 2L).inOrder()
        }

    @Test
    fun `trim keeps the newest messages`() =
        runBlocking {
            val id = insertConnection()
            messageDao.insertAll((1L..5L).map { message(id, it) })

            messageDao.trim(id, keep = 2)

            assertThat(messageDao.getAllForTransaction(id).map { it.seq }).containsExactly(4L, 5L).inOrder()
        }

    @Test
    fun `deleting connections deletes their messages`() =
        runBlocking {
            val kept = insertConnection()
            val deleted = insertConnection()
            messageDao.insertAll(listOf(message(kept, 1), message(deleted, 1)))

            transactionDao.deleteSelected(listOf(deleted))
            assertThat(messageDao.getAllForTransaction(deleted)).isEmpty()
            assertThat(messageDao.getAllForTransaction(kept)).hasSize(1)

            transactionDao.deleteAll()
            assertThat(messageDao.getAllForTransaction(kept)).isEmpty()
        }

    @Test
    fun `tuples count sent and received data messages only`() =
        runBlocking {
            val id = insertConnection()
            messageDao.insertAll(
                listOf(
                    message(id, 1, Direction.SENT),
                    message(id, 2, Direction.SENT, Type.BINARY),
                    message(id, 3, Direction.RECEIVED),
                    message(id, 4, Direction.SENT, Type.CLOSE),
                    message(id, 5, Direction.RECEIVED, Type.FAILURE),
                ),
            )

            val tuple: HttpTransactionTuple = transactionDao.getSortedTuples().awaitValue().single()
            assertThat(tuple.isWebSocket).isTrue()
            assertThat(tuple.sentCount).isEqualTo(2)
            assertThat(tuple.receivedCount).isEqualTo(1)
        }

    private fun <T> LiveData<T>.awaitValue(): T {
        var value: T? = null
        val observer = Observer<T> { value = it }
        observeForever(observer)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (value == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        removeObserver(observer)
        return checkNotNull(value) { "LiveData had no value" }
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
        const val POLL_MS = 10L
    }
}
