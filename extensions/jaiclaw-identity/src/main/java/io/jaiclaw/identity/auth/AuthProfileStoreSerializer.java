package io.jaiclaw.identity.auth;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;
import io.jaiclaw.core.auth.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jackson serializer/deserializer for {@link AuthProfileStore} with full OpenClaw format compatibility.
 * <p>
 * Handles aliases:
 * <ul>
 *   <li>{@code mode} → {@code type} (OpenClaw uses "mode" in config, "type" in store)</li>
 *   <li>{@code apiKey} → {@code key} in {@link ApiKeyCredential}</li>
 *   <li>{@code ${VAR}} inline strings → {@link SecretRef} with ENV source</li>
 * </ul>
 */
public final class AuthProfileStoreSerializer {

    private static final Logger log = LoggerFactory.getLogger(AuthProfileStoreSerializer.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^\\$\\{([A-Z][A-Z0-9_]*)\\}$");

    private static final ObjectMapper MAPPER = createMapper();

    private AuthProfileStoreSerializer() {}

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("AuthProfileStore");
        module.addDeserializer(AuthProfileCredential.class, new CredentialDeserializer());
        module.addSerializer(AuthProfileCredential.class, new CredentialSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    /** Returns the shared ObjectMapper configured for auth profile serialization. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Deserialize an auth profile store from JSON bytes. */
    public static AuthProfileStore deserialize(byte[] json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        return parseStore(root);
    }

    /** Deserialize an auth profile store from a JSON string. */
    public static AuthProfileStore deserialize(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        return parseStore(root);
    }

    /** Serialize an auth profile store to JSON bytes, stripping inline secrets where refs exist. */
    public static byte[] serialize(AuthProfileStore store) throws IOException {
        AuthProfileStore sanitized = stripInlineSecrets(store);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(sanitized);
    }

    /** Serialize an auth profile store to a JSON string, stripping inline secrets where refs exist. */
    public static String serializeToString(AuthProfileStore store) throws IOException {
        AuthProfileStore sanitized = stripInlineSecrets(store);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sanitized);
    }

    // --- Internal parsing ---

    private static AuthProfileStore parseStore(JsonNode root) {
        int version = root.has("version") ? root.get("version").asInt(AuthProfileStore.CURRENT_VERSION) : AuthProfileStore.CURRENT_VERSION;

        Map<String, AuthProfileCredential> profiles = new LinkedHashMap<>();
        JsonNode profilesNode = root.get("profiles");
        if (profilesNode != null && profilesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = profilesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                try {
                    AuthProfileCredential cred = parseCredential(entry.getValue());
                    if (cred != null) {
                        profiles.put(entry.getKey(), cred);
                    }
                } catch (Exception e) {
                    log.warn("Skipping invalid profile '{}': {}", entry.getKey(), e.getMessage());
                }
            }
        }

        Map<String, List<String>> order = parseOrder(root.get("order"));
        Map<String, String> lastGood = parseStringMap(root.get("lastGood"));
        Map<String, ProfileUsageStats> usageStats = parseUsageStats(root.get("usageStats"));

        return new AuthProfileStore(version, Map.copyOf(profiles), Map.copyOf(order),
                Map.copyOf(lastGood), Map.copyOf(usageStats));
    }

    private static AuthProfileCredential parseCredential(JsonNode node) {
        if (node == null || !node.isObject()) return null;

        // Resolve type: "type" or "mode" alias
        String type = null;
        if (node.has("type")) {
            type = node.get("type").asText();
        } else if (node.has("mode")) {
            type = node.get("mode").asText();
        }
        if (type == null || type.isBlank()) return null;

        String provider = textOrNull(node, "provider");
        if (provider == null || provider.isBlank()) return null;

        return switch (type) {
            case "api_key" -> parseApiKey(node, provider);
            case "token" -> parseToken(node, provider);
            case "oauth" -> parseOAuth(node, provider);
            default -> {
                log.warn("Unknown credential type: {}", type);
                yield null;
            }
        };
    }

