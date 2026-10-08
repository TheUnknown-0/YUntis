package sh.lsx.untisx.data.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface DecryptResult {
    data class Value(val plaintext: String) : DecryptResult

    data object Missing : DecryptResult
    data object KeyLost : DecryptResult
    data object Transient : DecryptResult
}

interface Cryptor {
    fun encrypt(plain: String): String

    fun decrypt(encoded: String): DecryptResult
}

object Crypto : Cryptor {

    private const val KEY_ALIAS = "untisx_master"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )

        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())

        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): DecryptResult {
        if (encoded.isBlank()) return DecryptResult.Missing

        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)

            if (data.size <= IV_SIZE + 16) return DecryptResult.KeyLost

            val cipher = Cipher.getInstance(TRANSFORMATION)

            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, data, 0, IV_SIZE))
            DecryptResult.Value(String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8))
        } catch (e: KeyPermanentlyInvalidatedException) {
            DecryptResult.KeyLost
        } catch (e: AEADBadTagException) {
            DecryptResult.KeyLost
        } catch (e: InvalidKeyException) {
            DecryptResult.KeyLost
        } catch (e: IllegalArgumentException) {
            DecryptResult.KeyLost
        } catch (e: GeneralSecurityException) {
            DecryptResult.Transient
        } catch (e: Exception) {
            DecryptResult.Transient
        }
    }
}
