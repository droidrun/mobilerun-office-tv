package ai.droidrun.officetv.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import ai.droidrun.officetv.R
import ai.droidrun.officetv.ui.MobilerunBlue

internal val Geist = FontFamily(
    Font(R.font.geist_extra_light, FontWeight.ExtraLight),
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
)

internal val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

internal val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Accent one word without changing the label's text, spacing or carousel identity. */
internal fun accentText(text: String, word: String?): AnnotatedString {
    if (word.isNullOrEmpty()) return AnnotatedString(text)
    val start = text.indexOf(word)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) addStyle(
            SpanStyle(fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal, color = MobilerunBlue),
            start, start + word.length,
        )
    }
}
