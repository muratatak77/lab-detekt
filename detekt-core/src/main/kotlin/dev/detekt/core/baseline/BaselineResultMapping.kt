package dev.detekt.core.baseline

import dev.detekt.api.Detektion
import dev.detekt.api.Issue
import dev.detekt.api.ReportingExtension
import dev.detekt.api.STALE_BASELINE_ENTRIES_KEY
import dev.detekt.api.SetupContext
import dev.detekt.api.StaleBaselineEntries
import dev.detekt.api.getOrNull
import dev.detekt.tooling.api.Baseline
import dev.detekt.tooling.api.FindingId
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

class BaselineResultMapping : ReportingExtension {

    private var baselineFile: Path? = null
    private var createBaseline: Boolean = false
    private var pruneBaseline: Boolean = false
    private var analyzedFiles: Set<String>? = null
    private var rawResult: Detektion? = null
    private var staleEntries: Set<FindingId> = emptySet()

    override val id: String = "BaselineResultMapping"

    override fun init(context: SetupContext) {
        baselineFile = context.getOrNull(DETEKT_BASELINE_PATH_KEY)
        createBaseline = context.getOrNull(DETEKT_BASELINE_CREATION_KEY) ?: false
        pruneBaseline = context.getOrNull(DETEKT_BASELINE_PRUNE_KEY) ?: false
        analyzedFiles = context.getOrNull(DETEKT_BASELINE_ANALYZED_FILES_KEY)
    }

    override fun onRawResult(result: Detektion) {
        // Other extensions may filter issues before us, only the raw result tells which entries are really unused.
        rawResult = result
    }

    override fun transformIssues(issues: List<Issue>): List<Issue> {
        val baselineFile = baselineFile
        require(!createBaseline || (createBaseline && baselineFile != null)) {
            "Invalid baseline options invariant."
        }

        return baselineFile?.let { issues.transformWithBaseline(it) } ?: issues
    }

    private fun List<Issue>.transformWithBaseline(baselinePath: Path): List<Issue> {
        if (createBaseline) {
            createOrUpdate(baselinePath, this)
        } else if (baselineExists(baselinePath)) {
            rawResult?.let { staleEntries = handleStaleEntries(baselinePath, it) }
        }

        return filterByBaseline(baselinePath, this)
    }

    override fun userData(): Map<String, Any> =
        if (staleEntries.isEmpty()) {
            emptyMap()
        } else {
            mapOf(STALE_BASELINE_ENTRIES_KEY to StaleBaselineEntries(staleEntries, pruned = pruneBaseline))
        }

    private fun handleStaleEntries(baselineFile: Path, result: Detektion): Set<FindingId> {
        val baseline = DefaultBaseline.load(baselineFile)
        val stale = baseline.findStaleEntries(result)
        if (pruneBaseline) {
            val baselineFormat = BaselineFormat()
            baselineFormat.write(
                baselineFile,
                baselineFormat.of(baseline.manuallySuppressedIssues, baseline.currentIssues - stale),
            )
        }
        return stale
    }

    private fun Baseline.findStaleEntries(result: Detektion): Set<FindingId> {
        val reportedIds = result.issues.mapTo(HashSet()) { it.baselineId }
        val activeRuleIds = result.rules.filter { it.active }.mapTo(HashSet()) { it.id }
        val analyzedFiles = analyzedFiles
        return currentIssues.filterTo(sortedSetOf()) { id ->
            id !in reportedIds && id.ruleId in activeRuleIds && (analyzedFiles == null || id.fileName in analyzedFiles)
        }
    }

    fun filterByBaseline(baselineFile: Path, issues: List<Issue>): List<Issue> =
        if (baselineExists(baselineFile)) {
            val baseline = DefaultBaseline.load(baselineFile)
            issues.filterNot { baseline.contains(it.baselineId) }
        } else {
            issues
        }

    fun createOrUpdate(baselineFile: Path, issues: List<Issue>) {
        val ids = issues.map { it.baselineId }.toSortedSet()
        val oldBaseline = if (baselineExists(baselineFile)) {
            DefaultBaseline.load(baselineFile)
        } else {
            DefaultBaseline(emptySet(), emptySet())
        }
        val baselineFormat = BaselineFormat()
        val baseline = baselineFormat.of(oldBaseline.manuallySuppressedIssues, ids)
        if (oldBaseline != baseline) {
            baselineFile.createParentDirectories()
            baselineFormat.write(baselineFile, baseline)
        }
    }

    private fun baselineExists(baseline: Path) = baseline.exists() && baseline.isRegularFile()
}
