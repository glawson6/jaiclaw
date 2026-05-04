package io.jaiclaw.identity.oauth;

import io.jaiclaw.core.auth.OAuthCredential;
import io.jaiclaw.identity.auth.AuthProfileStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Orchestrates OAuth flows (authorization code + device code) for credential acquisition.
 * Handles environment detection, browser/URL display, and credential storage.
 */
public class OAuthFlowManager {

    private static final Logger log = LoggerFactory.getLogger(OAuthFlowManager.class);
    private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);

    private final Map<String, OAuthProviderConfig> providerConfigs;
    private final AuthProfileStoreManager storeManager;
    private final AuthorizationCodeFlow authCodeFlow;
    private final DeviceCodeFlow deviceCodeFlow;
    private final ResourceOwnerPasswordFlow ropcFlow;

    public OAuthFlowManager(Map<String, OAuthProviderConfig> providerConfigs,
                            AuthProfileStoreManager storeManager) {
        this(providerConfigs, storeManager, new AuthorizationCodeFlow(), new DeviceCodeFlow(),
                new ResourceOwnerPasswordFlow());
    }

    public OAuthFlowManager(Map<String, OAuthProviderConfig> providerConfigs,
                            AuthProfileStoreManager storeManager,
                            AuthorizationCodeFlow authCodeFlow,
                            DeviceCodeFlow deviceCodeFlow,
                            ResourceOwnerPasswordFlow ropcFlow) {
        this.providerConfigs = new ConcurrentHashMap<>(providerConfigs);
        this.storeManager = storeManager;
        this.authCodeFlow = authCodeFlow;
        this.deviceCodeFlow = deviceCodeFlow;
        this.ropcFlow = ropcFlow;
    }

    /** Register a provider config. */
    public void registerProvider(OAuthProviderConfig config) {
        providerConfigs.put(config.providerId(), config);
    }

    /** Get a provider config by ID. */
    public Optional<OAuthProviderConfig> getProvider(String providerId) {
        return Optional.ofNullable(providerConfigs.get(providerId));
    }

    /** List all registered provider IDs. */
    public java.util.Set<String> listProviders() {
        return java.util.Set.copyOf(providerConfigs.keySet());
    }

    /**
     * Run the full OAuth login flow for a provider.
     *
     * @param providerId    the provider to authenticate with
     * @param agentDir      the agent directory to store credentials
     * @param outputHandler callback for displaying messages to the user (URL, instructions)
     * @param inputHandler  callback for reading user input (for manual URL paste in remote mode)
     * @return the OAuth flow result
     */
    public OAuthFlowResult login(String providerId, Path agentDir,
                                  Consumer<String> outputHandler,
                                  java.util.function.Supplier<String> inputHandler) throws OAuthFlowException {
        OAuthProviderConfig config = providerConfigs.get(providerId);
        if (config == null) {
            throw new OAuthFlowException("Unknown OAuth provider: " + providerId);
        }

        OAuthFlowResult result = switch (config.flowType()) {
            case AUTHORIZATION_CODE -> runAuthCodeFlow(config, outputHandler, inputHandler);
            case DEVICE_CODE -> runDeviceCodeFlow(config, outputHandler);
            case RESOURCE_OWNER_PASSWORD -> runROPCFlow(config, outputHandler, inputHandler);
        };

        // Store the credential
        String profileId = providerId + ":" + (result.email() != null ? result.email() : "default");
        OAuthCredential credential = new OAuthCredential(
                providerId, result.accessToken(), result.refreshToken(), result.expiresAt(),
                result.email(), result.clientId(), result.accountId(), result.projectId(), null);

        storeManager.upsertProfile(agentDir, profileId, credential);
        storeManager.syncToSiblings(agentDir, profileId, credential);

        log.info("Logged in as {} ({})", result.email() != null ? result.email() : "user", providerId);
        return result;
    }

    private OAuthFlowResult runAuthCodeFlow(OAuthProviderConfig config,
                                             Consumer<String> outputHandler,
                                             java.util.function.Supplier<String> inputHandler)
            throws OAuthFlowException {
        PkceGenerator.PkceChallenge pkce = PkceGenerator.generate();
        String state = PkceGenerator.generateState();
        String authorizeUrl = authCodeFlow.buildAuthorizeUrl(config, pkce, state);

        if (RemoteEnvironmentDetector.isRemote()) {
            // Remote mode: print URL and wait for manual paste
            outputHandler.accept("Open this URL in your browser to authenticate:\n\n  " + authorizeUrl + "\n");
            outputHandler.accept("After authorizing, paste the redirect URL here:");
            String redirectUrl = inputHandler.get();

            String code = extractCodeFromRedirectUrl(redirectUrl, state);
            return authCodeFlow.exchangeCode(config, code, pkce.verifier());
        } else {
            // Local mode: open browser and start callback server
            try (OAuthCallbackServer server = new OAuthCallbackServer(
                    config.callbackPort(), config.callbackPath(), state, CALLBACK_TIMEOUT)) {

                outputHandler.accept("Opening browser for authentication...");
                boolean opened = BrowserLauncher.open(authorizeUrl);
                if (!opened) {
                    outputHandler.accept("Could not open browser. Open this URL manually:\n\n  " + authorizeUrl);
                }

                OAuthCallbackServer.OAuthCallbackResult callback = server.awaitCallback();
                return authCodeFlow.exchangeCode(config, callback.code(), pkce.verifier());
            } catch (IOException e) {
                throw new OAuthFlowException("Failed to start callback server", e);
            } catch (TimeoutException e) {
                throw new OAuthFlowException("Timed out waiting for OAuth callback");
            }
        }
    }

    private OAuthFlowResult runDeviceCodeFlow(OAuthProviderConfig config,
                                               Consumer<String> outputHandler) throws OAuthFlowException {
        DeviceCodeFlow.DeviceCodeResponse deviceCode = deviceCodeFlow.requestDeviceCode(config);

        outputHandler.accept("To authenticate, visit:\n\n  " + deviceCode.verificationUri() + "\n");
        outputHandler.accept("Enter this code: " + deviceCode.userCode());
        outputHandler.accept("Waiting for authorization...");

        return deviceCodeFlow.pollForToken(config, deviceCode);
    }

    private OAuthFlowResult runROPCFlow(OAuthProviderConfig config,
                                          Consumer<String> outputHandler,
                                          java.util.function.Supplier<String> inputHandler)
            throws OAuthFlowException {
        outputHandler.accept("Username:");
        String username = inputHandler.get();
        if (username == null || username.isBlank()) {
            throw new OAuthFlowException("Username is required for password flow");
        }

        outputHandler.accept("Password:");
        String password = inputHandler.get();
        if (password == null || password.isBlank()) {
            throw new OAuthFlowException("Password is required for password flow");
        }

        return ropcFlow.requestToken(config, username.trim(), password.trim());
    }

    private String extractCodeFromRedirectUrl(String input, String expectedState) throws OAuthFlowException {
        if (input == null || input.isBlank()) {
            throw new OAuthFlowException("No redirect URL provided");
        }
        input = input.trim();

        // Parse query string from full URL or just query string
        String query;
        if (input.startsWith("http")) {
            int queryStart = input.indexOf('?');
            if (queryStart < 0) throw new OAuthFlowException("Redirect URL has no query parameters");
            query = input.substring(queryStart + 1);
        } else if (input.startsWith("?")) {
            query = input.substring(1);
        } else {
            query = input;
        }

        String code = null;
        String state = null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                if ("code".equals(kv[0])) code = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                if ("state".equals(kv[0])) state = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        if (state == null || !state.equals(expectedState)) {
            throw new OAuthFlowException("CSRF state mismatch in redirect URL");
        }
        if (code == null || code.isBlank()) {
            throw new OAuthFlowException("No authorization code in redirect URL");
        }
        return code;
    }
}
