package com.chuckerteam.chucker.sample

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerWebSocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opens a WebSocket to an echo server through [ChuckerWebSocketFactory], exchanges a few
 * messages and closes it, so the connection and its messages show up in Chucker.
 */
class WebSocketTask(
    context: Context,
    client: OkHttpClient,
) : HttpTask {
    private val webSocketFactory =
        ChuckerWebSocketFactory
            .Builder(context, client)
            .redactHeaders("Authorization")
            .build()

    override fun run() {
        val request =
            Request
                .Builder()
                .url("wss://ws.postman-echo.com/raw")
                .header("Authorization", "Bearer sample-token")
                .build()
        webSocketFactory.newWebSocket(request, EchoListener())
    }

    private class EchoListener : WebSocketListener() {
        private val echoes = AtomicInteger()

        override fun onOpen(
            webSocket: WebSocket,
            response: okhttp3.Response,
        ) {
            webSocket.send("Hello from Chucker")
            webSocket.send("""{"event":"chat","data":{"user":"chucker","text":"JSON payloads are pretty-printed"}}""")
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            onEcho(webSocket)
        }

        override fun onMessage(
            webSocket: WebSocket,
            bytes: ByteString,
        ) {
            onEcho(webSocket)
        }

        private fun onEcho(webSocket: WebSocket) {
            if (echoes.incrementAndGet() == MESSAGES_SENT) {
                webSocket.close(NORMAL_CLOSURE, "Sample finished")
            }
        }
    }

    private companion object {
        // Text only: Postman's echo server drops the connection when it receives a binary frame.
        const val MESSAGES_SENT = 2
        const val NORMAL_CLOSURE = 1000
    }
}
