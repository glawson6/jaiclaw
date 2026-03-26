package io.jaiclaw.tools.security;

/**
 * Configurable bootstrap trust level for the security handshake.
 *
 * <ul>
 *   <li><b>API_KEY</b> — Client sends a pre-shared API key in the initial negotiate request.
 *       Server validates before proceeding with ECDH. ECDH provides forward secrecy on top.</li>
 *   <li><b>CLIENT_CERT</b> — Client signs its nonce with a pre-registered public key.
 *       Server verifies the signature against a registry of allowed client keys.</li>
 *   <li><b>MUTUAL</b> — API key for bootstrap + ECDH for forward secrecy + server proves
 *       identity back by signing its nonce. Prevents MITM.</li>
 * </ul>
 */
public enum BootstrapTrust {
    API_KEY,
    CLIENT_CERT,
    MUTUAL
}
