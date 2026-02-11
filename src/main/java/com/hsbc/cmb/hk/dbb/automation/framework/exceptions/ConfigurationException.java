package com.hsbc.cmb.hk.dbb.automation.framework.exceptions;

public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ConfigurationException(String message) {
        super(message);
    }
}