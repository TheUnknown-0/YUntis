package sh.lsx.untisx.data.prefs

import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult
import java.util.Base64

class FakeCryptor : Cryptor {

    @Volatile
    var decryptThread: String? = null

    override fun encrypt(plain: String): String =
        PREFIX + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))

    override fun decrypt(encoded: String): DecryptResult {
        decryptThread = Thread.currentThread().name

        return when {
            encoded == TRANSIENT -> DecryptResult.Transient
            encoded == KEY_LOST -> DecryptResult.KeyLost
            encoded.startsWith(PREFIX) -> DecryptResult.Value(
                String(Base64.getDecoder().decode(encoded.removePrefix(PREFIX)), Charsets.UTF_8),
            )

            else -> DecryptResult.Missing
        }
    }

    companion object {
        const val PREFIX = "fake:"
        const val TRANSIENT = "fake-transient"
        const val KEY_LOST = "fake-keylost"
    }
}
