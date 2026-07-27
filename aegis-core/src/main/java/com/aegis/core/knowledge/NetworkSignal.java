package com.aegis.core.knowledge;

/**
 * One HTTP response or failed request captured live during the mission —
 * {@code requestUrl} is the resource that failed/errored, {@code pageUrl}
 * is the page it happened on ({@code page.url()} at fire time). {@code
 * status} is {@code -1} for a request that failed outright (no response
 * at all), a real HTTP status code otherwise.
 */
public record NetworkSignal(String requestUrl, int status, String method, String resourceType, String pageUrl) {
}
