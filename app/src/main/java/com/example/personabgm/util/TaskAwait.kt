package com.example.personabgm.util

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until a Play services [Task] finishes. Returns null when the task succeeds with no result
 * (for example `lastLocation` with nothing cached). A tiny local version of the
 * kotlinx-coroutines-play-services `await()`, to avoid one more dependency.
 */
internal suspend fun <T> Task<T>.awaitResult(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
