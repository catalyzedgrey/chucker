package com.chuckerteam.chucker.internal.ui.compose

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Converts the Android spans Chucker produces (JSON syntax coloring, search highlights,
 * HTML-formatted headers) into an [AnnotatedString]. Pure rendering adapter: span
 * generation itself is unchanged.
 */
internal fun CharSequence.toAnnotatedString(): AnnotatedString {
    if (this !is Spanned) return AnnotatedString(toString())
    val source = this
    return buildAnnotatedString {
        append(source.toString())
        source.getSpans(0, source.length, Any::class.java).forEach { span ->
            val start = source.getSpanStart(span)
            val end = source.getSpanEnd(span)
            val style = span.toSpanStyle()
            if (style != null && start >= 0 && end <= source.length && start < end) {
                addStyle(style, start, end)
            }
        }
    }
}

private fun Any.toSpanStyle(): SpanStyle? =
    when (this) {
        is ForegroundColorSpan -> SpanStyle(color = Color(foregroundColor))
        is BackgroundColorSpan -> SpanStyle(background = Color(backgroundColor))
        is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
        is StyleSpan -> style.toSpanStyle()
        else -> null
    }

private fun Int.toSpanStyle(): SpanStyle? =
    when (this) {
        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        else -> null
    }
