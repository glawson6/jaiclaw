package io.jaiclaw.copilot;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.GetAuthStatusResponse;
import com.github.copilot.rpc.ModelInfo;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around {@link CopilotClient} that owns the client's lifecycle
 * and exposes the two operations {@link CopilotChatModel} actually uses:
 * create a session, list entitled models.
 *
 * <p>The wrapper exists for three reasons:
 *
 * <ol>
 *   <li><b>Startability.</b> {@link CopilotClient#start()} returns a
 *       {@code CompletableFuture} — the auto-configuration blocks on it so
 *       the bean's post-construct hook doesn't race the first
 *       {@code createSession()} call.</li>
 *   <li><b>Testability.</b> {@link CopilotChatModel} depends on this
 *       interface (not the concrete {@code CopilotClient}) so specs can stub
 *       it with Spock's interface-based mocks — Copilot's client is a
 *       {@code final} class, awkward to mock even with byte-buddy.</li>
 *   <li><b>Ownership.</b> The wrapper implements {@link AutoCloseable} so
 *       Spring's bean lifecycle takes care of shutting the client down when
 *       the context closes; the client's own {@code AutoCloseable} would
 *       otherwise be called via the destroy method on a bare bean.</li>
 * </ol>
 *
 * <p>This is a class, not an interface — the wrapper is small and the
 * {@code CopilotClient} field is genuinely useful for one-off calls
 * ({@code ping}, {@code getAuthStatus}) that don't warrant additional
 * abstraction. Tests use a subclass override or a concrete stub.
 */
public class CopilotApi implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CopilotApi.class);

    private final CopilotClient client;

    /**
     * Builds an API wrapper around a newly-constructed {@link CopilotClient}.
     * Convenience for the default auto-config path.
     */
    public CopilotApi() {
        this(new CopilotClient());
    }

    /**
     * Builds an API wrapper around a {@link CopilotClient} constructed with the
     * given {@link CopilotClientOptions} (e.g. custom {@code cliPath},
     * {@code githubToken}, {@code copilotHome}).
     */
    public CopilotApi(CopilotClientOptions options) {
        this(new CopilotClient(options));
    }

    /**
     * Primary constructor — takes a pre-built client. Tests use this to
     * inject a mock.
     */
    public CopilotApi(CopilotClient client) {
        this.client = client;
    }

    /**
     * Starts the underlying client synchronously (blocks on the returned
     * future). Idempotent under the SDK's contract; safe to call from an
     * {@code @PostConstruct} hook.
     */
    public void start() {
        try {
            client.start().get();
            log.debug("Copilot client started");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Copilot client: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new session under the given config. Blocks on the SDK's
     * async result. The caller is responsible for closing the returned
     * session (or letting a session pool own its lifecycle).
     */
    public CopilotSession createSession(SessionConfig config) {
        try {
            return client.createSession(config).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Copilot session: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the list of models the current auth is entitled to. Used at
     * startup to log the available matrix and optionally warn on a
     * configured-but-unentitled default model.
     */
    public List<ModelInfo> listModels() {
        try {
            return client.listModels().get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Copilot models: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the current auth resolution — is-authenticated flag, auth
     * type ({@code oauth} / {@code pat}), GitHub host, login, and a
     * human-readable status message. Used by the startup probe so operators
     * see which GitHub identity + host their configuration resolved to.
     */
    public GetAuthStatusResponse getAuthStatus() {
        try {
            return client.getAuthStatus().get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Copilot auth status: " + e.getMessage(), e);
        }
    }

    /**
     * Async variant — returns the SDK's raw future so callers who want
     * non-blocking startup can await it themselves.
     */
    public CompletableFuture<List<ModelInfo>> listModelsAsync() {
        return client.listModels();
    }

    /**
     * Convenience factory for the "approve everything" permission handler —
     * matches the pattern from the SDK's README quickstart. Non-interactive
     * usage requires this; interactive shells may want to wire something
     * that prompts the operator.
     */
    public static PermissionHandler approveAllPermissions() {
        return PermissionHandler.APPROVE_ALL;
    }

    /**
     * Returns the underlying client for callers that need SDK-specific
     * features not exposed here (e.g. {@code resumeSession}, {@code ping}).
     * Prefer adding a method to this class over reaching for the underlying
     * client — every direct usage undermines the testability that this
     * wrapper exists to provide.
     */
    public CopilotClient underlying() {
        return client;
    }

    @Override
    public void close() {
        try {
            client.close();
            log.debug("Copilot client closed");
        } catch (Exception e) {
            log.warn("Error closing Copilot client: {}", e.toString());
        }
    }
}
