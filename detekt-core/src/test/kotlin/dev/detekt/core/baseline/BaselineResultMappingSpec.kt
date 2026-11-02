package dev.detekt.core.baseline

import dev.detekt.api.Issue
import dev.detekt.api.STALE_BASELINE_ENTRIES_KEY
import dev.detekt.api.StaleBaselineEntries
import dev.detekt.api.testfixtures.TestSetupContext
import dev.detekt.api.testfixtures.createIssue
import dev.detekt.api.testfixtures.createIssueEntity
import dev.detekt.api.testfixtures.createRuleInstance
import dev.detekt.test.TestDetektion
import dev.detekt.test.utils.resourceAsPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BaselineResultMappingSpec {
    @TempDir
    @Suppress("VarCouldBeVal")
    private lateinit var dir: Path
    private lateinit var baselineFile: Path
    private val existingBaselineFile = resourceAsPath("/baseline_feature/valid-baseline.xml")
    private val issues = listOf(
        createIssue(
            ruleInstance = createRuleInstance("SomeIssue", "RuleSet"),
            entity = createIssueEntity(signature = "SomeSignature"),
        ),
        createIssue(
            ruleId = "LongParameterList",
            entity = createIssueEntity(signature = "Signature")
        ),
        createIssue(
            ruleId = "LongMethod",
            entity = createIssueEntity(signature = "Signature")
        ),
        createIssue(
            ruleId = "FeatureEnvy",
            entity = createIssueEntity(signature = "Signature")
        ),
    )

    @BeforeEach
    fun setup() {
        baselineFile = dir.resolve("baseline.xml")
    }

    @Test
    fun `should not create a new baseline file when no issues occurred`() {
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        mapping.transformIssues(emptyList())

        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `should not update an existing baseline file if option configured as false`() {
        val existing = DefaultBaseline.load(existingBaselineFile)
        val mapping = resultMapping(
            baselineFile = existingBaselineFile,
            createBaseline = false,
        )

        mapping.transformIssues(issues)

        val changed = DefaultBaseline.load(existingBaselineFile)
        assertThat(changed).isEqualTo(existing)
    }

    @Test
    fun `should not update an existing baseline file if option is not configured`() {
        val existing = DefaultBaseline.load(existingBaselineFile)
        val mapping = resultMapping(
            baselineFile = existingBaselineFile,
            createBaseline = null,
        )

        mapping.transformIssues(issues)

        val changed = DefaultBaseline.load(existingBaselineFile)
        assertThat(changed).isEqualTo(existing)
    }

    @Test
    fun `should not create a new baseline file if no file is configured`() {
        val mapping = resultMapping(
            baselineFile = null,
            createBaseline = false,
        )

        mapping.transformIssues(issues)

        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `should create a new baseline file if a file is configured`() {
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        mapping.transformIssues(issues)

        assertThat(baselineFile).exists()
    }

    @Test
    fun `should update an existing baseline file if a file is configured`() {
        existingBaselineFile.copyTo(baselineFile)
        val existing = DefaultBaseline.load(baselineFile)
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        mapping.transformIssues(issues)

        val changed = DefaultBaseline.load(baselineFile)
        assertThat(changed).isNotEqualTo(existing)
    }

    @Test
    fun `returns a filtered issues list when the baseline exists`() {
        existingBaselineFile.copyTo(baselineFile)

        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        val filtered = mapping.filterByBaseline(DefaultBaseline.load(baselineFile), issues)

        assertThat(filtered).isNotEqualTo(issues)
    }

    @Test
    fun `returns the same issues list when the baseline doesn't exist`() {
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = false,
        )

        val filtered = mapping.transformIssues(issues)

        assertThat(filtered).isEqualTo(issues)
    }

    @Test
    fun `doesn't create a baseline file without issues`() {
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = false,
        )

        mapping.createOrUpdate(baselineFile, emptyList())

        assertThat(baselineFile).doesNotExist()
    }

    @Test
    fun `creates on top of an existing a baseline file without issues`() {
        existingBaselineFile.copyTo(baselineFile)
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        mapping.createOrUpdate(baselineFile, emptyList())

        assertThat(baselineFile).hasContent(
            """
                <?xml version="1.0" ?>
                <SmellBaseline>
                  <ManuallySuppressedIssues>
                    <ID>LongParameterList:TestFile.kt:Signature</ID>
                    <ID>LongMethod:TestFile.kt:Signature</ID>
                  </ManuallySuppressedIssues>
                  <CurrentIssues/>
                </SmellBaseline>
            """.trimIndent()
        )
    }

    @Test
    fun `creates on top of an existing a baseline file with issues`() {
        existingBaselineFile.copyTo(baselineFile)
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = true,
        )

        mapping.createOrUpdate(baselineFile, listOf(createIssue()))

        assertThat(baselineFile).hasContent(
            """
                <?xml version="1.0" ?>
                <SmellBaseline>
                  <ManuallySuppressedIssues>
                    <ID>LongParameterList:TestFile.kt:Signature</ID>
                    <ID>LongMethod:TestFile.kt:Signature</ID>
                  </ManuallySuppressedIssues>
                  <CurrentIssues>
                    <ID>TestSmell/id:TestFile.kt:TestEntitySignature</ID>
                  </CurrentIssues>
                </SmellBaseline>
            """.trimIndent()
        )
    }
}

