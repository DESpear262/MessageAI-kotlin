/**
 * MessageAI – WorkManager helper utilities.
 *
 * Provides standardized WorkManager configurations for consistent behavior
 * across different worker types.
 */
package com.messageai.tactical.util

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

object WorkerHelper {
    /**
     * Creates standard constraints for message/image workers.
     * Requires network connectivity and sufficient battery level.
     */
    fun standardConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    }
    
    /**
     * Standard backoff policy for retries: exponential with 2-second minimum.
     */
    fun standardBackoffPolicy() = BackoffPolicy.EXPONENTIAL
    
    /**
     * Standard backoff delay in seconds.
     */
    const val BACKOFF_DELAY_SECONDS = 2L
    
    /**
     * Standard time unit for backoff delay.
     */
    val BACKOFF_TIME_UNIT = TimeUnit.SECONDS
}

