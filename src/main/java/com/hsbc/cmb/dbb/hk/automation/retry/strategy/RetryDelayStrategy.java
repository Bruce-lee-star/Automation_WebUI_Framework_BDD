package com.hsbc.cmb.dbb.hk.automation.retry.strategy;


public interface RetryDelayStrategy {
    long calculateDelay(int attemptNumber);

    String getName();
}