class StaleBaselineEntriesSpec {
    @TempDir
    @Suppress("VarCouldBeVal")
    private lateinit var dir: Path

    private val fixedIssue = "RuleA:TestFile.kt:Fixed"
    private val openIssue = "RuleA:TestFile.kt:Open"
    private val manualIssue = "RuleA:TestFile.kt:Manual"
    private val otherFileIssue = "RuleA:OtherFile.kt:Fixed"
    private val otherRuleIssue = "RuleB:TestFile.kt:Fixed"

    private val ruleA = createRuleInstance("RuleA", "RuleSet")
    private val ruleB = createRuleInstance("RuleB", "RuleSet", active = false)
    private val open = createIssue(ruleInstance = ruleA, entity = createIssueEntity(signature = "Open"))

    private fun baselineWith(vararg current: String): Path =
        dir.resolve("baseline.xml").also {
            BaselineFormat().write(it, DefaultBaseline(setOf(manualIssue), current.toSet()))
        }

    private fun BaselineResultMapping.run(issues: List<Issue>): List<Issue> {
        onRawResult(TestDetektion(*issues.toTypedArray(), rules = listOf(ruleA, ruleB)))
        return transformIssues(issues)
    }

    private fun BaselineResultMapping.staleEntries() = userData()[STALE_BASELINE_ENTRIES_KEY] as StaleBaselineEntries?

    @Test
    fun `reports the entries which no longer match an issue`() {
        val mapping = staleMapping(baselineWith(fixedIssue, openIssue))

        val issues = mapping.run(listOf(open))

        assertThat(issues).isEmpty()
        assertThat(mapping.staleEntries()).isEqualTo(StaleBaselineEntries(setOf(fixedIssue), pruned = false))
    }

    @Test
    fun `sorts the reported entries`() {
        val mapping = staleMapping(baselineWith("RuleA:TestFile.kt:Z", "RuleA:TestFile.kt:B", "RuleA:TestFile.kt:M"))

        mapping.run(emptyList())

        assertThat(mapping.staleEntries()?.ids).containsExactly(
            "RuleA:TestFile.kt:B",
            "RuleA:TestFile.kt:M",
            "RuleA:TestFile.kt:Z",
        )
    }

    @Test
    fun `publishes nothing if all entries are in use`() {
        val mapping = staleMapping(baselineWith(openIssue))

        mapping.run(listOf(open))

        assertThat(mapping.userData()).isEmpty()
    }

    @Test
    fun `does not consider an entry stale when its issue was filtered by an earlier extension`() {
        val mapping = staleMapping(baselineWith(openIssue))

        mapping.onRawResult(TestDetektion(open, rules = listOf(ruleA, ruleB)))
        mapping.transformIssues(emptyList())

        assertThat(mapping.userData()).isEmpty()
    }

    @Test
    fun `keeps entries of rules which did not run`() {
        val mapping = staleMapping(baselineWith(otherRuleIssue, "UnknownRule:TestFile.kt:Fixed"))

        mapping.run(emptyList())

        assertThat(mapping.userData()).isEmpty()
    }

