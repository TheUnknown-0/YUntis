package sh.lsx.untisx.data

import java.time.LocalDate
import java.time.ZonedDateTime

object AppClock {

    @Volatile
    private var todayProvider: () -> LocalDate = { LocalDate.now() }

    @Volatile
    private var nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() }

    fun today(): LocalDate = todayProvider()

    fun now(): ZonedDateTime = nowProvider()

    internal fun set(
        today: (() -> LocalDate)? = null,
        now: (() -> ZonedDateTime)? = null,
    ) {
        if (today != null) todayProvider = today
        if (now != null) nowProvider = now
    }

    internal fun reset() {
        todayProvider = { LocalDate.now() }
        nowProvider = { ZonedDateTime.now() }
    }
}
