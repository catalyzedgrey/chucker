package com.chuckerteam.chucker.sample

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chuckerteam.chucker.api.ChuckerWebSocketFactory
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI

/**
 * Connects to Postman's public Socket.IO echo server through [ChuckerWebSocketFactory], emits a
 * few events and disconnects, so the decoded Socket.IO packets show up in Chucker.
 */
class SocketIoTask(
    context: Context,
    private val client: OkHttpClient,
) : HttpTask {
    private val webSocketFactory = ChuckerWebSocketFactory.Builder(context, client).build()
    private val handler = Handler(Looper.getMainLooper())

    override fun run() {
        val options =
            IO.Options
                .builder()
                .setTransports(arrayOf(WebSocket.NAME))
                .setReconnection(false)
                .build()
                .apply {
                    // Both are needed: without them Socket.IO builds its own client and Chucker sees nothing.
                    webSocketFactory = this@SocketIoTask.webSocketFactory
                    callFactory = client
                }
        val socket = IO.socket(URI.create(SERVER_URL), options)
        socket.on(Socket.EVENT_CONNECT) {
            socket.emit("chat", JSONObject().put("user", "chucker").put("text", "Hello from Chucker"))
            socket.emit("join", "room-1", Ack { args -> Log.d(TAG, "join acked: ${args.toList()}") })
            socket.emit("typing")
        }
        socket.onAnyIncoming { args -> Log.d(TAG, "received: ${args.toList()}") }
        socket.connect()
        handler.postDelayed({ socket.disconnect() }, DISCONNECT_AFTER_MS)
    }

    private companion object {
        const val TAG = "SocketIoTask"
        const val SERVER_URL = "https://ws.postman-echo.com"
        const val DISCONNECT_AFTER_MS = 5_000L
    }
}
