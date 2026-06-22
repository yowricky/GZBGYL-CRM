package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.shared.api.InvalidStateException;

public class DataScopeUnavailableException extends InvalidStateException {
    public DataScopeUnavailableException(String message) {
        super(message);
    }
}
