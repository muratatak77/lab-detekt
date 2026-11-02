package dev.detekt.core.tooling

import dev.detekt.core.ProcessingSettings
import dev.detekt.core.baseline.DETEKT_BASELINE_ANALYZED_FILES_KEY
import dev.detekt.test.utils.NullPrintStream
import dev.detekt.test.utils.resourceAsPath
import dev.detekt.tooling.api.spec.ProcessingSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LifecycleSpec {

    @Test
    fun `does not compute the analyzed files per rule without a baseline`() {
        val settings = analyze(baselinePath = null)

        assertThat(settings.properties).doesNotContainKey(DETEKT_BASELINE_ANALYZED_FILES_KEY)
    }

    @Test
    fun `does not compute the analyzed files per rule while creating a baseline`(@TempDir dir: Path) {
        val settings = analyze(baselinePath = dir.resolve("baseline.xml"), createBaseline = true)

        assertThat(settings.properties).doesNotContainKey(DETEKT_BASELINE_ANALYZED_FILES_KEY)
    }

    @Test
    fun `computes the analyzed files per rule with a baseline`(@TempDir dir: Path) {
        val settings = analyze(baselinePath = dir.resolve("baseline.xml"))

        assertThat(settings.properties).containsKey(DETEKT_BASELINE_ANALYZED_FILES_KEY)
    }

    private fun analyze(baselinePath: Path?, createBaseline: Boolean = false): ProcessingSettings {
        val spec = ProcessingSpec {
            project {
                inputPaths = listOf(resourceAsPath("cases/Test.kt"))
            }
            config {
                shouldValidateBeforeAnalysis = false
            }
            baseline {
                path = baselinePath
                shouldCreateDuringAnalysis = createBaseline
            }
            logging {
                outputChannel = NullPrintStream()
                errorChannel = NullPrintStream()
            }
        }
        lateinit var captured: ProcessingSettings
        val result = AnalysisFacade(spec).runAnalysis { settings ->
            captured = settings
            Lifecycle(
                settings.config,
                settings,
                processorsProvider = { emptyList() },
                ruleSetsProvider = { emptyList() },
            )
        }
        assertThat(result.error?.cause).isNull()
        return captured
    }
}
