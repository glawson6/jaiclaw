package io.jaiclaw.rules.engine.loader;

import org.kie.api.builder.KieFileSystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule loader implementation that loads DRL files from HTTP/HTTPS URLs.
 */
public class UrlRuleLoader extends AbstractRuleLoader {

    private static final String LOADER_TYPE = "url";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final Map<String, String> properties;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public UrlRuleLoader(List<String> locations, boolean enabled, int priority,
                         Map<String, String> properties) {
        super(locations, enabled, priority);
        this.properties = properties != null ? properties : Map.of();
        this.connectTimeoutMs = parseTimeout(properties, "connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT_MS);
        this.readTimeoutMs = parseTimeout(properties, "read-timeout-ms", DEFAULT_READ_TIMEOUT_MS);
    }

    public UrlRuleLoader(List<String> locations, boolean enabled, Map<String, String> properties) {
        this(locations, enabled, 100, properties);
    }

    public UrlRuleLoader(List<String> locations, boolean enabled) {
        this(locations, enabled, 100, Map.of());
    }

    @Override
    public String getLoaderType() {
        return LOADER_TYPE;
    }

    @Override
    public void loadRules(KieFileSystem kieFileSystem) throws IOException, RuleLoadingException {
        if (!enabled) {
            logger.debug("URL rule loader is disabled, skipping");
            return;
        }

        logger.info("Loading rules from URLs: {}", locations);

        int totalRulesLoaded = 0;
        List<String> failedLocations = new ArrayList<>();

        for (String location : locations) {
            try {
                loadRuleFromUrl(kieFileSystem, location, totalRulesLoaded);
                totalRulesLoaded++;
            } catch (RuleLoadingException e) {
                failedLocations.add(location);
                logger.warn("Failed to load rule from URL: {}", location, e);
            }
        }

        if (totalRulesLoaded == 0 && !failedLocations.isEmpty()) {
            throw new RuleLoadingException(
                LOADER_TYPE,
                String.join(", ", failedLocations),
                "No rules could be loaded from any configured URL"
            );
        }

        logger.info("Successfully loaded {} rule file(s) from URLs", totalRulesLoaded);
    }

    private void loadRuleFromUrl(KieFileSystem kieFileSystem, String urlString, int index)
            throws RuleLoadingException {

        validateUrl(urlString);

        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlString).toURL();
            URLConnection urlConnection = url.openConnection();

            if (!(urlConnection instanceof HttpURLConnection)) {
                throw new RuleLoadingException(LOADER_TYPE, urlString, "Only HTTP/HTTPS protocols are supported");
            }

            connection = (HttpURLConnection) urlConnection;
            configureConnection(connection);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuleLoadingException(
                    LOADER_TYPE, urlString,
                    String.format("HTTP request failed with status code: %d", responseCode));
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE) {
                throw new RuleLoadingException(
                    LOADER_TYPE, urlString,
                    String.format("Response size (%d bytes) exceeds maximum allowed (%d bytes)", contentLength, MAX_FILE_SIZE));
            }

            String content = readResponseContent(connection);
            validateRuleContent(content, urlString);

            String kieResourcePath = generateKieResourcePath(urlString, index);
            kieFileSystem.write(kieResourcePath, content);

        } catch (IOException e) {
            throw new RuleLoadingException(
                LOADER_TYPE, urlString, "Failed to fetch rule from URL: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void validateUrl(String urlString) throws RuleLoadingException {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new RuleLoadingException(LOADER_TYPE, urlString, "URL cannot be null or empty");
        }

        try {
            URI uri = URI.create(urlString);
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new RuleLoadingException(LOADER_TYPE, urlString, "Only HTTP and HTTPS protocols are supported");
            }

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new RuleLoadingException(LOADER_TYPE, urlString, "URL must have a valid host");
            }

            if (isBlockedHost(host)) {
                throw new RuleLoadingException(LOADER_TYPE, urlString, "Access to this host is not allowed");
            }

        } catch (IllegalArgumentException e) {
            throw new RuleLoadingException(LOADER_TYPE, urlString, "Invalid URL format: " + e.getMessage(), e);
        }
    }

    private boolean isBlockedHost(String host) {
        String allowPrivateHosts = properties.getOrDefault("allow-private-hosts", "false");
        if ("true".equalsIgnoreCase(allowPrivateHosts)) {
            return false;
        }

        String lowerHost = host.toLowerCase();
        return lowerHost.equals("localhost") ||
               lowerHost.equals("127.0.0.1") ||
               lowerHost.equals("0.0.0.0") ||
               lowerHost.startsWith("192.168.") ||
               lowerHost.startsWith("10.") ||
               lowerHost.startsWith("172.16.") ||
               lowerHost.startsWith("172.17.") ||
               lowerHost.startsWith("172.18.") ||
               lowerHost.startsWith("172.19.") ||
               lowerHost.startsWith("172.2") ||
               lowerHost.startsWith("172.30.") ||
               lowerHost.startsWith("172.31.");
    }

    private void configureConnection(HttpURLConnection connection) {
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("User-Agent", "JaiClaw-Rules-Loader/1.0");
        connection.setRequestProperty("Accept", "text/plain, application/octet-stream");

        properties.forEach((key, value) -> {
            if (key.startsWith("header.")) {
                String headerName = key.substring("header.".length());
                connection.setRequestProperty(headerName, value);
            }
        });

        String authHeader = properties.get("authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            connection.setRequestProperty("Authorization", authHeader);
        }
    }

    private String readResponseContent(HttpURLConnection connection) throws IOException {
        try (InputStream is = connection.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            if (bytes.length > MAX_FILE_SIZE) {
                throw new IOException(
                    String.format("Response size (%d bytes) exceeds maximum allowed (%d bytes)", bytes.length, MAX_FILE_SIZE));
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static int parseTimeout(Map<String, String> properties, String key, int defaultValue) {
        if (properties == null || !properties.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(properties.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void validateConfiguration() throws RuleLoadingException {
        super.validateConfiguration();
        for (String location : locations) {
            validateUrl(location);
        }
    }
}
