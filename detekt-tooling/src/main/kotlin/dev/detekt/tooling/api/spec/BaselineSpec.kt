package dev.detekt.tooling.api.spec

import java.nio.file.Path

interface BaselineSpec {

    /**
     * Path to your baseline file.
     *
     * A baseline is an additional mechanism to suppress issues without adding annotations or comments in your code.
     */
    val path: Path?

    /**
     * Should this analysis write all findings to a new baseline.
     */
    val shouldCreateDuringAnalysis: Boolean

    /**
     * Should this analysis remove the entries of [path] which no longer match any finding.
     *
     * Has no effect if [shouldCreateDuringAnalysis] is set as the baseline is replaced as a whole in that case.
     */
    val shouldPruneStaleEntries: Boolean
}
