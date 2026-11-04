package com.chuckerteam.chucker.internal.ui.transaction

import android.text.SpannableStringBuilder
import androidx.core.text.getSpans
import com.chuckerteam.chucker.internal.support.SpanTextUtil
import com.chuckerteam.chucker.internal.support.highlightWithDefinedColors
import com.chuckerteam.chucker.internal.support.highlightWithDefinedColorsSubstring
import com.chuckerteam.chucker.internal.support.indicesOf
import kotlin.math.abs

/**
 * Search highlighting and navigation over payload rows.
 *
 * The highlighting algorithm is moved verbatim from the former `TransactionBodyAdapter`, and the
 * navigation state from the former `TransactionPayloadFragment`. Instead of calling
 * `notifyItemChanged`, every mutation invokes [onItemChanged] with the affected row index.
 */
internal class PayloadSearchController(
    private val onItemChanged: (Int) -> Unit,
) {
    internal data class SearchItemBodyLine(
        val indexBodyLine: Int,
        val indexStartOfQuerySubString: Int,
    )

    /** Result of moving to a search match: which row to scroll to and the "n / total" summary. */
    internal data class SearchPosition(
        val rowIndex: Int,
        val current: Int,
        val total: Int,
    )

    var items: List<TransactionPayloadItem> = emptyList()

    private val scrollableIndices = arrayListOf<SearchItemBodyLine>()
    private var currentSearchScrollIndex = -1
    private var currentSearchQuery: String = ""

    val hasResults: Boolean get() = scrollableIndices.isNotEmpty()

    /**
     * Applies a new query. Returns true when results were found (former `onQueryTextChange`).
     */
    fun onQueryChanged(
        newText: String,
        backgroundSpanColor: Int,
        foregroundSpanColor: Int,
    ): Boolean {
        scrollableIndices.clear()
        currentSearchQuery = newText
        currentSearchScrollIndex = -1

        if (newText.isNotBlank() && newText.length > NUMBER_OF_IGNORED_SYMBOLS) {
            val listOfSearchQuery =
                highlightQueryWithColors(
                    newText,
                    backgroundSpanColor,
                    foregroundSpanColor,
                )
            if (listOfSearchQuery.isNotEmpty()) {
                scrollableIndices.addAll(listOfSearchQuery)
            } else {
                resetHighlight()
            }
        } else {
            resetHighlight()
        }
        return scrollableIndices.isNotEmpty()
    }

    /** Called after the debounce delay (former `onQueryTextChange` delayed block). */
    fun onQuerySettled(
        backgroundSpanColor: Int,
        foregroundSpanColor: Int,
        backgroundSpanColorSearchItem: Int,
    ): SearchPosition? =
        if (scrollableIndices.isNotEmpty()) {
            scrollToSearchedItemPosition(0, backgroundSpanColor, foregroundSpanColor, backgroundSpanColorSearchItem)
        } else {
            currentSearchScrollIndex = -1
            null
        }

    /** Former `onSearchScrollerButtonClick`. */
    fun navigate(
        goNext: Boolean,
        backgroundSpanColor: Int,
        foregroundSpanColor: Int,
        backgroundSpanColorSearchItem: Int,
    ): SearchPosition? {
        if (scrollableIndices.isEmpty()) return null
        val scrollToIndex =
            if (goNext) {
                ((currentSearchScrollIndex + 1) % scrollableIndices.size)
            } else {
                (abs(currentSearchScrollIndex - 1 + scrollableIndices.size) % scrollableIndices.size)
            }
        return scrollToSearchedItemPosition(
            scrollToIndex,
            backgroundSpanColor,
            foregroundSpanColor,
            backgroundSpanColorSearchItem,
        )
    }

    private fun scrollToSearchedItemPosition(
        positionOfScrollableIndices: Int,
        backgroundSpanColor: Int,
        foregroundSpanColor: Int,
        backgroundSpanColorSearchItem: Int,
    ): SearchPosition? {
        // reset the last searched item highlight if done
        scrollableIndices.getOrNull(currentSearchScrollIndex)?.let {
            highlightItemWithColorOnPosition(
                it.indexBodyLine,
                it.indexStartOfQuerySubString,
                currentSearchQuery,
                backgroundSpanColor,
                foregroundSpanColor,
            )
        }
        currentSearchScrollIndex = positionOfScrollableIndices
        val scrollTo = scrollableIndices.getOrNull(positionOfScrollableIndices) ?: return null
        // highlight the next navigated item
        highlightItemWithColorOnPosition(
            scrollTo.indexBodyLine,
            scrollTo.indexStartOfQuerySubString,
            currentSearchQuery,
            backgroundSpanColorSearchItem,
            foregroundSpanColor,
        )
        currentSearchScrollIndex = positionOfScrollableIndices
        return SearchPosition(
            rowIndex = scrollTo.indexBodyLine,
            current = positionOfScrollableIndices + 1,
            total = scrollableIndices.size,
        )
    }

    internal fun highlightQueryWithColors(
        newText: String,
        backgroundColor: Int,
        foregroundColor: Int,
    ): List<SearchItemBodyLine> {
        val listOfSearchItems = arrayListOf<SearchItemBodyLine>()
        items
            .withIndex()
            .forEach { (index, item) ->
                if (item !is TransactionPayloadItem.BodyLineItem) return@forEach
                val listOfOccurrences = item.line.indicesOf(newText)
                if (listOfOccurrences.isNotEmpty()) {
                    // storing the occurrences and their positions
                    listOfOccurrences.forEach {
                        listOfSearchItems.add(
                            SearchItemBodyLine(
                                indexBodyLine = index,
                                indexStartOfQuerySubString = it,
                            ),
                        )
                    }

                    // highlighting the occurrences
                    item.line.clearHighlightSpans()
                    item.line =
                        item.line.highlightWithDefinedColors(
                            newText,
                            listOfOccurrences,
                            backgroundColor,
                            foregroundColor,
                        )
                    onItemChanged(index)
                } else {
                    // Let's clear the spans if we haven't found the query string.
                    val removedSpansCount = item.line.clearHighlightSpans()
                    if (removedSpansCount > 0) {
                        onItemChanged(index)
                    }
                }
            }
        return listOfSearchItems
    }

    internal fun highlightItemWithColorOnPosition(
        position: Int,
        queryStartPosition: Int,
        queryText: String,
        backgroundColor: Int,
        foregroundColor: Int,
    ) {
        val item = items.getOrNull(position) as? TransactionPayloadItem.BodyLineItem
        if (item != null) {
            item.line =
                item.line.highlightWithDefinedColorsSubstring(
                    queryText,
                    queryStartPosition,
                    backgroundColor,
                    foregroundColor,
                )
            onItemChanged(position)
        }
    }

    internal fun resetHighlight() {
        items
            .withIndex()
            .forEach { (index, item) ->
                if (item !is TransactionPayloadItem.BodyLineItem) return@forEach
                val removedSpansCount = item.line.clearHighlightSpans()
                if (removedSpansCount > 0) {
                    onItemChanged(index)
                }
            }
    }

    /**
     * Clear span that created during search process
     * @return Number of spans that removed.
     */
    private fun SpannableStringBuilder.clearHighlightSpans(): Int {
        var removedSpansCount = 0
        val spanList = getSpans<Any>(0, length)
        for (span in spanList) {
            if (span !is SpanTextUtil.ChuckerForegroundColorSpan) {
                removeSpan(span)
                removedSpansCount++
            }
        }
        return removedSpansCount
    }

    private companion object {
        const val NUMBER_OF_IGNORED_SYMBOLS = 1
    }
}
