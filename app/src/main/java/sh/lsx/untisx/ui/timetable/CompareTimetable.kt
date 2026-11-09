package sh.lsx.untisx.ui.timetable

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData

/** The timetable of [accountId]; the same instance serves the account when it is active and when it is compared. */
@Composable
internal fun timetableViewModel(accountId: String): TimetableViewModel {
    val app = LocalContext.current.applicationContext as Application
    val factory = remember(accountId) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimetableViewModel(app, accountId) as T
        }
    }

    return viewModel(key = accountId, factory = factory)
}

/** A second account shown next to the active one in the day view. */
class CompareSource internal constructor(
    internal val vm: TimetableViewModel,
    internal val ownLabel: String,
    internal val otherLabel: String,
    internal val onLessonClick: (TimetableEntry) -> Unit,
    internal val onExit: () -> Unit,
)

/** Short label for an account in the compare header: the user name, with the school when two schools are mixed. */
internal fun compareLabel(account: AuthData, other: AuthData?): String =
    if (other != null && other.displayName != account.displayName && account.displayName.isNotBlank()) {
        "${account.user} · ${account.displayName}"
    } else {
        account.user
    }
