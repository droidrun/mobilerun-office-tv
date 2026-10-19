package ai.droidrun.officetv.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Space grouping stays readable from across the room, German decimal comma. */
fun formatNumber(value: Number, demo: Boolean = false): String = if (demo) "—" else DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.GERMANY).apply {
    groupingSeparator = ' '
}).format(value)
