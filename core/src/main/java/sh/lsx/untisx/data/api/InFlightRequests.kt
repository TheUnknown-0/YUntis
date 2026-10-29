package sh.lsx.untisx.data.api

import kotlinx.coroutines.CompletableDeferred

internal object InFlightRequests {
    private val pending = HashMap<List<Any>, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> read(key: List<Any>, request: suspend () -> T): T {
        val (result, owner) = synchronized(pending) {
            pending[key]?.let { it to false } ?: CompletableDeferred<Any?>().let {
                pending[key] = it
                it to true
            }
        }

        if (!owner) return result.await() as T

        try {
            return request().also { result.complete(it) }
        } catch (e: Throwable) {
            result.completeExceptionally(e)
            throw e
        } finally {
            synchronized(pending) { pending.remove(key) }
        }
    }
}
