package com.spotterkanji.app.review

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time now, then once a minute.
 *
 * Due counts change in two ways: when the database does — a word filed or
 * answered, which Room's flows report by themselves — and when the clock passes
 * a due time, which nothing reports. Re-querying on this tick covers the second.
 * A minute is fine-grained enough for counts measured in words, not seconds.
 */
internal fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}