    private static ApiKeyCredential parseApiKey(JsonNode node, String provider) {
        // Accept "apiKey" as alias for "key" (OpenClaw compat)
        String key = textOrNull(node, "key");
        if (key == null) {
            key = textOrNull(node, "apiKey");
        }
        SecretRef keyRef = parseSecretRef(node.get("keyRef"));

        // Coerce ${VAR} inline strings to SecretRef
        if (key != null && keyRef == null) {
            SecretRef coerced = coerceEnvRef(key);
            if (coerced != null) {
                keyRef = coerced;
                key = null;
            }
        }

        String email = textOrNull(node, "email");
        Map<String, String> metadata = parseStringMetadata(node.get("metadata"));
        return new ApiKeyCredential(provider, key, keyRef, email, metadata);
    }

    private static TokenCredential parseToken(JsonNode node, String provider) {
        String token = textOrNull(node, "token");
        SecretRef tokenRef = parseSecretRef(node.get("tokenRef"));

        // Coerce ${VAR} inline strings to SecretRef
        if (token != null && tokenRef == null) {
            SecretRef coerced = coerceEnvRef(token);
            if (coerced != null) {
                tokenRef = coerced;
                token = null;
            }
        }

        Long expires = node.has("expires") && node.get("expires").isNumber()
                ? node.get("expires").asLong() : null;
        String email = textOrNull(node, "email");
        return new TokenCredential(provider, token, tokenRef, expires, email);
    }

    private static OAuthCredential parseOAuth(JsonNode node, String provider) {
        String access = textOrNull(node, "access");
        String refresh = textOrNull(node, "refresh");
        long expires = node.has("expires") ? node.get("expires").asLong(0) : 0;
        String email = textOrNull(node, "email");
        String clientId = textOrNull(node, "clientId");
        String accountId = textOrNull(node, "accountId");
        String projectId = textOrNull(node, "projectId");
        String enterpriseUrl = textOrNull(node, "enterpriseUrl");
        return new OAuthCredential(provider, access, refresh, expires, email,
                clientId, accountId, projectId, enterpriseUrl);
    }

    private static SecretRef parseSecretRef(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        String source = textOrNull(node, "source");
        String provider = textOrNull(node, "provider");
        String id = textOrNull(node, "id");
        if (source == null || id == null) return null;
        if (provider == null) provider = SecretRef.DEFAULT_PROVIDER;
        try {
            SecretRefSource refSource = SecretRefSource.valueOf(source.toUpperCase());
            return new SecretRef(refSource, provider, id);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown SecretRef source: {}", source);
            return null;
        }
    }

    /** Coerce "${VAR_NAME}" to SecretRef(ENV, "default", "VAR_NAME"). */
    static SecretRef coerceEnvRef(String value) {
        if (value == null) return null;
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        if (matcher.matches()) {
            return SecretRef.env(matcher.group(1));
        }
        return null;
    }

    private static Map<String, List<String>> parseOrder(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isArray()) {
                List<String> ids = new ArrayList<>();
                for (JsonNode item : entry.getValue()) {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        ids.add(item.asText());
                    }
                }
                if (!ids.isEmpty()) {
                    result.put(entry.getKey(), List.copyOf(ids));
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
                result.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return result;
    }

