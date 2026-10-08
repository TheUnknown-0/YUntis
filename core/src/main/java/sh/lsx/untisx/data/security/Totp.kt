package sh.lsx.untisx.data.security

import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val TOTP_PERIOD_MILLIS = 30_000L

internal fun generateTotp(base32Secret: String, nowMillis: Long): Long? {
    val key = decodeBase32(base32Secret) ?: return null
    if (key.isEmpty()) return null

    val counterBytes = ByteArray(8)
    var counter = nowMillis / TOTP_PERIOD_MILLIS
    for (i in 7 downTo 0) {
        counterBytes[i] = (counter and 0xFF).toByte()
        counter = counter ushr 8
    }

    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(counterBytes)

    val offset = hash[19].toInt() and 0xF
    var value = 0L
    for (i in 0 until 4) {
        value = (value shl 8) or (hash[offset + i].toInt() and 0xFF).toLong()
    }

    return (value and 0x7FFFFFFFL) % 1_000_000L
}

internal fun decodeBase32(encoded: String): ByteArray? {
    val clean = encoded.replace(" ", "").replace("=", "").uppercase(Locale.ROOT)
    if (clean.isEmpty()) return null

    var buffer = 0
    var bits = 0
    val out = ArrayList<Byte>(clean.length * 5 / 8)

    for (c in clean) {
        val index = BASE32_ALPHABET.indexOf(c)
        if (index < 0) return null

        buffer = (buffer shl 5) or index
        bits += 5

        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xFF).toByte())
        }
    }

    return out.toByteArray()
}
