package sh.lsx.untisx.data

/**
 * Decides which card and text color a subject gets.
 *
 * Order when the school colors win (default):
 * school color, own color, automatic color, global default.
 *
 * Order when own colors win:
 * own color, school color, automatic color, global default.
 */
object SubjectColorResolver {

    /** Calm, clearly distinguishable card colors that stay readable with dark text. */
    val AUTO_PALETTE: List<Int> = listOf(
        0xFF8EC5FC, 0xFFF6A6A6, 0xFFA8E6A1, 0xFFFFD580,
        0xFFC9B6F2, 0xFF8FDCD5, 0xFFF8B6D8, 0xFFD7E38C,
        0xFFFFB98A, 0xFFA9C4EB, 0xFFB8E0F6, 0xFFE3C3A0,
        0xFFB5EAD7, 0xFFF3C6F0, 0xFFC7D2A4, 0xFFFFE3A3,
    ).map { it.toInt() }

    data class Sources(
        val own: Map<String, Int> = emptyMap(),
        val ownText: Map<String, Int> = emptyMap(),
        val school: Map<String, Int> = emptyMap(),
        val schoolText: Map<String, Int> = emptyMap(),
        val preferSchool: Boolean = true,
        val autoColors: Boolean = true,
    )

    enum class Origin { SCHOOL, OWN, AUTO, DEFAULT }

    /** Parses Untis colors such as "f49f25", "#F49F25" or "#80F49F25". */
    fun parseUntisColor(raw: String?): Int? {
        val hex = raw?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return null
        if (hex.any { it.digitToIntOrNull(16) == null }) return null

        return when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            3 -> {
                val expanded = hex.map { "$it$it" }.joinToString("")
                (0xFF000000L or expanded.toLong(16)).toInt()
            }
            else -> null
        }
    }

    /** Stable automatic colors: every subject keeps its color, collisions move to the next free one. */
    fun autoColors(names: Collection<String>, taken: Collection<Int> = emptyList()): Map<String, Int> {
        val palette = AUTO_PALETTE
        val used = HashSet<Int>(taken)
        val result = LinkedHashMap<String, Int>()

        for (name in names.distinct().sorted()) {
            val start = Math.floorMod(stableHash(name), palette.size)
            var chosen = palette[start]

            for (step in palette.indices) {
                val candidate = palette[(start + step) % palette.size]
                if (candidate !in used) {
                    chosen = candidate
                    break
                }
            }

            used += chosen
            result[name] = chosen
        }

        return result
    }

    fun origin(name: String, sources: Sources): Origin {
        val school = name in sources.school
        val own = name in sources.own

        return when {
            school && (sources.preferSchool || !own) -> Origin.SCHOOL
            own -> Origin.OWN
            sources.autoColors -> Origin.AUTO
            else -> Origin.DEFAULT
        }
    }

    /** Card color for every subject that does not simply fall back to the global default. */
    fun cardColors(names: Collection<String>, sources: Sources): Map<String, Int> {
        val all = (names + sources.own.keys + sources.school.keys).distinct()
        val fixed = HashMap<String, Int>()

        for (name in all) {
            when (origin(name, sources)) {
                Origin.SCHOOL -> fixed[name] = sources.school.getValue(name)
                Origin.OWN -> fixed[name] = sources.own.getValue(name)
                else -> Unit
            }
        }

        if (!sources.autoColors) return fixed

        val auto = autoColors(all.filter { it !in fixed }, taken = fixed.values)

        return fixed + auto
    }

    /** Text colors: the school's text color only applies where the school's card color is shown. */
    fun textColors(names: Collection<String>, sources: Sources): Map<String, Int> {
        val all = (names + sources.ownText.keys + sources.schoolText.keys).distinct()
        val result = HashMap<String, Int>()

        for (name in all) {
            val schoolText = sources.schoolText[name]
            val ownText = sources.ownText[name]

            when {
                origin(name, sources) == Origin.SCHOOL && schoolText != null -> result[name] = schoolText
                ownText != null -> result[name] = ownText
            }
        }

        return result
    }

    private fun stableHash(text: String): Int {
        var hash = 0x811C9DC5.toInt()

        for (char in text.lowercase()) {
            hash = hash xor char.code
            hash *= 0x01000193
        }

        return hash
    }
}
