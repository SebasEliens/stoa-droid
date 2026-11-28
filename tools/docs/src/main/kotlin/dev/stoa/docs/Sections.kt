package dev.stoa.docs

/** A timeline item with its status resolved: from its spec, else its plan, else the YAML (D002-2). */
data class Checkpoint(
    val item: TimelineItem,
    val status: String,
    val source: String,
)

class Section(
    val id: String,
    val title: String,
    val html: String,
)

private fun list(items: String) = if (items.isEmpty()) "<p class=\"muted\">None yet.</p>" else "<ul>$items</ul>"

private fun badge(status: String) = "<span class=\"badge\">${statusLabel(status)}</span>"

private fun area(name: String) = "<span class=\"area\">${esc(name)}</span>"

fun timelineSection(checkpoints: List<Checkpoint>): Section {
    val items =
        checkpoints.joinToString("\n") { (item, status, source) ->
            val decided = item.decided.joinToString("") { "<li>${inline(it.text)} ${area(it.area)}</li>" }
            val open = item.open.joinToString("") { "<li>${inline(it)}</li>" }
            "<article class=\"item\" id=\"t-${esc(item.id)}\" data-status=\"${esc(status)}\">" +
                "<h3>${inline(item.title)} ${badge(status)}</h3><p class=\"muted\">$source</p>" +
                (if (decided.isEmpty()) "" else "<h4>Decided</h4><ul>$decided</ul>") +
                (if (open.isEmpty()) "" else "<h4>Open questions</h4><ul>$open</ul>") +
                "</article>"
        }
    val intro = "<p>Design checkpoints, in order. When a checkpoint has a spec, the spec's status is shown.</p>"
    return Section("timeline", "Timeline", "$intro\n$items")
}

private val DECISION_HEAD = listOf("ID", "Area", "Question", "Options and trade-offs", "Choice and why", "Verified by")

private fun decisionRow(d: Decision) =
    "<tr id=\"${esc(d.id)}\"><th scope=\"row\">${esc(d.id)}</th><td>${esc(d.area)}</td>" +
        "<td>${inline(d.question)}</td><td>${inline(d.options)}</td><td>${inline(d.choice)}</td>" +
        "<td>${inline(d.verifiedBy)}</td></tr>"

fun designSection(specs: List<Spec>): Section {
    val body =
        specs.joinToString("\n") { spec ->
            val decisions =
                if (spec.decisions.isEmpty()) {
                    "<p class=\"muted\">No decisions yet.</p>"
                } else {
                    table("Decisions of spec ${spec.number}", DECISION_HEAD, spec.decisions.map(::decisionRow))
                }
            "<h3 id=\"spec-${spec.number}\">${spec.number} ${inline(spec.title)} ${badge(spec.status)}</h3>\n" +
                "<p class=\"muted\">From <code>specs/${esc(spec.file)}</code>.</p>\n${blocks(spec.goal)}\n$decisions"
        }
    val intro = "<p>Every decision from every spec, with the check behind it.</p>"
    return Section("design", "Technical design", "$intro\n$body")
}

fun privacySection(
    specs: List<Spec>,
    checkpoints: List<Checkpoint>,
    permissions: List<Permission>,
): Section {
    val fromSpecs =
        specs.flatMap { s -> s.decisions.filter { it.area == "privacy" }.map { s to it } }.joinToString("") { (s, d) ->
            val id = esc(d.id)
            "<li><a href=\"#$id\">$id</a> (<a href=\"#spec-${s.number}\">spec ${s.number}</a>): " +
                "${inline(d.question)} ${inline(d.choice)}</li>"
        }
    val fromTimeline =
        checkpoints
            .flatMap { c ->
                c.item.decided
                    .filter { it.area == "privacy" }
                    .map { c.item to it }
            }.joinToString("") {
                "<li><a href=\"#t-${esc(it.first.id)}\">${inline(it.first.title)}</a>: ${inline(it.second.text)}</li>"
            }
    val rows =
        permissions.map {
            "<tr><th scope=\"row\"><code>${esc(it.name)}</code></th>" +
                "<td>${esc(it.library)}</td><td>${inline(it.reason)}</td></tr>"
        }
    val html =
        "<h3>Decisions in the specs</h3>" + list(fromSpecs) +
            "<h3>Decisions on the timeline</h3>" + list(fromTimeline) +
            "<h3>Permissions</h3><p>The approved list in <code>app/permissions.yaml</code>. A test fails the build " +
            "when the app asks for any permission not on it, and INTERNET may never be added.</p>" +
            table("Approved permissions", listOf("Permission", "Added by", "Why it is accepted"), rows)
    return Section("privacy", "Privacy", html)
}
