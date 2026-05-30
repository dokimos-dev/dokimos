package dev.dokimos.server.entity;

/**
 * The API surface an {@link LlmConnection}'s endpoint speaks. The server judge builds its request and
 * parses its response according to this value.
 */
public enum LlmConnectionProtocol {

    /**
     * The Responses API ({@code POST {baseUrl}/responses}, {@code input} items, {@code output_text}
     * results). The default for new connections.
     */
    RESPONSES,

    /**
     * The older Chat Completions API ({@code POST {baseUrl}/chat/completions}, {@code messages},
     * {@code choices}). Kept for endpoints that do not yet implement the Responses API.
     */
    CHAT_COMPLETIONS
}
