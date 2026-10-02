package com.enerlytics.carbon.provider;

public class ProviderServerException extends ProviderException {

    public ProviderServerException(String message) {
        super(message);
    }

    public ProviderServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
