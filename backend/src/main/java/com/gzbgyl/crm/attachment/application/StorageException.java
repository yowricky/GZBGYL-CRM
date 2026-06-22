package com.gzbgyl.crm.attachment.application;

public class StorageException extends RuntimeException {
    private final boolean notFound;

    public StorageException(String message) {
        this(message, null, false);
    }

    public StorageException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private StorageException(String message, Throwable cause, boolean notFound) {
        super(message, cause);
        this.notFound = notFound;
    }

    public static StorageException notFound(String message) {
        return new StorageException(message, null, true);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
