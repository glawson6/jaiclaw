package io.jaiclaw.examples.oauthprovider;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO(spring-ai-2.0-migration): The bean-wiring body of this @Configuration
 * class is quarantined during the Boot 4 / Spring AI 2.0 upgrade.
 *
 * <p>The original config wired the jaiclaw-identity OAuth infrastructure and
 * built a {@link org.springframework.ai.openai.OpenAiChatModel} using
 * {@code OpenAiApi.builder().apiKey(oauthAccessToken).build()}. Spring AI 2.0
 * removed the {@code org.springframework.ai.openai.api.OpenAiApi} class
 * entirely — the OpenAI path now runs on the official {@code openai-java}
 * SDK ({@code com.openai.client.OpenAIClient}) built via
 * {@code OpenAIOkHttpClient.builder().apiKey(...).build()} and wired into
 * {@code OpenAiChatModel.builder().openAiClient(client)}.
 *
 * <p>Full re-implementation deferred to a post-1.0 example refresh — needs
 * an {@code openai-java-client-okhttp} dep, a rewrite of the OAuth-token
 * → OpenAIClient path, and re-verification of the OAuth login flow.
 * See release-1.0.0.md § Known follow-ups and
 * docs/spring-boot-4-upgrade/05-spring-ai-2-migration.md.
 *
 * <p>Path constants retained so the sibling {@code OAuthLoginController}
 * still compiles.
 */
public class OAuthProviderDemoConfig {

    static final Path STATE_DIR = Paths.get(
            System.getProperty("user.home"), ".jaiclaw");
    static final Path AGENT_DIR = STATE_DIR.resolve("agents/default/agent");

    // Bean wiring removed during Spring Boot 4 / Spring AI 2.0 upgrade — see class javadoc.
}