    @Test
    fun `keeps entries of files which were not analyzed`() {
        val mapping = staleMapping(baselineWith(otherFileIssue, fixedIssue))

        mapping.run(emptyList())

        assertThat(mapping.staleEntries()?.ids).containsExactly(fixedIssue)
    }

    @Test
    fun `keeps entries of files the rule skipped through its path filters`() {
        val baselineFile = baselineWith(otherFileIssue, fixedIssue)
        val mapping = resultMapping(
            baselineFile = baselineFile,
            createBaseline = false,
            pruneBaseline = true,
            analyzedFiles = mapOf("RuleA" to setOf("TestFile.kt"), "RuleC" to setOf("OtherFile.kt")),
        )

        mapping.run(emptyList())

        assertThat(mapping.staleEntries()?.ids).containsExactly(fixedIssue)
        assertThat(DefaultBaseline.load(baselineFile).currentIssues).containsExactly(otherFileIssue)
    }

    @Test
    fun `can't tell an entry of a not analyzed file from an analyzed file of the same name`() {
        // The id could belong to any TestFile.kt: it holds the simple file name only, see baseline.mdx.
        val baselineFile = baselineWith(fixedIssue)
        val mapping = staleMapping(baselineFile, prune = true)

        mapping.run(emptyList())

        assertThat(mapping.staleEntries()?.ids).containsExactly(fixedIssue)
        assertThat(DefaultBaseline.load(baselineFile).currentIssues).isEmpty()
    }

    @Test
    fun `leaves the baseline file untouched without the prune option`() {
        val baselineFile = baselineWith(fixedIssue)
        val before = baselineFile.readText()

        staleMapping(baselineFile).run(emptyList())

        assertThat(baselineFile).hasContent(before)
    }

    @Test
    fun `prunes only the stale entries`() {
        val baselineFile = baselineWith(fixedIssue, openIssue, otherFileIssue)
        val mapping = staleMapping(baselineFile, prune = true)

        val issues = mapping.run(listOf(open))

        assertThat(issues).isEmpty()
        assertThat(mapping.staleEntries()).isEqualTo(StaleBaselineEntries(setOf(fixedIssue), pruned = true))
        assertThat(DefaultBaseline.load(baselineFile)).isEqualTo(
            DefaultBaseline(setOf(manualIssue), setOf(openIssue, otherFileIssue))
        )
    }

    @Test
    fun `does not rewrite the baseline file if there is nothing to prune`() {
        val baselineFile = dir.resolve("baseline.xml").apply {
            writeText(
                """
                    <?xml version="1.0" ?>
                    <!-- hand-formatted, keep the reviewers happy -->
                    <SmellBaseline>
                        <ManuallySuppressedIssues/>
                        <CurrentIssues><ID>$openIssue</ID></CurrentIssues>
                    </SmellBaseline>
                """.trimIndent()
            )
        }
        val before = baselineFile.readText()

        staleMapping(baselineFile, prune = true).run(listOf(open))

        assertThat(baselineFile).hasContent(before)
    }

    @Test
    fun `does not look for stale entries while creating a baseline`() {
        val baselineFile = baselineWith(fixedIssue)
        val mapping = resultMapping(baselineFile, createBaseline = true)

        mapping.run(listOf(open))

        assertThat(mapping.userData()).isEmpty()
    }

    private fun staleMapping(baselineFile: Path, prune: Boolean = false) =
        resultMapping(
            baselineFile = baselineFile,
            createBaseline = false,
            pruneBaseline = prune,
            analyzedFiles = mapOf("RuleA" to setOf("TestFile.kt")),
        )
}

private fun resultMapping(
    baselineFile: Path?,
    createBaseline: Boolean?,
    pruneBaseline: Boolean? = null,
    analyzedFiles: Map<String, Set<String>>? = null,
) = BaselineResultMapping().apply {
    init(
        TestSetupContext(
            properties = mapOf(
                DETEKT_BASELINE_PATH_KEY to baselineFile,
                DETEKT_BASELINE_CREATION_KEY to createBaseline,
                DETEKT_BASELINE_PRUNE_KEY to pruneBaseline,
                DETEKT_BASELINE_ANALYZED_FILES_KEY to analyzedFiles,
            )
        )
    )
}
