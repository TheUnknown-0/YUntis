package sh.lsx.untisx.data

import kotlinx.serialization.Serializable

@Serializable
data class SubjectAlias(
    val short: String? = null,
    val long: String? = null,
)

fun subjectShortLabel(aliases: Map<String, SubjectAlias>, name: String): String =
    aliases[name]?.short?.takeIf { it.isNotBlank() }
        ?: aliases[name]?.long?.takeIf { it.isNotBlank() }
        ?: name

fun subjectLongLabel(aliases: Map<String, SubjectAlias>, name: String): String =
    aliases[name]?.long?.takeIf { it.isNotBlank() }
        ?: aliases[name]?.short?.takeIf { it.isNotBlank() }
        ?: name
