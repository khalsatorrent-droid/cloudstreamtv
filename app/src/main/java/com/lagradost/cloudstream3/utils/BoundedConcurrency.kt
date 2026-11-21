package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Leaves a core for the ui when doing cpu heavy work for every installed extension */
val cpuBoundConcurrency: Int
    get() = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)

/** Like amap, but with at most [maxConcurrent] items running at the same time */
suspend fun <A, B> List<A>.amapLimited(maxConcurrent: Int, f: suspend (A) -> B): List<B> =
    coroutineScope {
        ensureActive()
        val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))
        map { async { semaphore.withPermit { f(it) } } }.map { it.await() }
    }
