package me.him188.ani.utils.coroutines

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * Runs the block multiple times, returns when it succeeds the first time. with a delay between each attempt.
 */
suspend inline fun <R, V> V.runUntilSuccess(
    onFailure: (Exception) -> Unit = { it.printStackTrace() },
    block: V.() -> R,
): R {
    contract { callsInPlace(block, InvocationKind.AT_LEAST_ONCE) }
    var failed = 0
    while (currentCoroutineContext().isActive) {
        try {
            return block()
        } catch (e: Exception) {
            onFailure(e)
            failed++
            delay(backoffDelay(failed))
        }
    }
    yield() // throws CancellationException()
    throw CancellationException() // should not reach, defensive
}

/**
 * Runs the block multiple times, returns when it succeeds the first time. with a delay between each attempt.
 */
suspend inline fun <R> runUntilSuccess(
    onError: (Exception) -> Unit = { it.printStackTrace() },
    block: () -> R,
): R {
    contract { callsInPlace(block, InvocationKind.AT_LEAST_ONCE) }
    var failed = 0
    while (currentCoroutineContext().isActive) {
        try {
            return block()
        } catch (e: Exception) {
            onError(e)
            failed++
            delay(backoffDelay(failed))
        }
    }
    yield() // throws CancellationException()
    throw CancellationException() // should not reach, defensive
}

@PublishedApi
internal fun backoffDelay(failureCount: Int): Duration {
    return when (failureCount) {
        0, 1 -> 1.seconds
        2 -> 2.seconds
        3 -> 4.seconds
        else -> 8.seconds
    }
}
