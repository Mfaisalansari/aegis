package com.aegis.core.llm;

/** Wraps any failure to reach or parse a response from the configured LLM server. */
public class LlmClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
