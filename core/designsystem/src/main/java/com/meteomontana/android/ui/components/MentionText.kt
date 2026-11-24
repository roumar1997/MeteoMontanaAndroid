package com.meteomontana.android.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.meteomontana.android.ui.theme.Terra

/** Menciones @username (3-20, minúsculas/dígitos/_) sin cortar palabra. */
private val MENTION_REGEX = Regex("@([a-z0-9_]{3,20})(?![a-z0-9_])", RegexOption.IGNORE_CASE)

/**
 * Igual que un Text normal, pero pinta los `@usuario` en terracota y los hace
 * pulsables → [onOpenUser] con el username (sin @). Se usa en descripciones y
 * comentarios del feed.
 */
@Composable
fun MentionText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onOpenUser: (username: String) -> Unit
) {
    val baseColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onBackground
    val annotated = remember(text, baseColor) {
        buildAnnotatedString {
            var last = 0
            for (m in MENTION_REGEX.findAll(text)) {
                append(text.substring(last, m.range.first))
                val username = m.groupValues[1].lowercase()
                pushStringAnnotation("mention", username)
                withStyle(SpanStyle(color = Terra, fontWeight = FontWeight.SemiBold)) {
                    append(m.value)   // "@username" tal cual se escribió
                }
                pop()
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.merge(TextStyle(color = baseColor)),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotated.getStringAnnotations("mention", offset, offset)
                .firstOrNull()?.let { onOpenUser(it.item) }
        }
    )
}
