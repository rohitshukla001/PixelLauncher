package dev.rohitshukla.pixellauncher.data

import java.text.Normalizer

/** Drawer search: matches labels ignoring case and accents. Labels that start with the query come first. */
object AppSearch {

    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return apps
        val (prefix, contains) = apps
            .map { it to normalize(it.label) }
            .filter { (_, label) -> label.contains(q) }
            .partition { (_, label) -> label.startsWith(q) || label.split(' ').any { it.startsWith(q) } }
        return (prefix + contains).map { it.first }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
