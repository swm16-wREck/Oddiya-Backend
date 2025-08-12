package com.oddiya.service.storage;

/**
 * Exception thrown when storage operations fail.
 */
public class StorageException extends Exception {

    private final String errorCode;
    private final String storageKey;

    public StorageException(String message) {
        super(message);
        this.errorCode = null;
        this.storageKey = null;
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.storageKey = null;
    }

    public StorageException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.storageKey = null;
    }

    public StorageException(String message, String errorCode, String storageKey) {
        super(message);
        this.errorCode = errorCode;
        this.storageKey = storageKey;
    }

    public StorageException(String message, Throwable cause, String errorCode, String storageKey) {
        super(message, cause);
        this.errorCode = errorCode;
        this.storageKey = storageKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getStorageKey() {
        return storageKey;
    }
}