package io.jaiclaw.identity.link;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exchanges an authorization code at the provider's token endpoint, using the
 * JDK HTTP client.
 *
 * <p>Provider-neutral: RFC 6749 §4.1.3 plus RFC 7636 PKCE. The identity is read
 * from the returned ID token, whose claims are the standard OIDC ones.
 *
 * <p><strong>The ID token is not signature-verified here.</strong> That is
 * sound in this one context and nowhere else: the token arrived over a direct
 * TLS connection to the provider's token endpoint in response to a code we
 * generated, rather than from the user's browser. OpenID Connect Core §3.1.3.7
 * permits skipping verification exactly in this case. A token arriving by any
 * other route must be validated — that is what {@code jaiclaw-security-oidc}
 * does for the request path.
 *
 * <p>1.4.0.
 */
public class HttpTokenExchanger implements ChannelLinkService.TokenExchanger {

    private static final Logger log = LoggerFactory.getLogger(HttpTokenExchanger.class);

    private final ChannelLinkProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String tenantClaim;

    public HttpTokenExchanger(ChannelLinkProperties properties, String tenantClaim) {
        this(properties, tenantClaim, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public HttpTokenExchanger(ChannelLinkProperties properties, String tenantClaim,
                              HttpClient httpClient) {
        this.properties = properties;
        this.tenantClaim = (tenantClaim == null || tenantClaim.isBlank())
                ? "organization_id" : tenantClaim;
        this.httpClient = httpClient;
    }

    @Override
    public ExchangeResult exchange(String code, String pkceVerifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", properties.redirectUri());
        form.put("client_id", properties.clientId());
        form.put("code_verifier", pkceVerifier);
        if (properties.resource() != null) {
            form.put("resource", properties.resource());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.tokenUri()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)));

        if (properties.clientSecret() != null) {
            // client_secret_basic. Preferred over client_secret_post: the
            // credential stays out of the body, and so out of anything that logs
            // request bodies.
            String credentials = properties.clientId() + ":" + properties.clientSecret();
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Token endpoint unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token exchange interrupted", e);
        }

        if (response.statusCode() != 200) {
            // The body can carry the authorization code and client credentials —
            // log the status only.
            throw new IllegalStateException(
                    "Token endpoint returned HTTP " + response.statusCode());
        }

        JsonNode body = mapper.readTree(response.body());
        String idToken = body.path("id_token").asString(null);
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalStateException(
                    "Token response carried no id_token. Request the 'openid' scope.");
        }
        return fromIdToken(idToken);
    }

    /** Reads {@code sub}, {@code iss} and the tenant claim from an ID token. */
    ExchangeResult fromIdToken(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Malformed id_token");
        }
        JsonNode claims = mapper.readTree(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));

        String subject = claims.path("sub").asString(null);
        String issuer = claims.path("iss").asString(null);
        String tenantId = claims.path(tenantClaim).asString(null);

        log.debug("Exchanged code for subject={} issuer={} tenant={}", subject, issuer, tenantId);
        return new ExchangeResult(subject, issuer, tenantId);
    }

    private static String encode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        form.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
