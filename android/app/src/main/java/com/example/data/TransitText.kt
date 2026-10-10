package com.example.data

import java.util.Locale

/** Numeric-first ordering for public route numbers: 1А, 1Б, 2, …, 31, 31Э. */
fun compareRouteNumbers(left: String, right: String): Int {
    val a = routeNumberKeyParts(left)
    val b = routeNumberKeyParts(right)
    return compareValuesBy(a, b, RouteNumberKey::number, RouteNumberKey::suffix, RouteNumberKey::fallback)
}

val naturalRouteComparator: Comparator<Route> = Comparator { left, right ->
    compareRouteNumbers(left.number, right.number)
        .takeIf { it != 0 }
        ?: compareValuesBy(
            left,
            right,
            { normalizeTransitSearch(it.origin) },
            { normalizeTransitSearch(it.destination) },
            Route::id
        )
}

/**
 * Search normalization shared by the route sheet and planner.
 * It is deliberately locale-stable, treats ё/е equally, folds punctuation and
 * understands Latin suffixes often entered for Cyrillic route numbers (5A, 31E).
 */
fun normalizeTransitSearch(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace('ё', 'е')
    .replace(Regex("[^0-9a-zа-я]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")
    .split(' ')
    .joinToString(" ") { normalizeRouteToken(it) }

fun matchesTransitSearch(query: String, vararg values: String): Boolean {
    val normalizedQuery = normalizeTransitSearch(query)
    if (normalizedQuery.isBlank()) return true
    val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
    val haystack = values.joinToString(" ") { normalizeTransitSearch(it) }
    return tokens.all(haystack::contains)
}

private data class RouteNumberKey(val number: Int, val suffix: String, val fallback: String)

private fun routeNumberKeyParts(value: String): RouteNumberKey {
    val normalized = normalizeTransitSearch(value).replace(" ", "")
    val match = Regex("^(\\d+)(.*)$").matchEntire(normalized)
    return if (match == null) {
        RouteNumberKey(Int.MAX_VALUE, normalized, normalized)
    } else {
        RouteNumberKey(
            number = match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE,
            suffix = match.groupValues[2],
            fallback = normalized
        )
    }
}

private fun normalizeRouteToken(token: String): String {
    val match = Regex("^(\\d+)([a-z])$").matchEntire(token) ?: return token
    val suffix = when (match.groupValues[2]) {
        "a" -> "а"
        "b" -> "б"
        "e" -> "э"
        "i" -> "и"
        "k" -> "к"
        else -> match.groupValues[2]
    }
    return match.groupValues[1] + suffix
}
