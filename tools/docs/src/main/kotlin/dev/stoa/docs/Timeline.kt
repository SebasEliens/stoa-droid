package dev.stoa.docs

import java.io.File

/** Resolves each item's status and checks every item; all problems are reported at once (S002-3). */
fun resolveTimeline(
    root: File,
    items: List<TimelineItem>,
    specs: List<Spec>,
): List<Checkpoint> {
    val duplicates = items.groupBy { it.id }.filter { it.value.size > 1 }.keys
    val errors = duplicates.map { "id '$it' is used more than once" }.toMutableList()
    val checkpoints =
        items.mapNotNull { item ->
            val result = runCatching { checkpointOf(root, item, specs) }
            result.exceptionOrNull()?.let { if (it is DocsError) errors += it.message.orEmpty() else throw it }
            result.getOrNull()
        }
    if (errors.isNotEmpty()) throw DocsError(errors.joinToString("\n") { "docs/timeline.yaml $it" })
    return checkpoints
}

private fun checkpointOf(
    root: File,
    item: TimelineItem,
    specs: List<Spec>,
): Checkpoint {
    fun fail(problem: String): Nothing = throw DocsError("item '${item.id}': $problem")
    item.decided.firstOrNull { it.area !in AREAS }?.let {
        fail(
            "area '${it.area}' is not one of ${AREAS.joinToString()}",
        )
    }
    val spec =
        item.spec?.let { n ->
            specs.firstOrNull { it.number == n }
                ?: fail("spec $n does not exist (no specs/$n-*.md)")
        }
    val plan = item.plan?.let { File(root, it).takeIf(File::isFile) ?: fail("plan $it does not exist") }
    return when {
        spec != null -> Checkpoint(item, spec.status, "From <a href=\"#spec-${spec.number}\">spec ${spec.number}</a>.")
        plan != null -> planCheckpoint(item, plan.readText())
        item.status in TIMELINE_STATUSES -> Checkpoint(item, item.status.orEmpty(), "No spec yet.")
        else -> fail("without a spec or plan, status must be one of ${TIMELINE_STATUSES.joinToString()}")
    }
}

/** A plan's status comes from its own `Status:` line, as a spec's does (D002-2). */
private fun planCheckpoint(
    item: TimelineItem,
    text: String,
): Checkpoint {
    val path = item.plan.orEmpty()
    val line =
        Regex("""^Status:\s*(.*)$""", RegexOption.MULTILINE)
            .find(text)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    return Checkpoint(item, statusOf(path, text), "Plan <code>${esc(path)}</code>: ${inline(line)}")
}
