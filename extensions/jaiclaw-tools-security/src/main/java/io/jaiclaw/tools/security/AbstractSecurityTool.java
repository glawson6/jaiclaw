package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;

/**
 * Base class for security handshake tools.
 * Holds references to the crypto service, session store, and handshake properties.
 */
public abstract class AbstractSecurityTool extends AbstractBuiltinTool {

    protected final CryptoService cryptoService;
    protected final HandshakeSessionStore sessionStore;
    protected final SecurityHandshakeProperties properties;

    protected AbstractSecurityTool(ToolDefinition definition,
                                   CryptoService cryptoService,
                                   HandshakeSessionStore sessionStore,
                                   SecurityHandshakeProperties properties) {
        super(definition);
        this.cryptoService = cryptoService;
        this.sessionStore = sessionStore;
        this.properties = properties;
    }

    protected int optionalIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return ((Number) value).intValue();
    }

    protected HandshakeMode mode() {
        return properties.mode();
    }
}
