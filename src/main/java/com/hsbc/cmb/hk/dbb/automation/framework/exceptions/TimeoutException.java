package com.hsbc.cmb.hk.dbb.automation.framework.exceptions;

/**
 * 超时异常
 */
public class TimeoutException extends ElementException {
    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}