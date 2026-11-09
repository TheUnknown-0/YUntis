package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AuthData

class CompareLabelTest {
    private fun account(user: String, school: String) =
        AuthData(server = "s", loginName = "l", displayName = school, user = user, session = AuthResult())

    @Test
    fun `same school shows only the user name`() {
        assertEquals("anna", compareLabel(account("anna", "Gymnasium"), account("ben", "Gymnasium")))
    }

    @Test
    fun `different schools add the school`() {
        assertEquals("anna · Gymnasium", compareLabel(account("anna", "Gymnasium"), account("ben", "Realschule")))
    }

    @Test
    fun `no partner shows only the user name`() {
        assertEquals("anna", compareLabel(account("anna", "Gymnasium"), null))
    }
}
