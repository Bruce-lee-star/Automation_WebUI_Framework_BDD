package com.hsbc.cmb.dbb.hk.automation.retry.configuration;

public class RoundResult {
    private final int roundNumber;
    private final int successCount;
    private final int failureCount;
    private final long durationMs;

    public RoundResult(int roundNumber, int successCount, int failureCount, long durationMs) {
        this.roundNumber = roundNumber;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.durationMs = durationMs;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getTotalCount() {
        return successCount + failureCount;
    }

    @Override
    public String toString() {
        return String.format("RoundResult{round=%d, success=%d, failure=%d, duration=%dms}",
                roundNumber, successCount, failureCount, durationMs);
    }
}
