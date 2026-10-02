package com.enerlytics.carbon.provider;

public class ProviderResponseException extends ProviderException {

    public ProviderResponseException(String message) {
        super(message);
    }

    public ProviderResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
