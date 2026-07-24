package com.aegis.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to any LLM server exposing an OpenAI-compatible
 * /chat/completions endpoint. Ollama, LM Studio, vLLM, llama.cpp server,
 * and OpenAI itself all speak this same request/response shape, so
 * switching providers is a configuration change — base URL, model name,
 * API key — never a code change.
 *
 * Configured from environment variables rather than mission parameters
 * deliberately: mission parameters end up serialized into every report,
 * and an API key must never end up there.
 *
 *   AEGIS_LLM_BASE_URL        default http://localhost:11434/v1 (Ollama)
 *   AEGIS_LLM_MODEL           default llama3.1
 *   AEGIS_LLM_API_KEY         default "" — most local servers ignore it;
 *                             set this to point at a real hosted provider
 *   AEGIS_LLM_TIMEOUT_SECONDS default 30
 */
public class OpenAiCompatibleChatClient implements LlmChatClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434/v1";
    private static final String DEFAULT_MODEL = "llama3.1";
    private static final String DEFAULT_TIMEOUT_SECONDS = "30";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public static OpenAiCompatibleChatClient fromEnvironment() {

        return new OpenAiCompatibleChatClient(
                envOrDefault("AEGIS_LLM_BASE_URL", DEFAULT_BASE_URL),
                envOrDefault("AEGIS_LLM_MODEL", DEFAULT_MODEL),
                envOrDefault("AEGIS_LLM_API_KEY", ""),
                Duration.ofSeconds(Long.parseLong(envOrDefault("AEGIS_LLM_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS)))
        );
    }

    public OpenAiCompatibleChatClient(String baseUrl, String model, String apiKey, Duration timeout) {

        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    private static String envOrDefault(String key, String defaultValue) {

        String value = System.getenv(key);

        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {

        try {

            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.2);

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmClientException(
                        "LLM server returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.isNull()) {
                throw new LlmClientException(
                        "LLM response had no choices[0].message.content: " + response.body());
            }

            return content.asText();

        } catch (IOException e) {
            throw new LlmClientException("Failed to call LLM at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmClientException("Interrupted while calling LLM at " + baseUrl, e);
        }
    }
}
