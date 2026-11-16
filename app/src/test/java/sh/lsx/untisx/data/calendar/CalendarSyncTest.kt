package sh.lsx.untisx.data.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarSyncTest {
    private lateinit var context: Context
    private lateinit var provider: DeletionProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("calendar_export_map", Context.MODE_PRIVATE).edit().clear().commit()
        provider = DeletionProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }

    private fun seedMapping() {
        context.getSharedPreferences("calendar_export_map", Context.MODE_PRIVATE).edit()
            .putString("map", """{"acc:lesson":{"eventId":42,"fingerprint":"old","startMillis":0,"calendarId":1}}""")
            .commit()
    }

    private fun orphans() = context.getSharedPreferences("calendar_export_map", Context.MODE_PRIVATE)
        .getStringSet("orphans", emptySet()).orEmpty()

    @Test
    fun `failed removal is retried by sync after its mapping was dropped`() {
        seedMapping()
        provider.fail = true
        CalendarSync.removeAll(context, "acc")
        assertEquals(setOf("42"), orphans())
        provider.fail = false
        CalendarSync.sync(context, "acc", 1, emptyList(), LocalDate.of(2026, 11, 9))
        assertEquals(listOf(42L, 42L), provider.attempts)
        assertTrue(orphans().isEmpty())
    }

    @Test
    fun `zero deleted rows keep an existing event pending but forget a missing event`() {
        seedMapping()
        provider.zeroRows = true
        CalendarSync.removeAll(context, "acc")
        assertEquals(setOf("42"), orphans())
        provider.exists = false
        CalendarSync.removeAll(context)
        assertTrue(orphans().isEmpty())
        assertEquals(listOf(42L, 42L), provider.attempts)
    }

    class DeletionProvider : ContentProvider() {
        var fail = false
        var zeroRows = false
        var exists = true
        val attempts = mutableListOf<Long>()

        override fun onCreate() = true
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            attempts += uri.lastPathSegment!!.toLong()
            if (fail) throw IllegalStateException("provider unavailable")
            if (zeroRows) return 0
            exists = false
            return 1
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DELETED)).apply {
                if (exists) addRow(arrayOf(42L, 0))
            }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
