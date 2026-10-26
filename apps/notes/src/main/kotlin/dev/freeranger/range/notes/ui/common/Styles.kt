package dev.freeranger.range.notes.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** How formatting looks, from the theme: headings a size up and bold, marks in the dim text colour. */
@Composable
fun rememberMarkdownStyles(): Markdown.Styles {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colors, typography) {
        Markdown.Styles(
            heading = SpanStyle(fontSize = typography.titleLarge.fontSize, fontWeight = FontWeight.SemiBold),
            bold = SpanStyle(fontWeight = FontWeight.Bold),
            italic = SpanStyle(fontStyle = FontStyle.Italic),
            mark = SpanStyle(color = colors.outline),
            link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
        )
    }
}
