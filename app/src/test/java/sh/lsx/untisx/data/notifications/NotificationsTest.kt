package sh.lsx.untisx.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(manager).setNotificationsEnabled(true)
    }

    private fun entry(subjectId: Int, subject: String, roomId: Int, room: String) = TimetableEntry(
        date = 20261005,
        startTime = 800,
        endTime = 845,
        subject = listOf(TimetableElement(id = subjectId, name = subject)),
        room = listOf(TimetableElement(id = roomId, name = room)),
    )

    private fun change(subjectId: Int, subject: String, roomId: Int, room: String) = ScheduleChange(
        type = ChangeType.ROOM,
        entry = entry(subjectId, subject, roomId, room),
        subject = subject,
        previousValue = room,
        newValue = "${room}B",
    )

    @Test
    fun `two changes in the same slot do not overwrite each other`() {
        Notifications.notifyChanges(
            context,
            listOf(
                change(subjectId = 1, subject = "Math", roomId = 10, room = "A101"),
                change(subjectId = 2, subject = "English", roomId = 11, room = "A102"),
            ),
            silent = true,
        )

        assertEquals(2, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `posting the same change twice replaces instead of duplicating`() {
        val change = change(subjectId = 1, subject = "Math", roomId = 10, room = "A101")

        Notifications.notifyChanges(context, listOf(change), silent = true)
        Notifications.notifyChanges(context, listOf(change), silent = true)

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `nothing is posted while notifications are disabled`() {
        shadowOf(manager).setNotificationsEnabled(false)

        Notifications.notifyChanges(context, listOf(change(1, "Math", 10, "A101")), silent = true)

        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `login required notification is posted once`() {
        Notifications.notifyLoginRequired(context)
        Notifications.notifyLoginRequired(context)

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
