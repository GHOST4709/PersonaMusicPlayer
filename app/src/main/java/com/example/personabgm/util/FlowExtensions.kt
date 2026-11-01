package com.example.personabgm.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Debounce that lets the **first** value through immediately and delays every later one by [timeoutMs]
 * (a newer value replaces a pending one). Spec 5.6 asks for a 1 s debounce to coalesce near-simultaneous
 * changes, but a plain debounce would also hold back the very first track after the service starts.
 *
 * `channelFlow` is required: emitting from inside `collectLatest` directly would violate the flow invariant.
 */
internal fun <T> Flow<T>.debounceAfterFirst(timeoutMs: Long): Flow<T> = channelFlow {
    var first = true
    collectLatest { value ->
        if (first) first = false else delay(timeoutMs)
        send(value)
    }
}
