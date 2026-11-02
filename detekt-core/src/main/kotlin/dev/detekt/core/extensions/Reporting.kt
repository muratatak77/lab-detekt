package dev.detekt.core.extensions

import dev.detekt.api.Detektion
import dev.detekt.api.Issue
import dev.detekt.api.ReportingExtension
import dev.detekt.core.ProcessingSettings

fun handleReportingExtensions(settings: ProcessingSettings, initialResult: Detektion): Detektion {
    val extensions = loadExtensions<ReportingExtension>(settings)
    extensions.forEach { it.onRawResult(initialResult) }
    val finalIssues = extensions.fold(initialResult.issues) { acc, extension -> extension.transformIssues(acc) }
    val finalUserData = extensions.fold(initialResult.userData) { acc, extension -> acc + extension.userData() }
    val finalResult = initialResult.copy(issues = finalIssues.sortedWith(issueComparator), userData = finalUserData)
    extensions.forEach { it.onFinalResult(finalResult) }
    return finalResult
}

private val issueComparator = compareBy<Issue>(
    { it.location.path },
    { it.location.source },
    { it.location.endSource },
    { it.ruleInstance.id },
    { it.message },
)

private fun Detektion.copy(issues: List<Issue>, userData: Map<String, Any>): Detektion =
    Detektion(
        issues = issues,
        rules = rules,
        metrics = metrics,
        notifications = notifications,
        userData = userData,
    )
