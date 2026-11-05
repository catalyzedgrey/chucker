package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Direction
import com.chuckerteam.chucker.internal.data.entity.WebSocketMessage.Type
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class WebSocketMessagesContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun message(
        seq: Long,
        direction: Direction,
        payload: String,
    ) = WebSocketMessage(
        id = seq,
        transactionId = 1,
        seq = seq,
        timestamp = seq,
        direction = direction,
        type = Type.TEXT,
        payload = payload,
        payloadSize = payload.length.toLong(),
    )

    private val messages =
        listOf(
            message(1, Direction.SENT, """42["chat",{"text":"hello"}]"""),
            message(2, Direction.RECEIVED, "2probe"),
        )

    private fun setContent(
        url: String,
        query: String = "",
        formatJson: Boolean = false,
    ) {
        composeRule.setContent {
            ChuckerTheme {
                WebSocketMessagesContent(
                    messages = messages,
                    openedAt = 0,
                    connectionUrl = url,
                    searchQuery = query,
                    formatJson = formatJson,
                )
            }
        }
    }

    @Test
    fun formatJsonPrettyPrintsJsonMessages() {
        setContent("https://example.com/socket.io/?EIO=4", formatJson = true)

        // Pretty-printed output puts a space after the colon; the compact payload has none.
        composeRule.onNodeWithText("\"text\": \"hello\"", substring = true).assertExists()
        composeRule.onNodeWithText("""[{"text":"hello"}]""").assertDoesNotExist()
    }

    @Test
    fun jsonStaysCompactWithoutFormatJson() {
        setContent("https://example.com/socket.io/?EIO=4", formatJson = false)

        composeRule.onNodeWithText("""[{"text":"hello"}]""").assertExists()
    }

    @Test
    fun decodesSocketIoAndHidesPingPongUntilEnabled() {
        setContent("https://example.com/socket.io/?EIO=4")

        composeRule.onNodeWithText("EVENT chat").assertExists()
        composeRule.onNodeWithText("""[{"text":"hello"}]""").assertExists()
        composeRule.onNodeWithText("PING").assertDoesNotExist()

        composeRule.onNodeWithText("Ping/pong").performClick()

        composeRule.onNodeWithText("PING").assertExists()
    }

    @Test
    fun showsRawPayloadsForPlainWebSockets() {
        setContent("https://example.com/chat")

        composeRule.onNodeWithText("""42["chat",{"text":"hello"}]""").assertExists()
        composeRule.onNodeWithText("Ping/pong").assertDoesNotExist()
    }

    @Test
    fun showsEmptyStateWhenSearchMatchesNothing() {
        setContent("https://example.com/chat", query = "nope")

        composeRule.onNodeWithText("No matching messages").assertExists()
    }
}
