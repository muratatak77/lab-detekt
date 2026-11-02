package dev.detekt.api

/**
 * Allows to intercept detekt's result container by listening to the initial and final state
 * and manipulate the reported issues.
 */
interface ReportingExtension : Extension {

    /**
     * Is called before any [transformIssues] calls were executed.
     */
    fun onRawResult(result: Detektion) {
        // intercept for the initial results
    }

    /**
     * Allows to transform the reported issues e.g. apply custom filtering.
     */
    fun transformIssues(issues: List<Issue>): List<Issue> = issues

    /**
     * Additional data to publish in [Detektion.userData]. Is called after all extensions's [transformIssues] were
     * called, so that the data can be based on the outcome of the transformation.
     *
     * Unlike [FileProcessListener.onFinish], a reporting extension does not return a whole [Detektion]: detekt builds
     * the final result itself from the transformed issues, so an extension can only add entries here and can't replace
     * the issues, rules or metrics of the result.
     */
    fun userData(): Map<String, Any> = emptyMap()

    /**
     * Is called after all extensions's [transformIssues] were called.
     */
    fun onFinalResult(result: Detektion) {
        // intercept for the final results
    }
}
