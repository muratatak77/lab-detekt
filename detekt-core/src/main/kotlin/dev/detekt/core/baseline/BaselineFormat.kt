package dev.detekt.core.baseline

import dev.detekt.tooling.api.Baseline
import dev.detekt.tooling.api.BaselineProvider
import dev.detekt.tooling.api.FindingsIdList
import org.xml.sax.SAXParseException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.name

internal class BaselineFormat : BaselineProvider {

    private val XMLStreamException.positions
        get() = location.lineNumber to location.columnNumber

    class InvalidState(msg: String, error: Throwable) : IllegalStateException(msg, error)

    override fun of(manuallySuppressedIssues: FindingsIdList, currentIssues: FindingsIdList): DefaultBaseline =
        DefaultBaseline(manuallySuppressedIssues, currentIssues)

    override fun read(sourcePath: Path): DefaultBaseline {
        try {
            sourcePath.inputStream().use {
                val reader = SAXParserFactory.newInstance()
                    .apply {
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    }
                    .newSAXParser()
                val handler = BaselineHandler()
                reader.parse(it, handler)
                return handler.createBaseline()
            }
        } catch (error: SAXParseException) {
            val (line, column) = error.lineNumber to error.columnNumber
            throw InvalidState("Error on position $line:$column while reading the baseline xml file!", error)
        }
    }

    /**
     * Writes [baseline] to a temporary file next to [targetPath] and then moves it over [targetPath], so a failure
     * while writing leaves an existing baseline untouched. If [targetPath] is a symbolic link, its target is replaced.
     */
    override fun write(targetPath: Path, baseline: Baseline) {
        val target = if (targetPath.exists()) targetPath.toRealPath() else targetPath
        val tempFile = target.resolveSibling(".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            try {
                tempFile.bufferedWriter(Charsets.UTF_8, DEFAULT_BUFFER_SIZE, StandardOpenOption.CREATE_NEW)
                    .addFinalNewLine()
                    .use { it.streamXml().prettyPrinter().save(baseline) }
            } catch (error: XMLStreamException) {
                val (line, column) = error.positions
                throw InvalidState("Error on position $line:$column while writing the baseline xml file!", error)
            }
            tempFile.replace(target)
        } finally {
            tempFile.deleteIfExists()
        }
    }

    private fun Path.replace(target: Path) {
        try {
            moveTo(target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            moveTo(target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun XMLStreamWriter.save(baseline: Baseline) {
        document {
            tag(SMELL_BASELINE) {
                if (baseline.manuallySuppressedIssues.isEmpty()) {
                    writeEmptyElement(MANUALLY_SUPPRESSED_ISSUES)
                } else {
                    tag(MANUALLY_SUPPRESSED_ISSUES) {
                        baseline.manuallySuppressedIssues.forEach { tag(ID, it) }
                    }
                }

                if (baseline.currentIssues.isEmpty()) {
                    writeEmptyElement(CURRENT_ISSUES)
                } else {
                    tag(CURRENT_ISSUES) {
                        baseline.currentIssues.forEach { tag(ID, it) }
                    }
                }
            }
        }
    }
}
