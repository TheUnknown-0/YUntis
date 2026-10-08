package sh.lsx.untisx.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HtmlTest {

    @Suppress("unused")
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `entities are decoded exactly once`() {
        assertEquals("&lt;b&gt;", stripHtml("&amp;lt;b&amp;gt;"))
    }

    @Test
    fun `named entities are decoded`() {
        assertEquals("äöü", stripHtml("&auml;&ouml;&uuml;"))
    }

    @Test
    fun `line breaks become newlines`() {
        assertEquals("a\nb", stripHtml("a<br>b"))
        assertEquals("a\nb", stripHtml("a<br/>b"))
    }

    @Test
    fun `tags are removed`() {
        assertEquals("hello world", stripHtml("<p>hello <b>world</b></p>"))
    }

    @Test
    fun `excess newlines collapse to a single blank line`() {
        assertEquals("a\n\nb", stripHtml("a<br><br><br><br>b"))
    }

    @Test
    fun `trailing spaces before a line break are removed`() {
        assertEquals("a\nb", stripHtml("a  <br>b"))
    }

    @Test
    fun `result is trimmed`() {
        assertEquals("hello", stripHtml("  hello  "))
    }
}
