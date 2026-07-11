package com.davismariotti.campalert.util

import kotlin.random.Random

/** Sleeps a random duration in `[0, maxMs]` — used to break up the otherwise perfectly regular timing of scheduled polling. */
fun sleepJitter(maxMs: Long) {
    if (maxMs <= 0) return
    Thread.sleep(Random.nextLong(maxMs + 1))
}
