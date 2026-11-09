package sh.lsx.untisx.ui.absences

import android.app.Application
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.Absence
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.PasswordVault
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class AbsencesViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object Loading : State
        data class Loaded(val absences: List<Absence>) : State
        data object NotShared : State
        data object Failed : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    fun load() {
        state = State.Loading
        viewModelScope.launch {
            state = try {
                AccountStore.ensureLoaded(getApplication())
                val auth = AccountStore.active ?: throw UntisException.BadCredentials
                val id = AccountPrefs.accountId(auth)
                val password = withContext(Dispatchers.IO) { PasswordVault.retrieve(getApplication(), id) }
                    ?: throw UntisException.BadCredentials
                val today = AppClock.today()
                val yearStart = StatsStore.fallbackYear(today).startDate.toUntisLocalDateOrNull() ?: today.minusMonths(6)
                State.Loaded(UntisClient.fetchAbsences(auth, password, yearStart, today))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UntisException.Api) {
                Log.d("Absences", "not shared: ${e.code}")
                State.NotShared
            } catch (e: Exception) {
                Log.d("Absences", "failed: ${e.javaClass.simpleName}")
                State.Failed
            }
        }
    }
}

private val ExcusedColor = Color(0xFF43A047)
private val UnexcusedColor = Color(0xFFE53935)

@Composable
fun AbsencesScreen(modifier: Modifier = Modifier, vm: AbsencesViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }

    when (val state = vm.state) {
        AbsencesViewModel.State.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        AbsencesViewModel.State.NotShared -> Message(modifier, stringResource(R.string.absences_not_shared), vm::load)
        AbsencesViewModel.State.Failed -> Message(modifier, stringResource(R.string.absences_failed), vm::load)
        is AbsencesViewModel.State.Loaded -> AbsenceList(state.absences, modifier)
    }
}

@Composable
private fun Message(modifier: Modifier, text: String, onRetry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun AbsenceList(absences: List<Absence>, modifier: Modifier) {
    val excused = absences.filter { it.excused }
    val unexcused = absences.filterNot { it.excused }
    val byMonth = absences.sortedByDescending { it.date * 10000L + it.startTime }
        .groupBy { it.date.toUntisLocalDateOrNull()?.let(YearMonth::from) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(stringResource(R.string.absences_total), absences.size, hours(absences), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Tile(stringResource(R.string.absences_excused), excused.size, hours(excused), ExcusedColor, Modifier.weight(1f))
                Tile(stringResource(R.string.absences_unexcused), unexcused.size, hours(unexcused), UnexcusedColor, Modifier.weight(1f))
            }
        }

        if (absences.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.absences_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        byMonth.forEach { (month, list) ->
            item {
                Text(
                    text = month?.format(DateTimeFormatter.ofPattern("LLLL yyyy")).orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(list) { AbsenceRow(it) }
        }
    }
}

private fun hours(list: List<Absence>): Int = list.sumOf { it.minutes } / 60

@Composable
private fun Tile(label: String, count: Int, hours: Int, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.absences_hours, hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AbsenceRow(absence: Absence) {
    val date = absence.date.toUntisLocalDateOrNull()?.format(DateTimeFormatter.ofPattern("EEE d.M.")).orEmpty()
    val time = "%02d:%02d – %02d:%02d".format(absence.startTime / 100, absence.startTime % 100, absence.endTime / 100, absence.endTime % 100)
    val color = if (absence.excused) ExcusedColor else UnexcusedColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$date · $time", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val details = listOf(absence.reason, absence.text).filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = absence.excuseStatus.ifBlank {
                stringResource(if (absence.excused) R.string.absences_excused_one else R.string.absences_unexcused_one)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.background(color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
