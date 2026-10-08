package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.CorePrefs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CredentialsPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `markInvalid reports the first mark only`() {
        assertFalse(CredentialsPrefs.isInvalid(context, "a"))
        assertTrue(CredentialsPrefs.markInvalid(context, "a"))
        assertTrue(CredentialsPrefs.isInvalid(context, "a"))
        assertFalse(CredentialsPrefs.markInvalid(context, "a"))
    }

    @Test
    fun `invalid state is per account and can be cleared`() {
        CredentialsPrefs.markInvalid(context, "a")

        assertFalse(CredentialsPrefs.isInvalid(context, "b"))

        CredentialsPrefs.clear(context, "a")

        assertFalse(CredentialsPrefs.isInvalid(context, "a"))
    }
}
