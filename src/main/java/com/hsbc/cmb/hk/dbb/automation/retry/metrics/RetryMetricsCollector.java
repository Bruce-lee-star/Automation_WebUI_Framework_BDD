package com.hsbc.cmb.hk.dbb.automation.retry.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RetryMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(RetryMetricsCollector.class);

    private final ConcurrentMap<String, ScenarioMetrics> scenarioMetrics = new ConcurrentHashMap<>();
    private final List<RoundMetrics> roundMetrics = new CopyOnWriteArrayList<>();
    private final AtomicInteger totalRounds = new AtomicInteger(0);
    private final AtomicInteger successfulRounds = new AtomicInteger(0);
    private final AtomicInteger failedRounds = new AtomicInteger(0);
    private final AtomicInteger totalRetries = new AtomicInteger(0);
    private final AtomicInteger successfulRetries = new AtomicInteger(0);
    private final AtomicInteger failedRetries = new AtomicInteger(0);

    private final Map<String, AtomicInteger> exceptionTypeCounts = new ConcurrentHashMap<>();
    private final List<Long> delayMeasurements = new CopyOnWriteArrayList<>();
    private final AtomicInteger totalDelayMs = new AtomicInteger(0);

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Starts a new metrics collection session.
     * Initializes all counters and clears previous data.
     * 
     * @since 1.0.0
     */
    public void startSession() {
        startTime = LocalDateTime.now();
        totalRounds.set(0);
        successfulRounds.set(0);
        failedRounds.set(0);
        totalRetries.set(0);
        successfulRetries.set(0);
        failedRetries.set(0);
        scenarioMetrics.clear();
        roundMetrics.clear();
        exceptionTypeCounts.clear();
        delayMeasurements.clear();
        totalDelayMs.set(0);
        logger.info("[Metrics] Starting retry metrics collection session");
    }

    /**
     * Ends the current metrics collection session.
     * 
     * @since 1.0.0
     */
    public void endSession() {
        endTime = LocalDateTime.now();
        logger.info("[Metrics] Ending retry metrics collection session");
    }

    /**
     * Records the start of a retry round.
     * 
     * @param round The current round number
     * @param maxRounds The maximum number of rounds
     * @since 1.0.0
     */
    public void recordRoundStart(int round, int maxRounds) {
        RoundMetrics metrics = new RoundMetrics(round, maxRounds);
        metrics.start();
        roundMetrics.add(metrics);
        totalRounds.incrementAndGet();
        logger.debug("[Metrics] Recording round {} start", round);
    }

    /**
     * Records the end of a retry round with results.
     * 
     * @param round The round number
     * @param exitCode The exit code of the round (0 for success)
     * @param durationMs The duration of the round in milliseconds
     * @param passed Number of passed tests
     * @param failed Number of failed tests
     * @param retriedPassed Number of tests that passed on retry
     * @since 1.0.0
     */
    public void recordRoundEnd(int round, int exitCode, long durationMs, int passed, int failed, int retriedPassed) {
        Optional<RoundMetrics> metricsOpt = roundMetrics.stream()
            .filter(m -> m.getRound() == round)
            .findFirst();

        if (metricsOpt.isPresent()) {
            RoundMetrics metrics = metricsOpt.get();
            metrics.end(durationMs);
            metrics.setExitCode(exitCode);
            metrics.setResults(passed, failed, retriedPassed);

            if (exitCode == 0) {
                successfulRounds.incrementAndGet();
            } else {
                failedRounds.incrementAndGet();
            }
        }

        logger.info("[Metrics] Round {} completed - Duration: {}ms, Passed: {}, Failed: {}, Retried Passed: {}",
            round, durationMs, passed, failed, retriedPassed);
    }

    /**
     * Records a scenario retry attempt.
     * 
     * @param scenarioId The unique identifier of the scenario
     * @param attempt The attempt number
     * @param durationMs The duration of the attempt in milliseconds
     * @param success Whether the attempt was successful
     * @since 1.0.0
     */
    public void recordScenarioRetry(String scenarioId, int attempt, long durationMs, boolean success) {
        ScenarioMetrics metrics = scenarioMetrics.computeIfAbsent(scenarioId,
            id -> new ScenarioMetrics(id));

        metrics.recordAttempt(attempt, durationMs, success);

        totalRetries.incrementAndGet();
        if (success) {
            successfulRetries.incrementAndGet();
        } else {
            failedRetries.incrementAndGet();
        }

        logger.debug("[Metrics] Scenario {} attempt {} {} - Duration: {}ms",
            scenarioId, attempt, success ? "success" : "failure", durationMs);
    }

    /**
     * Records a scenario failure.
     * 
     * @param scenarioId The unique identifier of the scenario
     * @param attempt The attempt number
     * @param errorType The type of error encountered
     * @param errorMessage The error message
     * @since 1.0.0
     */
    public void recordScenarioFailure(String scenarioId, int attempt, String errorType, String errorMessage) {
        ScenarioMetrics metrics = scenarioMetrics.computeIfAbsent(scenarioId,
            id -> new ScenarioMetrics(id));

        metrics.recordFailure(attempt, errorType, errorMessage);

        exceptionTypeCounts.computeIfAbsent(errorType, k -> new AtomicInteger(0)).incrementAndGet();

        logger.debug("[Metrics] Scenario {} attempt {} failure - Type: {}, Message: {}",
            scenarioId, attempt, errorType, errorMessage);
    }

    public void recordRetryDelay(long delayMs) {
        delayMeasurements.add(delayMs);
        totalDelayMs.addAndGet((int) delayMs);
        logger.debug("[Metrics] Recorded retry delay: {}ms", delayMs);
    }

    public Map<String, Integer> getExceptionTypeCounts() {
        Map<String, Integer> result = new HashMap<>();
        exceptionTypeCounts.forEach((k, v) -> result.put(k, v.get()));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Double> getExceptionTypePercentages() {
        int total = exceptionTypeCounts.values().stream().mapToInt(AtomicInteger::get).sum();
        if (total == 0) {
            return Collections.emptyMap();
        }

        Map<String, Double> percentages = new HashMap<>();
        exceptionTypeCounts.forEach((type, count) ->
            percentages.put(type, count.get() * 100.0 / total));
        return Collections.unmodifiableMap(percentages);
    }

    public DelayStatistics getDelayStatistics() {
        if (delayMeasurements.isEmpty()) {
            return new DelayStatistics(0, 0, 0, 0);
        }

        long min = delayMeasurements.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = delayMeasurements.stream().mapToLong(Long::longValue).max().orElse(0);
        double avg = delayMeasurements.stream().mapToLong(Long::longValue).average().orElse(0);
        long total = delayMeasurements.stream().mapToLong(Long::longValue).sum();

        return new DelayStatistics(delayMeasurements.size(), min, max, (long) avg, total);
    }

    public RetryMetrics getOverallMetrics() {
        Duration totalDuration = Duration.between(startTime, endTime != null ? endTime : LocalDateTime.now());

        return new RetryMetrics(
            totalRounds.get(),
            successfulRounds.get(),
            failedRounds.get(),
            totalRetries.get(),
            successfulRetries.get(),
            failedRetries.get(),
            scenarioMetrics.size(),
            totalDuration.toMillis()
        );
    }

    public Map<String, ScenarioMetrics> getScenarioMetrics() {
        return Collections.unmodifiableMap(scenarioMetrics);
    }

    public List<RoundMetrics> getRoundMetrics() {
        return Collections.unmodifiableList(roundMetrics);
    }

    public String getFormattedSummary() {
        RetryMetrics metrics = getOverallMetrics();
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================\n");
        sb.append("         重试监控指标汇总\n");
        sb.append("========================================\n");
        sb.append(String.format("总轮次:          %d\n", metrics.getTotalRounds()));
        sb.append(String.format("成功轮次:        %d\n", metrics.getSuccessfulRounds()));
        sb.append(String.format("失败轮次:        %d\n", metrics.getFailedRounds()));
        sb.append(String.format("成功率:          %.2f%%\n", metrics.getRoundSuccessRate()));
        sb.append(String.format("总重试次数:      %d\n", metrics.getTotalRetries()));
        sb.append(String.format("成功重试:        %d\n", metrics.getSuccessfulRetries()));
        sb.append(String.format("失败重试:        %d\n", metrics.getFailedRetries()));
        sb.append(String.format("重试成功率:      %.2f%%\n", metrics.getRetrySuccessRate()));
        sb.append(String.format("涉及场景数:      %d\n", metrics.getUniqueScenarios()));
        sb.append(String.format("总耗时:          %d ms\n", metrics.getTotalDurationMs()));
        sb.append("========================================\n");

        List<ScenarioMetrics> failedScenarios = scenarioMetrics.values().stream()
            .filter(m -> !m.isUltimatelySuccessful())
            .sorted((a, b) -> Integer.compare(b.getRetryCount(), a.getRetryCount()))
            .collect(Collectors.toList());

        if (!failedScenarios.isEmpty()) {
            sb.append("\n失败场景 (按重试次数排序):\n");
            for (ScenarioMetrics sm : failedScenarios.stream().limit(10).collect(Collectors.toList())) {
                sb.append(String.format("  - %s: 重试 %d 次\n", sm.getScenarioId(), sm.getRetryCount()));
            }
        }

        return sb.toString();
    }

    public static class RoundMetrics {
        private final int round;
        private final int maxRounds;
        private long startTimeMs;
        private long endTimeMs;
        private int exitCode;
        private int passed;
        private int failed;
        private int retriedPassed;

        public RoundMetrics(int round, int maxRounds) {
            this.round = round;
            this.maxRounds = maxRounds;
        }

        public void start() {
            this.startTimeMs = System.currentTimeMillis();
        }

        public void end(long durationMs) {
            this.endTimeMs = startTimeMs + durationMs;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public void setResults(int passed, int failed, int retriedPassed) {
            this.passed = passed;
            this.failed = failed;
            this.retriedPassed = retriedPassed;
        }

        public int getRound() { return round; }
        public int getMaxRounds() { return maxRounds; }
        public long getStartTimeMs() { return startTimeMs; }
        public long getEndTimeMs() { return endTimeMs; }
        public long getDurationMs() { return endTimeMs - startTimeMs; }
        public int getExitCode() { return exitCode; }
        public int getPassed() { return passed; }
        public int getFailed() { return failed; }
        public int getRetriedPassed() { return retriedPassed; }
    }

    public static class ScenarioMetrics {
        private final String scenarioId;
        private final List<AttemptMetrics> attempts = new CopyOnWriteArrayList<>();
        private final List<FailureInfo> failures = new CopyOnWriteArrayList<>();
        private int maxAttempts;
        private boolean ultimatelySuccessful;

        public ScenarioMetrics(String scenarioId) {
            this.scenarioId = scenarioId;
            this.maxAttempts = 0;
            this.ultimatelySuccessful = false;
        }

        public void recordAttempt(int attempt, long durationMs, boolean success) {
            attempts.add(new AttemptMetrics(attempt, durationMs, success));
            if (attempt > maxAttempts) {
                maxAttempts = attempt;
            }
            if (success) {
                ultimatelySuccessful = true;
            }
        }

        public void recordFailure(int attempt, String errorType, String errorMessage) {
            failures.add(new FailureInfo(attempt, errorType, errorMessage));
        }

        public String getScenarioId() { return scenarioId; }
        public List<AttemptMetrics> getAttempts() { return Collections.unmodifiableList(attempts); }
        public List<FailureInfo> getFailures() { return Collections.unmodifiableList(failures); }
        public int getRetryCount() { return Math.max(0, maxAttempts - 1); }
        public int getTotalAttempts() { return maxAttempts; }
        public boolean isUltimatelySuccessful() { return ultimatelySuccessful; }

        public long getTotalDurationMs() {
            return attempts.stream().mapToLong(AttemptMetrics::getDurationMs).sum();
        }

        public Optional<String> getMostCommonErrorType() {
            return failures.stream()
                .map(f -> f.errorType)
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
        }
    }

    public static class AttemptMetrics {
        private final int attempt;
        private final long durationMs;
        private final boolean success;

        public AttemptMetrics(int attempt, long durationMs, boolean success) {
            this.attempt = attempt;
            this.durationMs = durationMs;
            this.success = success;
        }

        public int getAttempt() { return attempt; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
    }

    public static class FailureInfo {
        private final int attempt;
        private final String errorType;
        private final String errorMessage;

        public FailureInfo(int attempt, String errorType, String errorMessage) {
            this.attempt = attempt;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public int getAttempt() { return attempt; }
        public String getErrorType() { return errorType; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class RetryMetrics {
        private final int totalRounds;
        private final int successfulRounds;
        private final int failedRounds;
        private final int totalRetries;
        private final int successfulRetries;
        private final int failedRetries;
        private final int uniqueScenarios;
        private final long totalDurationMs;

        public RetryMetrics(int totalRounds, int successfulRounds, int failedRounds,
                           int totalRetries, int successfulRetries, int failedRetries,
                           int uniqueScenarios, long totalDurationMs) {
            this.totalRounds = totalRounds;
            this.successfulRounds = successfulRounds;
            this.failedRounds = failedRounds;
            this.totalRetries = totalRetries;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
            this.uniqueScenarios = uniqueScenarios;
            this.totalDurationMs = totalDurationMs;
        }

        public int getTotalRounds() { return totalRounds; }
        public int getSuccessfulRounds() { return successfulRounds; }
        public int getFailedRounds() { return failedRounds; }
        public int getTotalRetries() { return totalRetries; }
        public int getSuccessfulRetries() { return successfulRetries; }
        public int getFailedRetries() { return failedRetries; }
        public int getUniqueScenarios() { return uniqueScenarios; }
        public long getTotalDurationMs() { return totalDurationMs; }

        public double getRoundSuccessRate() {
            return totalRounds > 0 ? (successfulRounds * 100.0 / totalRounds) : 0.0;
        }

        public double getRetrySuccessRate() {
            return totalRetries > 0 ? (successfulRetries * 100.0 / totalRetries) : 0.0;
        }

        public String getFormattedRoundSuccessRate() {
            return String.format("%.2f%%", getRoundSuccessRate());
        }

        public String getFormattedRetrySuccessRate() {
            return String.format("%.2f%%", getRetrySuccessRate());
        }
    }

    public static class DelayStatistics {
        private final int count;
        private final long minMs;
        private final long maxMs;
        private final long avgMs;
        private final long totalMs;

        public DelayStatistics(int count, long minMs, long maxMs, long avgMs, long totalMs) {
            this.count = count;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.avgMs = avgMs;
            this.totalMs = totalMs;
        }

        public DelayStatistics(int count, long minMs, long maxMs, long totalMs) {
            this(count, minMs, maxMs, count > 0 ? totalMs / count : 0, totalMs);
        }

        public int getCount() { return count; }
        public long getMinMs() { return minMs; }
        public long getMaxMs() { return maxMs; }
        public long getAvgMs() { return avgMs; }
        public long getTotalMs() { return totalMs; }

        public String getFormattedSummary() {
            return String.format("DelayStats{count=%d, min=%dms, max=%dms, avg=%dms, total=%dms}",
                count, minMs, maxMs, avgMs, totalMs);
        }
    }
}
