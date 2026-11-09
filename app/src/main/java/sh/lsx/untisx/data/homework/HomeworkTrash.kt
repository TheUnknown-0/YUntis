package sh.lsx.untisx.data.homework

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.notebook.NotebookItem

/** A homework in the trash and when it went there. */
@Serializable
data class TrashedHomework(val item: NotebookItem, val trashedAt: Long) {
    /** When it is gone for good. */
    fun expiresAt(): Long = trashedAt + HomeworkTrash.KEEP_MILLIS
}

/**
 * Completed homework the user cleared away. It stays restorable for two days, in case of a
 * wrong tap, and is then deleted. Kept apart from the notebook, so reminders, calendar export
 * and widgets never see it.
 */
object HomeworkTrash {
    const val KEEP_MILLIS = 2 * 24 * 60 * 60 * 1000L
    private const val PREFS = "homework_trash"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What is in the trash, newest first; expired entries are dropped on the way. */
    @Synchronized
    fun load(context: Context, accountId: String, now: Long = System.currentTimeMillis()): List<TrashedHomework> {
        val all = prefs(context).getString(accountId, null)
            ?.let { runCatching { json.decodeFromString<List<TrashedHomework>>(it) }.getOrNull() }
            .orEmpty()
        val alive = purge(all, now)
        if (alive.size != all.size) save(context, accountId, alive)
        return alive.sortedByDescending { it.trashedAt }
    }

    /** Pure: entries that have not expired yet. */
    fun purge(entries: List<TrashedHomework>, now: Long): List<TrashedHomework> = entries.filter { it.expiresAt() > now }

    @Synchronized
    fun add(context: Context, accountId: String, items: List<NotebookItem>, now: Long = System.currentTimeMillis()) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }.toSet()
        val kept = load(context, accountId, now).filterNot { it.item.id in ids }
        save(context, accountId, kept + items.map { TrashedHomework(it, now) })
    }

    @Synchronized
    fun remove(context: Context, accountId: String, ids: Set<String>) {
        save(context, accountId, load(context, accountId).filterNot { it.item.id in ids })
    }

    private fun save(context: Context, accountId: String, list: List<TrashedHomework>) {
        prefs(context).edit().apply {
            if (list.isEmpty()) remove(accountId) else putString(accountId, json.encodeToString(list))
        }.apply()
    }
}
