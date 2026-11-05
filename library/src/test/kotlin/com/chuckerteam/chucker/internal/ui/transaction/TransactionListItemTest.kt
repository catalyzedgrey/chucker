package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.chuckerteam.chucker.internal.data.entity.HttpTransactionTuple
import com.chuckerteam.chucker.internal.ui.compose.ChuckerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
internal class TransactionListItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersPathAndHost() {
        setItem(createTuple(responseCode = 200, tookMs = 12))

        composeRule.onNodeWithText("GET /some/path").assertExists()
        composeRule.onNodeWithText("example.com").assertExists()
        composeRule.onNodeWithText("200").assertExists()
    }

    @Test
    fun showsExclamationMarksForFailedTransaction() {
        setItem(createTuple(error = "boom"))

        composeRule.onNodeWithText("!!!").assertExists()
    }

    @Test
    fun rendersOpenWebSocketWithMessageCounts() {
        setItem(createTuple(responseCode = 101).copy(isWebSocket = true, sentCount = 2, receivedCount = 3))

        composeRule.onNodeWithText("WS").assertExists()
        composeRule.onNodeWithText("/some/path").assertExists()
        composeRule.onNodeWithText("Open").assertExists()
        composeRule.onNodeWithText("↑2 ↓3").assertExists()
    }

    @Test
    fun rendersClosedWebSocketWithDuration() {
        setItem(createTuple(responseCode = 101, tookMs = 1500).copy(isWebSocket = true))

        composeRule.onNodeWithText("Open").assertDoesNotExist()
        composeRule.onNodeWithText("1500 ms").assertExists()
    }

    @Test
    fun clickInvokesOnClick() {
        var clicks = 0
        setItem(createTuple(responseCode = 200), onClick = { clicks++ })

        composeRule.onNodeWithText("GET /some/path").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun longClickInvokesOnLongClick() {
        var longClicks = 0
        setItem(createTuple(responseCode = 200), onLongClick = { longClicks++ })

        composeRule.onNodeWithText("GET /some/path").performTouchInput { longClick() }

        assertThat(longClicks).isEqualTo(1)
    }

    private fun setItem(
        tuple: HttpTransactionTuple,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChuckerTheme {
                TransactionListItem(
                    transaction = tuple,
                    isSelected = false,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            }
        }
    }

    private fun createTuple(
        responseCode: Int? = null,
        tookMs: Long? = null,
        error: String? = null,
    ) = HttpTransactionTuple(
        id = 1,
        requestDate = 0L,
        tookMs = tookMs,
        protocol = "HTTP/1.1",
        method = "GET",
        host = "example.com",
        path = "/some/path",
        scheme = "https",
        responseCode = responseCode,
        requestPayloadSize = 0L,
        responsePayloadSize = 0L,
        error = error,
        graphQlOperationName = null,
        graphQlDetected = false,
    )
}
