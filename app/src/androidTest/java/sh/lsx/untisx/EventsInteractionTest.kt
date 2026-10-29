package sh.lsx.untisx

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.ui.screens.EventsScreen
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EventsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingExamRequestsItsTimetableDate() {
        val date = LocalDate.now().plusDays(2).toUntisDate()
        var requestedDate = 0

        compose.setContent {
            MaterialTheme {
                EventsScreen(
                    events = emptyList(), holidays = emptyList(), messages = emptyList(), roomsById = emptyMap(),
                    loading = false, onRefresh = {}, onExamClick = { requestedDate = it },
                    exams = listOf(ExamInfo(id = 99, date = date, name = "Device exam")),
                )
            }
        }

        compose.onNodeWithText("Device exam", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(date, requestedDate) }
    }
}
