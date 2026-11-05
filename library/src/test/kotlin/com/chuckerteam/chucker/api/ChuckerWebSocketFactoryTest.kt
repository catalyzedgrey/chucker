package com.chuckerteam.chucker.api

import android.content.Context
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.util.ChuckerInterceptorDelegate
import com.chuckerteam.chucker.util.FakeWebSocketStore
import com.chuckerteam.chucker.util.NoLoggerRule
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(NoLoggerRule::class)
internal class ChuckerWebSocketFactoryTest {
    private val server = MockWebServer()
    private val store = FakeWebSocketStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun factory(
        client: OkHttpClient = OkHttpClient(),
        configure: ChuckerWebSocketFactory.Builder.() -> Unit = {},
    ) = ChuckerWebSocketFactory
        .Builder(mockk<Context>(), client)
        .store(store, scope, batchDelayMs = 0)
        .apply(configure)
        .build()

    private fun request(vararg headers: Pair<String, String>) =
        Request
            .Builder()
            .url(server.url("/chat"))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

    private fun enqueueEchoServer() {
        server.enqueue(MockResponse().withWebSocketUpgrade(EchoServer))
    }

    @Test
    fun `records the handshake and messages in both directions`() {
        enqueueEchoServer()
        val listener = TestListener()

        val socket = factory().newWebSocket(request(), listener)
        listener.awaitOpen()
        socket.send("hello")
        listener.awaitMessage("echo:hello")
        socket.close(NORMAL_CLOSURE, "bye")
        listener.awaitClosed()

        store.awaitUntil { connections.singleOrNull()?.tookMs != null && messages.size == 4 }
        val connection = store.connections.single()
        assertThat(connection.isWebSocket).isTrue()
        assertThat(connection.method).isEqualTo("GET")
        assertThat(connection.path).isEqualTo("/chat")
        assertThat(connection.responseCode).isEqualTo(SWITCHING_PROTOCOLS)
        assertThat(connection.error).isNull()
        assertThat(connection.getParsedRequestHeaders()!!.map { it.name }).contains("Sec-WebSocket-Key")

        assertThat(store.messagesInOrder().map { it.summary() })
            .containsExactly(
                "SENT TEXT hello",
                "RECEIVED TEXT echo:hello",
                "SENT CLOSE bye 1000",
                "RECEIVED CLOSE bye 1000",
            ).inOrder()
    }

    @Test
    fun `listener callbacks get the recording socket, so their sends are recorded`() {
        enqueueEchoServer()
        var socketInCallback: WebSocket? = null
        val listener =
            object : TestListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    socketInCallback = webSocket
                    webSocket.send("from-open")
                    super.onOpen(webSocket, response)
                }
            }

        val socket = factory().newWebSocket(request(), listener)
        listener.awaitMessage("echo:from-open")
        socket.close(NORMAL_CLOSURE, null)

        assertThat(socketInCallback).isSameInstanceAs(socket)
        store.awaitUntil { messages.size >= 2 }
        assertThat(store.messagesInOrder().take(2).map { it.summary() })
            .containsExactly("SENT TEXT from-open", "RECEIVED TEXT echo:from-open")
            .inOrder()
    }

    @Test
    fun `records a failed handshake`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK))
        val listener = TestListener()

        factory().newWebSocket(request(), listener)
        listener.awaitFailure()

        store.awaitUntil { connections.singleOrNull()?.error != null && messages.isNotEmpty() }
        val connection = store.connections.single()
        assertThat(connection.responseCode).isEqualTo(HTTP_OK)
        assertThat(connection.error).contains("101")
        assertThat(connection.tookMs).isNotNull()
        assertThat(store.messagesInOrder().single().type).isEqualTo(Type.FAILURE)
    }

    @Test
    fun `redacts handshake headers`() {
        enqueueEchoServer()
        val listener = TestListener()

        val socket =
            factory { redactHeaders("Authorization") }
                .newWebSocket(request("Authorization" to "secret"), listener)
        listener.awaitOpen()
        socket.close(NORMAL_CLOSURE, null)

        store.awaitUntil { connections.singleOrNull()?.responseCode != null }
        val authorization =
            store.connections
                .single()
                .getParsedRequestHeaders()!!
                .single { it.name == "Authorization" }
        assertThat(authorization.value).isEqualTo("**")
    }

    @Test
    fun `interceptor skips handshakes opened through the factory`() {
        enqueueEchoServer()
        val interceptor = ChuckerInterceptorDelegate(cacheDirectoryProvider = { tempDir })
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val listener = TestListener()

        val socket = factory(client).newWebSocket(request(), listener)
        listener.awaitOpen()
        socket.close(NORMAL_CLOSURE, null)

        interceptor.expectNoTransactions()
        store.awaitUntil { connections.singleOrNull()?.responseCode == SWITCHING_PROTOCOLS }
    }

    private fun WebSocketMessage.summary() = listOfNotNull(direction, type, payload, closeCode).joinToString(" ")

    private object EchoServer : WebSocketListener() {
        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            webSocket.send("echo:$text")
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, reason)
        }
    }

    private open class TestListener : WebSocketListener() {
        private val opened = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        private val failed = CountDownLatch(1)
        private val received = java.util.concurrent.LinkedBlockingQueue<String>()

        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            opened.countDown()
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            received += text
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, reason)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            closed.countDown()
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            failed.countDown()
        }

        fun awaitOpen() = check(opened.await(TIMEOUT_S, TimeUnit.SECONDS)) { "Socket did not open" }

        fun awaitClosed() = check(closed.await(TIMEOUT_S, TimeUnit.SECONDS)) { "Socket did not close" }

        fun awaitFailure() = check(failed.await(TIMEOUT_S, TimeUnit.SECONDS)) { "Socket did not fail" }

        fun awaitMessage(expected: String) {
            assertThat(received.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(expected)
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val SWITCHING_PROTOCOLS = 101
        const val HTTP_OK = 200
        const val TIMEOUT_S = 5L
    }
}
