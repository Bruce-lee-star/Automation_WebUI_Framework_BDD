package com.hsbc.cmb.dbb.hk.automation.retry.strategy;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExponentialBackoffStrategy implements RetryDelayStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ExponentialBackoffStrategy.class);

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double multiplier;

    public ExponentialBackoffStrategy(long baseDelayMs, long maxDelayMs, double multiplier) {
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
    }

    @Override
    public long calculateDelay(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0;
        }

        long delay = (long) (baseDelayMs * Math.pow(multiplier, attemptNumber - 1));
        long cappedDelay = Math.min(delay, maxDelayMs);

        logger.debug("[ExponentialBackoffStrategy] Attempt {} - Base: {}ms, Multiplier: {}, Calculated: {}ms, Capped: {}ms",
                attemptNumber, baseDelayMs, multiplier, delay, cappedDelay);

        return cappedDelay;
    }

    @Override
    public String getName() {
        return "ExponentialBackoff";
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    @Override
    public String toString() {
        return String.format("ExponentialBackoffStrategy{baseDelayMs=%d, maxDelayMs=%d, multiplier=%.2f}",
                baseDelayMs, maxDelayMs, multiplier);
    }
}

