package com.aegis.api;

/** Thrown by {@link AegisConfigLoader} when a config file can't be read or isn't valid YAML. */
public class AegisConfigException extends RuntimeException {

    public AegisConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public AegisConfigException(String message) {
        super(message);
    }
}
