package dev.stoa.droid

/** Reads `app/permissions.yaml` and compares it with the permissions the manifest requests (S001-6). */
object PermissionAllowlist {
    const val INTERNET = "android.permission.INTERNET"

    data class Entry(
        val name: String,
        val library: String,
        val reason: String,
    )

    /**
     * A hand parser for the file's one fixed shape: a `permissions:` key, then per entry `- name:`, `library:` and
     * `reason:` lines with plain values. Anything else fails, so the format cannot drift from what is read here.
     */
    fun parse(yaml: String): List<Entry> {
        val entries = mutableListOf<MutableMap<String, String>>()
        yaml.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line == "permissions:") return@forEachIndexed
            val isNewEntry = line.startsWith("- ")
            val (key, value) =
                line.removePrefix("- ").split(": ", limit = 2).takeIf { it.size == 2 }
                    ?: error("permissions.yaml line ${index + 1}: expected `key: value`, got `$raw`")
            require(
                key in setOf("name", "library", "reason"),
            ) { "permissions.yaml line ${index + 1}: unknown key `$key`" }
            if (isNewEntry) entries += mutableMapOf()
            val entry =
                entries.lastOrNull() ?: error("permissions.yaml line ${index + 1}: `$key` before the first `- name:`")
            require(entry.put(key, value.trim()) == null) { "permissions.yaml line ${index + 1}: `$key` given twice" }
        }
        return entries.map { Entry(it["name"].orEmpty(), it["library"].orEmpty(), it["reason"].orEmpty()) }
    }

    /** Every way the requested permissions differ from the approved list; empty when they match. */
    fun violations(
        requested: Collection<String>,
        approved: Collection<String>,
    ): List<String> =
        listOfNotNull(INTERNET.takeIf { it in requested || it in approved }?.let { "never allowed: $it" }) +
            (requested - approved.toSet() - INTERNET).sorted().map {
                "requested but not approved in app/permissions.yaml: $it"
            } +
            (approved - requested.toSet() - INTERNET).sorted().map {
                "approved in app/permissions.yaml but not requested: $it"
            }
}
