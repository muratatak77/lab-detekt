package dev.detekt.core.reporting.console

import dev.detekt.api.ConsoleReport
import dev.detekt.api.Detektion
import dev.detekt.api.staleBaselineEntries

/**
 * Lists the baseline entries which no longer match any finding, so that they do not silently hide future regressions.
 * See: https://detekt.dev/docs/introduction/baseline
 */
class StaleBaselineReport : ConsoleReport {

    override val id: String = "StaleBaselineReport"

    /**
     * Print the entries right before the notifications to keep them close to the end of the output.
     */
    override val priority: Int = Int.MIN_VALUE + 2

    /**
     * Returns a headline followed by one line per stale baseline entry of [detektion], or `null` when there are none.
     */
    override fun render(detektion: Detektion): String? {
        val stale = detektion.staleBaselineEntries
        if (stale.ids.isEmpty()) {
            return null
        }
        val entries = if (stale.ids.size == 1) "1 stale entry" else "${stale.ids.size} stale entries"
        val headline = if (stale.pruned) {
            "Removed $entries from the baseline:"
        } else {
            "The baseline contains $entries. Use --prune-baseline or the Gradle option pruneBaseline to remove:"
        }
        return (listOf(headline) + stale.ids.map { "\t$it" }).joinToString(System.lineSeparator())
    }
}
