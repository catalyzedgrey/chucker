package com.chuckerteam.chucker.internal.ui.transaction

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class PayloadCopyRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val copyDescription = context.getString(R.string.chucker_copy_response)
    private val rawLabel = context.getString(R.string.chucker_copy_raw)
    private val formattedLabel = context.getString(R.string.chucker_copy_formatted)

    private var rawCount = 0
    private var formattedCount = 0

    private fun setRow() {
        composeRule.setContent {
            PayloadCopyRow(onCopyRaw = { rawCount++ }, onCopyFormatted = { formattedCount++ })
        }
    }

    @Test
    fun `tap invokes raw copy only`() {
        setRow()

        composeRule.onNodeWithContentDescription(copyDescription).performClick()

        assertThat(rawCount).isEqualTo(1)
        assertThat(formattedCount).isEqualTo(0)
        composeRule.onNodeWithText(rawLabel).assertDoesNotExist()
    }

    @Test
    fun `long press opens menu with raw and formatted options`() {
        setRow()

        composeRule.onNodeWithContentDescription(copyDescription).performTouchInput { longClick() }

        assertThat(rawCount).isEqualTo(0)
        composeRule.onNodeWithText(rawLabel).assertExists()
        composeRule.onNodeWithText(formattedLabel).assertExists()
    }

    @Test
    fun `menu entries invoke their copy actions`() {
        setRow()

        composeRule.onNodeWithContentDescription(copyDescription).performTouchInput { longClick() }
        composeRule.onNodeWithText(formattedLabel).performClick()
        assertThat(formattedCount).isEqualTo(1)

        composeRule.onNodeWithContentDescription(copyDescription).performTouchInput { longClick() }
        composeRule.onNodeWithText(rawLabel).performClick()
        assertThat(rawCount).isEqualTo(1)
    }
}
