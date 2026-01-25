package com.hsbc.cmb.dbb.hk.automation.retry.strategy;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixedDelayStrategy implements RetryDelayStrategy {
    private static final Logger logger = LoggerFactory.getLogger(FixedDelayStrategy.class);

    private final long delayMs;

    public FixedDelayStrategy(long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public long calculateDelay(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0;
        }
        logger.debug("[FixedDelayStrategy] Attempt {} - Fixed delay: {}ms", attemptNumber, delayMs);
        return delayMs;
    }

    @Override
    public String getName() {
        return "FixedDelay";
    }

    public long getDelayMs() {
        return delayMs;
    }

    @Override
    public String toString() {
        return String.format("FixedDelayStrategy{delayMs=%d}", delayMs);
    }
}

