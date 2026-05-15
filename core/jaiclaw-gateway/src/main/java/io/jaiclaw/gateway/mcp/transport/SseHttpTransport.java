package io.jaiclaw.gateway.mcp.transport;

import java.io.InputStream;

/**
 * Abstracts the HTTP transport layer for SSE-based MCP communication.
 * Two operations are needed: opening an SSE event stream (GET) and
 * posting a JSON-RPC request (POST). Implementations can use
 * {@code java.net.http.HttpClient} or Spring {@code WebClient}.
 */
public interface SseHttpTransport extends AutoCloseable {

    /**
     * Open an SSE event stream by sending a GET request to the given URL.
     *
     * @param url the SSE endpoint URL
     * @return an InputStream of the raw SSE event stream
     * @throws Exception if the connection fails
     */
    InputStream connectSseStream(String url) throws Exception;

    /**
     * POST a JSON-RPC request body to the given URL.
     * The actual JSON-RPC response arrives via the SSE stream, not the HTTP body.
     *
     * @param url  the JSON-RPC POST endpoint URL
     * @param json the JSON-RPC request body
     * @return the HTTP status code
     * @throws Exception if the request fails
     */
    int postJsonRpc(String url, String json) throws Exception;

    @Override
    default void close() throws Exception {}
}
