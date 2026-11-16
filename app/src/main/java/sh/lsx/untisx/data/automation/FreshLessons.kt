package sh.lsx.untisx.data.automation

import sh.lsx.untisx.data.model.TimetableEntry

/** Lessons returned for a successfully fetched, inclusive date range (Untis dates). */
data class FreshLessons(val entries: List<TimetableEntry>, val from: Int, val to: Int) {
    init {
        require(from <= to)
    }

    fun mergeWith(cached: List<TimetableEntry>): List<TimetableEntry> =
        entries + cached.filter { it.date !in from..to }
}
