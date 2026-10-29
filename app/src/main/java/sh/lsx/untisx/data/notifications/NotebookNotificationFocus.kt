package sh.lsx.untisx.data.notifications

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object NotebookNotificationFocus {

    const val EXTRA_FOCUS_TOKEN = "sh.lsx.untisx.extra.NOTEBOOK_FOCUS_TOKEN"

    internal const val PREFS_NAME = "notebook_notification_focus"
    internal const val TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
    internal const val MAX_ENTRIES = 256

    private const val TOKEN_BYTES = 32
    private const val MAX_TOKEN_LENGTH = 256
    private const val MAX_ID_LENGTH = 512

    private val lock = Any()
    private val random = SecureRandom()

    internal var clock: () -> Long = { System.currentTimeMillis() }

    fun issue(context: Context, accountId: String, itemId: String): String {
        require(accountId.isNotBlank() && accountId.length <= MAX_ID_LENGTH)
        require(itemId.isNotBlank() && itemId.length <= MAX_ID_LENGTH)

        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val tokenHash = sha256(token)
        val accountHash = sha256(accountId)
        val itemHash = sha256(itemId)

        synchronized(lock) {
            val now = clock()
            val expiresAt = now + TTL_MILLIS
            val prefs = prefs(context)
            val editor = prefs.edit()
            val valid = ArrayList<Pair<String, Long>>()

            prefs.all.forEach { (key, value) ->
                val binding = parse(value as? String)

                if (binding == null || binding.expiresAt <= now) {
                    editor.remove(key)
                } else {
                    valid += key to binding.expiresAt
                }
            }

            val overflow = valid.size - MAX_ENTRIES + 1
            if (overflow > 0) {
                valid.sortedBy { it.second }
                    .take(overflow)
                    .forEach { editor.remove(it.first) }
            }

            editor.putString(tokenHash, "$accountHash|$itemHash|$expiresAt")

            check(editor.commit()) { "Could not persist notebook focus token" }
        }

        return token
    }

    fun consume(
        context: Context,
        token: String?,
        accountId: String?,
        itemId: String?,
    ): Boolean {
        if (token.isNullOrBlank() || token.length > MAX_TOKEN_LENGTH) return false
        if (accountId.isNullOrBlank() || accountId.length > MAX_ID_LENGTH) return false
        if (itemId.isNullOrBlank() || itemId.length > MAX_ID_LENGTH) return false

        val tokenHash = sha256(token)
        val accountHash = sha256(accountId)
        val itemHash = sha256(itemId)

        synchronized(lock) {
            val prefs = prefs(context)
            val stored = prefs.all[tokenHash] as? String ?: return false
            val binding = parse(stored)
            val now = clock()

            if (binding == null || binding.expiresAt <= now) {
                prefs.edit().remove(tokenHash).commit()
                return false
            }

            if (binding.accountHash != accountHash || binding.itemHash != itemHash) {
                return false
            }

            return prefs.edit().remove(tokenHash).commit()
        }
    }

    internal fun resetForTests() {
        clock = { System.currentTimeMillis() }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class Binding(
        val accountHash: String,
        val itemHash: String,
        val expiresAt: Long,
    )

    private fun parse(value: String?): Binding? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 3) return null

        val expiresAt = parts[2].toLongOrNull() ?: return null

        return Binding(
            accountHash = parts[0],
            itemHash = parts[1],
            expiresAt = expiresAt,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
