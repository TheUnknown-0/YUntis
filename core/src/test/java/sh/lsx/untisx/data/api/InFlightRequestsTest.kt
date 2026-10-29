package sh.lsx.untisx.data.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class InFlightRequestsTest {
    @Test fun `concurrent reads share a result but subsequent refresh is fresh`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        suspend fun fetch() = InFlightRequests.read(listOf("test")) { calls++; gate.await(); calls }
        val first = async { fetch() }
        val second = async { fetch() }
        yield()
        gate.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(2, fetch())
    }
}
