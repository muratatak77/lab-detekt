package dev.detekt.core.reporting.console

import dev.detekt.api.STALE_BASELINE_ENTRIES_KEY
import dev.detekt.api.StaleBaselineEntries
import dev.detekt.core.NL
import dev.detekt.test.TestDetektion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StaleBaselineReportSpec {

    private val subject = StaleBaselineReport()

    private fun detektion(vararg ids: String, pruned: Boolean = false) =
        TestDetektion(userData = mapOf(STALE_BASELINE_ENTRIES_KEY to StaleBaselineEntries(ids.toSet(), pruned)))

    @Test
    fun `lists the stale entries and how to remove them`() {
        assertThat(subject.render(detektion("A:File.kt:a", "B:File.kt:b"))).isEqualTo(
            "The baseline contains 2 stale entries. Use --prune-baseline or the Gradle option pruneBaseline to remove:" +
                "$NL\tA:File.kt:a$NL\tB:File.kt:b"
        )
    }

    @Test
    fun `lists the removed entries`() {
        assertThat(subject.render(detektion("A:File.kt:a", pruned = true)))
            .isEqualTo("Removed 1 stale entry from the baseline:$NL\tA:File.kt:a")
    }

    @Test
    fun `reports nothing without stale entries`() {
        assertThat(subject.render(TestDetektion())).isNull()
    }
}