    private static Map<String, String> parseStringMetadata(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    private static Map<String, ProfileUsageStats> parseUsageStats(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, ProfileUsageStats> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                ProfileUsageStats stats = parseStats(entry.getValue());
                if (stats != null) {
                    result.put(entry.getKey(), stats);
                }
            } catch (Exception e) {
                log.warn("Skipping invalid usage stats for '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    private static ProfileUsageStats parseStats(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        Long lastUsed = longOrNull(node, "lastUsed");
        Long cooldownUntil = longOrNull(node, "cooldownUntil");
        Long disabledUntil = longOrNull(node, "disabledUntil");
        AuthProfileFailureReason disabledReason = null;
        if (node.has("disabledReason") && node.get("disabledReason").isTextual()) {
            try {
                disabledReason = AuthProfileFailureReason.valueOf(
                        node.get("disabledReason").asText().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        int errorCount = node.has("errorCount") ? node.get("errorCount").asInt(0) : 0;
        Map<AuthProfileFailureReason, Integer> failureCounts = new LinkedHashMap<>();
        JsonNode fcNode = node.get("failureCounts");
        if (fcNode != null && fcNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fcFields = fcNode.fields();
            while (fcFields.hasNext()) {
                Map.Entry<String, JsonNode> fcEntry = fcFields.next();
                try {
                    AuthProfileFailureReason reason = AuthProfileFailureReason.valueOf(
                            fcEntry.getKey().toUpperCase());
                    failureCounts.put(reason, fcEntry.getValue().asInt(0));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        Long lastFailureAt = longOrNull(node, "lastFailureAt");
        return new ProfileUsageStats(lastUsed, cooldownUntil, disabledUntil,
                disabledReason, errorCount, Map.copyOf(failureCounts), lastFailureAt);
    }

    // --- Sanitization (strip inline secrets where ref exists) ---

    private static AuthProfileStore stripInlineSecrets(AuthProfileStore store) {
        Map<String, AuthProfileCredential> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, AuthProfileCredential> entry : store.profiles().entrySet()) {
            sanitized.put(entry.getKey(), stripSecrets(entry.getValue()));
        }
        return store.withProfiles(Map.copyOf(sanitized));
    }

    private static AuthProfileCredential stripSecrets(AuthProfileCredential cred) {
        return switch (cred) {
            case ApiKeyCredential c -> c.keyRef() != null
                    ? new ApiKeyCredential(c.provider(), null, c.keyRef(), c.email(), c.metadata())
                    : c;
            case TokenCredential c -> c.tokenRef() != null
                    ? new TokenCredential(c.provider(), null, c.tokenRef(), c.expires(), c.email())
                    : c;
            case OAuthCredential c -> c; // OAuth creds don't use refs
        };
    }

    // --- Helpers ---

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isNumber()) ? child.asLong() : null;
    }

    /**
     * Custom deserializer that handles the sealed interface dispatch and OpenClaw aliases.
     */
    private static class CredentialDeserializer extends ValueDeserializer<AuthProfileCredential> {
        @Override
        public AuthProfileCredential deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return parseCredential(node);
        }
    }

    /**
     * Custom serializer that includes the "type" discriminator field for round-trip compatibility.
     */
    private static class CredentialSerializer extends ValueSerializer<AuthProfileCredential> {
        @Override
        public void serialize(AuthProfileCredential cred, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            switch (cred) {
                case ApiKeyCredential c -> {
                    gen.writeStringField("type", "api_key");
                    gen.writeStringField("provider", c.provider());
                    if (c.key() != null) gen.writeStringField("key", c.key());
                    if (c.keyRef() != null) writeSecretRef(gen, "keyRef", c.keyRef());
                    if (c.email() != null) gen.writeStringField("email", c.email());
                    if (c.metadata() != null && !c.metadata().isEmpty()) {
                        gen.writeObjectField("metadata", c.metadata());
                    }
                }
                case TokenCredential c -> {
                    gen.writeStringField("type", "token");
                    gen.writeStringField("provider", c.provider());
                    if (c.token() != null) gen.writeStringField("token", c.token());
                    if (c.tokenRef() != null) writeSecretRef(gen, "tokenRef", c.tokenRef());
                    if (c.expires() != null) gen.writeNumberField("expires", c.expires());
                    if (c.email() != null) gen.writeStringField("email", c.email());
                }
                case OAuthCredential c -> {
                    gen.writeStringField("type", "oauth");
                    gen.writeStringField("provider", c.provider());
                    if (c.access() != null) gen.writeStringField("access", c.access());
                    if (c.refresh() != null) gen.writeStringField("refresh", c.refresh());
                    gen.writeNumberField("expires", c.expires());
                    if (c.email() != null) gen.writeStringField("email", c.email());
                    if (c.clientId() != null) gen.writeStringField("clientId", c.clientId());
                    if (c.accountId() != null) gen.writeStringField("accountId", c.accountId());
                    if (c.projectId() != null) gen.writeStringField("projectId", c.projectId());
                    if (c.enterpriseUrl() != null) gen.writeStringField("enterpriseUrl", c.enterpriseUrl());
                }
            }
            gen.writeEndObject();
        }

        private void writeSecretRef(JsonGenerator gen, String fieldName, SecretRef ref) throws IOException {
            gen.writeObjectFieldStart(fieldName);
            gen.writeStringField("source", ref.source().name());
            gen.writeStringField("provider", ref.provider());
            gen.writeStringField("id", ref.id());
            gen.writeEndObject();
        }
    }
}
