package com.chuckerteam.chucker.internal.ui.transaction

import android.graphics.Color
import android.text.SpannableStringBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class PayloadSearchControllerTest {
    private val changed = mutableListOf<Int>()
    private val controller = PayloadSearchController { changed.add(it) }

    private fun line(text: String) = TransactionPayloadItem.BodyLineItem(SpannableStringBuilder(text))

    private fun setItems() {
        controller.items =
            listOf(
                TransactionPayloadItem.HeaderItem(SpannableStringBuilder("header")),
                line("alpha beta alpha"),
                line("gamma"),
                line("beta"),
            )
    }

    private fun query(text: String) = controller.onQueryChanged(text, Color.YELLOW, Color.RED)

    private fun settle() = controller.onQuerySettled(Color.YELLOW, Color.RED, Color.GREEN)

    private fun navigate(goNext: Boolean) = controller.navigate(goNext, Color.YELLOW, Color.RED, Color.GREEN)

    @Test
    fun `query highlights every occurrence and reports results`() {
        setItems()

        val hasResults = query("alpha")

        assertThat(hasResults).isTrue()
        assertThat(changed).containsExactly(1)
        val position = settle()
        assertThat(position).isEqualTo(PayloadSearchController.SearchPosition(rowIndex = 1, current = 1, total = 2))
    }

    @Test
    fun `query without matches reports no results`() {
        setItems()

        assertThat(query("zzz")).isFalse()
        assertThat(settle()).isNull()
    }

    @Test
    fun `navigate forward wraps around to the first match`() {
        setItems()
        query("beta")
        settle()

        assertThat(navigate(true)?.current).isEqualTo(2)
        assertThat(navigate(true)?.current).isEqualTo(1)
    }

    @Test
    fun `navigate backward wraps around to the last match`() {
        setItems()
        query("beta")
        settle()

        assertThat(navigate(false)?.current).isEqualTo(2)
        assertThat(navigate(false)?.current).isEqualTo(1)
    }

    @Test
    fun `navigate without results returns null`() {
        setItems()
        query("zzz")

        assertThat(navigate(true)).isNull()
    }

    @Test
    fun `short query resets highlights`() {
        setItems()
        query("alpha")
        changed.clear()

        val hasResults = query("a")

        assertThat(hasResults).isFalse()
        assertThat(changed).containsExactly(1)
    }

    @Test
    fun `blank query resets highlights`() {
        setItems()
        query("alpha")
        changed.clear()

        val hasResults = query("   ")

        assertThat(hasResults).isFalse()
        assertThat(changed).containsExactly(1)
        assertThat(settle()).isNull()
    }
}
