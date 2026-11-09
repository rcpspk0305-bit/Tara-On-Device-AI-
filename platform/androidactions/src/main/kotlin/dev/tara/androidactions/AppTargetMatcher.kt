package dev.tara.androidactions

import dev.tara.domain.TargetOption

internal fun selectAppTargets(installed: List<TargetOption>, query: String): List<TargetOption> {
    if (query.isBlank()) return emptyList()
    val exact = installed.filter { it.label.equals(query, ignoreCase = true) || it.id.equals(query, ignoreCase = true) }
    return (exact.ifEmpty { installed.filter { it.label.contains(query, ignoreCase = true) } })
        .distinctBy { it.id }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
