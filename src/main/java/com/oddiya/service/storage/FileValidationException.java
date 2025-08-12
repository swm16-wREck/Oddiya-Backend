package com.oddiya.service.storage;

/**
 * Exception thrown when file validation fails.
 */
public class FileValidationException extends Exception {

    private final String validationType;
    private final Object actualValue;
    private final Object expectedValue;

    public FileValidationException(String message) {
        super(message);
        this.validationType = null;
        this.actualValue = null;
        this.expectedValue = null;
    }

    public FileValidationException(String message, String validationType) {
        super(message);
        this.validationType = validationType;
        this.actualValue = null;
        this.expectedValue = null;
    }

    public FileValidationException(String message, String validationType, Object actualValue, Object expectedValue) {
        super(message);
        this.validationType = validationType;
        this.actualValue = actualValue;
        this.expectedValue = expectedValue;
    }

    public String getValidationType() {
        return validationType;
    }

    public Object getActualValue() {
        return actualValue;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }
}