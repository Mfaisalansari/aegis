package com.aegis.core.llm;

/**
 * Minimal seam over "send a prompt to a language model, get text back" —
 * kept provider-agnostic so callers (e.g. LlmActionScorer) don't depend
 * on HTTP/JSON details or a specific vendor's SDK.
 */
public interface LlmChatClient {

    String complete(String systemPrompt, String userPrompt);

}
