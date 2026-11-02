package dev.detekt.tooling.dsl

import dev.detekt.tooling.api.spec.BaselineSpec
import java.nio.file.Path

@ProcessingModelDsl
class BaselineSpecBuilder : Builder<BaselineSpec> {

    var path: Path? = null
    var shouldCreateDuringAnalysis: Boolean = false
    var shouldPruneStaleEntries: Boolean = false

    override fun build(): BaselineSpec = BaselineModel(path, shouldCreateDuringAnalysis, shouldPruneStaleEntries)
}

private data class BaselineModel(
    override val path: Path?,
    override val shouldCreateDuringAnalysis: Boolean,
    override val shouldPruneStaleEntries: Boolean,
) : BaselineSpec
