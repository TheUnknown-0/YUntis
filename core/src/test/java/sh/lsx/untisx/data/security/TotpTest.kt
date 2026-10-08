package sh.lsx.untisx.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {

    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun rfc6238Sha1VectorAt59Seconds() {
        assertEquals(287082L, generateTotp(rfcSecret, 59_000L))
    }

    @Test
    fun rfc6238Sha1VectorAt1234567890Seconds() {
        assertEquals(5_924L, generateTotp(rfcSecret, 1_234_567_890_000L))
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(
            generateTotp(rfcSecret, 1_234_567_890_000L),
            generateTotp(rfcSecret, 1_234_567_890_000L),
        )
    }

    @Test
    fun secretWithSpacesAndLowercaseIsAccepted() {
        val secret = "gezdgnbvgy3tqojq gezdgnbvgy3tqojq"

        assertEquals(287082L, generateTotp(secret, 59_000L))
    }

    @Test
    fun invalidCharactersYieldNull() {
        assertNull(generateTotp("GEZDGNBV1!", 59_000L))
    }

    @Test
    fun emptyOrTooShortSecretYieldsNull() {
        assertNull(generateTotp("", 59_000L))
        assertNull(generateTotp("A", 59_000L))
    }

    @Test
    fun codesRollOverAtTheNextTimeStep() {
        assertNotEquals(generateTotp(rfcSecret, 29_999L), generateTotp(rfcSecret, 30_000L))
    }

    @Test
    fun paddingCharactersAreIgnored() {
        assertEquals(287082L, generateTotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ====", 59_000L))
    }
}
