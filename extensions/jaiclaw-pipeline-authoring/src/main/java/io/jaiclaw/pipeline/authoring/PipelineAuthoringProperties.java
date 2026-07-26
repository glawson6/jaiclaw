package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.pipeline.PipelineSecurityProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;

/**
 * Configuration for the Pipeline Studio authoring plane. Bound under
 * {@code jaiclaw.pipeline.authoring}.
 *
 * @param enabled     master on/off switch. Defaults to {@code true} but
 *                    the auto-config gates on the property being
 *                    explicitly {@code true}, so leaving it unset means
 *                    the authoring endpoints are NOT exposed.
 * @param storagePath filesystem path for the {@link JsonFilePipelineDraftStore}.
 *                    Defaults to {@code ~/.jaiclaw/pipeline-drafts/}. Ignored
 *                    when an adopter supplies their own {@link PipelineDraftStore}
 *                    bean.
 * @param hotReload   hot-deploy tuning (Phase 3 addition).
 * @param security    UI-origin authorization + URI-scheme allowlist
 *                    (Phase 3 addition).
 * @param roles       Spring Security authority names mapped onto the four
 *                    Studio roles. Blank values disable enforcement for
 *                    that role (endpoint stays reachable to authenticated
 *                    principals).
 */
@ConfigurationProperties(prefix = "jaiclaw.pipeline.authoring")
public record PipelineAuthoringProperties(
        boolean enabled,
        Path storagePath,
        @NestedConfigurationProperty HotReload hotReload,
        @NestedConfigurationProperty Security security,
        @NestedConfigurationProperty Roles roles,
        @NestedConfigurationProperty Mcp mcp
) {
    public PipelineAuthoringProperties {
        if (storagePath == null) {
            String home = System.getProperty("user.home", ".");
            storagePath = Paths.get(home, ".jaiclaw", "pipeline-drafts");
        }
        if (hotReload == null) hotReload = HotReload.DEFAULT;
        if (security == null) security = Security.DEFAULT;
        if (roles == null) roles = Roles.DEFAULT;
        if (mcp == null) mcp = Mcp.DEFAULT;
    }

    /**
     * @param drainTimeout bounded per-route stop wait during undeploy
     *                     (see PIPELINE_HOT_RELOAD.md § 4). Default 10s.
     */
    public record HotReload(Duration drainTimeout) {
        public static final HotReload DEFAULT = new HotReload(Duration.ofSeconds(10));

        public HotReload {
            if (drainTimeout == null || drainTimeout.isNegative() || drainTimeout.isZero()) {
                drainTimeout = Duration.ofSeconds(10);
            }
        }
    }

    /**
     * @param allowedUriSchemes UI-origin allowlist for Camel URIs. Defaults
     *                          to {@link PipelineSecurityProperties#DEFAULT_ALLOWED_URI_SCHEMES}.
     *                          Adopters extend as needed (e.g. add {@code smtp} + {@code kafka}
     *                          in an internal-only deployment).
     */
    public record Security(Set<String> allowedUriSchemes) {
        public static final Security DEFAULT =
                new Security(PipelineSecurityProperties.DEFAULT_ALLOWED_URI_SCHEMES);

        public Security {
            if (allowedUriSchemes == null || allowedUriSchemes.isEmpty()) {
                allowedUriSchemes = PipelineSecurityProperties.DEFAULT_ALLOWED_URI_SCHEMES;
            } else {
                allowedUriSchemes = Set.copyOf(allowedUriSchemes);
            }
        }
    }

    /**
     * Spring Security authority names mapped onto the four Studio roles.
     * Absent authority means no method-level enforcement — the endpoint
     * still requires an authenticated principal (per the app's chain)
     * but any authenticated caller may invoke it.
     */
    public record Roles(String viewer, String author, String deployer, String runner) {
        public static final Roles DEFAULT = new Roles(
                "ROLE_PIPELINE_VIEWER",
                "ROLE_PIPELINE_AUTHOR",
                "ROLE_PIPELINE_DEPLOYER",
                "ROLE_PIPELINE_RUNNER");
    }

    /**
     * MCP tool exposure. {@code pipeline_validate} always ships; the
     * write-path {@code pipeline_deploy} tool ships only when
     * {@code deployEnabled=true}.
     */
    public record Mcp(boolean deployEnabled) {
        public static final Mcp DEFAULT = new Mcp(false);
    }
}
