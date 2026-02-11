package com.hsbc.cmb.hk.dbb.automation.retry.strategy;


public interface RetryDelayStrategy {
    long calculateDelay(int attemptNumber);

    String getName();
}

