package ai.droidrun.officetv.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Text sized in cqw with CSS-style letter spacing (em) and line height (multiple of font size). */
@Composable
fun Txt(
    text: String,
    sizeCq: Float,
    color: Color,
    family: FontFamily,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    spacingEm: Float = 0f,
    lineHeightEm: Float = 1.2f,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    tabularNums: Boolean = false,
    accentWord: String? = null,
) {
    val style = TextStyle(
        color = color,
        fontSize = cqSp(sizeCq),
        fontFamily = family,
        fontWeight = weight,
        letterSpacing = spacingEm.em,
        lineHeight = (cqSp(sizeCq).value * lineHeightEm).sp,
        textAlign = align,
        fontFeatureSettings = if (tabularNums) "tnum" else null,
    )
    if (accentWord == null) {
        BasicText(text = text, modifier = modifier, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, style = style)
    } else {
        BasicText(text = remember(text, accentWord) { accentText(text, accentWord) },
            modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = style)
    }
}
