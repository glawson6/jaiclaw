package io.jaiclaw.tools.security;

/**
 * Determines how the security handshake tools operate.
 *
 * <ul>
 *   <li><b>LOCAL</b> — Both client and server sides run in-process.
 *       Useful for demos, testing, and single-process deployments.</li>
 *   <li><b>HTTP_CLIENT</b> — Tools act as an HTTP client, sending handshake
 *       requests to a remote MCP server's security endpoint. The MCP server
 *       runs its own handshake logic and returns responses. The resulting
 *       session token is used as a Bearer token for subsequent MCP tool calls.</li>
 *   <li><b>ORCHESTRATED</b> — The LLM has access to client-side tools
 *       (key generation, signing) locally and calls server-side tools on the
 *       MCP server. The LLM passes data between both sides to complete the
 *       handshake. Gives the LLM full visibility into each step.</li>
 * </ul>
 */
public enum HandshakeMode {
    LOCAL,
    HTTP_CLIENT,
    ORCHESTRATED
}
