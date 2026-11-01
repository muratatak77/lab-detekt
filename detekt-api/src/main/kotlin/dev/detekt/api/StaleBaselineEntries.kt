package dev.detekt.api

import dev.drewhamilton.poko.Poko

/**
 * Baseline entries which no longer match any issue found by the analysis, e.g. because the code was fixed or removed.
 *
 * Entries of rules which did not run and of files which were not analyzed are not considered stale, as the analysis
 * cannot tell anything about them. Baseline ids only hold the simple file name though, so an entry of a file which was
 * not analyzed counts as stale if an analyzed file has the same name. Manually suppressed issues are curated by the
 * user and are therefore left out too.
 *
 * @property ids the sorted baseline ids which are stale
 * @property pruned whether the [ids] were removed from the baseline file during this analysis
 */
@Poko
class StaleBaselineEntries(val ids: Set<String>, val pruned: Boolean)

/**
 * The key under which [StaleBaselineEntries] are stored in [Detektion.userData].
 */
const val STALE_BASELINE_ENTRIES_KEY: String = "detekt.baseline.stale.entries"

/**
 * The stale entries of the used baseline. Empty when there is no baseline or when all its entries are still needed.
 */
val Detektion.staleBaselineEntries: StaleBaselineEntries
    get() = userData[STALE_BASELINE_ENTRIES_KEY] as? StaleBaselineEntries
        ?: StaleBaselineEntries(ids = emptySet(), pruned = false)
