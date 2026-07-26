package io.jaiclaw.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that every artifact the reactor produced was successfully deployed
 * to the configured remote repository.
 *
 * <p>Reads the reactor's {@code ${session.projects}} list (so it works from
 * the root aggregator), walks every non-{@code maven.deploy.skip=true}
 * project, computes the set of files Maven's {@code deploy} plugin would
 * have uploaded (main artifact + pom + attached classifiers when present),
 * and performs an HTTP HEAD against each expected URL on the target
 * repository. For SNAPSHOT versions the plugin first fetches
 * {@code maven-metadata.xml} to resolve the timestamped filename that the
 * remote actually stored.
 *
 * <p>Fails the build (by default) if any expected artifact is missing —
 * catching silent partial-deploy failures that Maven's own {@code deploy}
 * plugin doesn't always surface.
 *
 * <p>Works identically for release deploys: point at the release repo, set
 * {@code version} to the release version, and the SNAPSHOT-metadata path
 * is skipped in favor of a direct HEAD.
 *
 * <p>Usage from the root pom:
 * <pre>{@code
 * mvn deploy && mvn io.jaiclaw:jaiclaw-maven-plugin:verify-deploy \
 *   -Djaiclaw.verify.serverId=taptech-repo
 * }</pre>
 *
 * <p>Or wire into the {@code deploy} phase so it runs automatically as the
 * last step of every deploy.
 */
@Mojo(name = "verify-deploy", defaultPhase = LifecyclePhase.DEPLOY,
        threadSafe = true, aggregator = true)
public class VerifyDeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * Target repository URL. Defaults to the current project's snapshot
     * repository URL (when the current version is a SNAPSHOT) or its
     * release repository URL. Explicit override wins.
     */
    @Parameter(property = "jaiclaw.verify.repositoryUrl")
    private String repositoryUrl;

    /**
     * Maven settings.xml {@code <server>} id whose credentials should be
     * used for authenticated HEAD requests. Falls back to the current
     * project's {@code distributionManagement} server id.
     */
    @Parameter(property = "jaiclaw.verify.serverId")
    private String serverId;

    /**
     * Restrict verification to artifacts under this groupId (prefix match).
     * Defaults to the root project's groupId.
     */
    @Parameter(property = "jaiclaw.verify.groupId")
    private String groupId;

    /**
     * Restrict verification to this version. Defaults to the root project's
     * version. Set explicitly when verifying a release cut whose
     * {@code project.version} was updated after the deploy.
     */
    @Parameter(property = "jaiclaw.verify.version")
    private String version;

    /**
     * Fail the build if any expected artifact is missing on the remote.
     */
    @Parameter(property = "jaiclaw.verify.failOnMissing", defaultValue = "true")
    private boolean failOnMissing;

    /**
     * Skip verification entirely.
     */
    @Parameter(property = "jaiclaw.verify.skip", defaultValue = "false")
    private boolean skip;

    /**
     * HTTP request timeout in seconds (per HEAD / GET).
     */
    @Parameter(property = "jaiclaw.verify.timeoutSeconds", defaultValue = "30")
    private int timeoutSeconds;

    /**
     * Number of parallel HTTP checks. Kept modest so we don't hammer the
     * Nexus instance during a normal deploy verification.
     */
    @Parameter(property = "jaiclaw.verify.parallelism", defaultValue = "8")
    private int parallelism;

    /**
     * Also verify the {@code .sha1} + {@code .md5} sidecar hashes exist
     * (Nexus writes them automatically alongside every artifact).
     */
    @Parameter(property = "jaiclaw.verify.checkHashes", defaultValue = "true")
    private boolean checkHashes;

    // Matches the snapshot metadata's <snapshotVersion> block resolved
    // filename value — e.g. 1.0.0-20260714.003211-1
    private static final Pattern SNAP_VERSION =
            Pattern.compile("<value>([^<]+)</value>");

    // Matches the primary <version> line from a maven-metadata.xml — used
    // as a fallback when parsing <snapshotVersion> entries.
    private static final Pattern META_VERSION =
            Pattern.compile("<version>([^<]+)</version>");

    @Override
    public void execute() throws MojoFailureException {
        if (skip) {
            getLog().info("jaiclaw:verify-deploy skipped");
            return;
        }

        String rawUrl = resolveRepositoryUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new MojoFailureException("No repository URL configured. "
                    + "Set jaiclaw.verify.repositoryUrl or configure "
                    + "<distributionManagement> on the current project.");
        }
        final String effectiveUrl = rawUrl.endsWith("/") ? rawUrl : rawUrl + "/";

        String effectiveGroup = groupId != null && !groupId.isBlank()
                ? groupId
                : session.getTopLevelProject().getGroupId();
        String effectiveVersion = version != null && !version.isBlank()
                ? version
                : session.getTopLevelProject().getVersion();

        getLog().info("jaiclaw:verify-deploy");
        getLog().info("  repository: " + effectiveUrl);
        getLog().info("  groupId:    " + effectiveGroup);
        getLog().info("  version:    " + effectiveVersion);

        List<ExpectedArtifact> expected = collectExpectedArtifacts(
                effectiveGroup, effectiveVersion);
        if (expected.isEmpty()) {
            getLog().warn("No deployable artifacts found in reactor (all skipped?). "
                    + "Nothing to verify.");
            return;
        }
        getLog().info("  artifacts:  " + expected.size() + " expected (" +
                countExtension(expected, "jar") + " jars, " +
                countExtension(expected, "pom") + " poms)");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String authHeader = resolveAuthHeader();

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
        ConcurrentLinkedQueue<CheckResult> results = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>(expected.size());
        for (ExpectedArtifact a : expected) {
            futures.add(pool.submit(() -> {
                try {
                    results.add(verifyOne(http, effectiveUrl, authHeader, a));
                } catch (Exception e) {
                    results.add(CheckResult.error(a, e.toString()));
                }
            }));
        }
        pool.shutdown();
        for (Future<?> f : futures) {
            try {
                f.get(timeoutSeconds * 2L, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                getLog().warn("Verification task did not complete: " + e);
            }
        }
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Sort results deterministically for the report — by artifact path
        List<CheckResult> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> a.artifact.remotePath().compareTo(b.artifact.remotePath()));

        int ok = 0;
        int missing = 0;
        int hashOnly = 0;
        long bytesFound = 0;
        List<String> missingPaths = new ArrayList<>();
        for (CheckResult r : sorted) {
            if (r.status == CheckStatus.OK) {
                ok++;
                bytesFound += r.contentLength;
            } else if (r.status == CheckStatus.MISSING) {
                missing++;
                missingPaths.add(r.artifact.remotePath());
            } else if (r.status == CheckStatus.HASH_MISSING) {
                hashOnly++;
            }
        }

        getLog().info("");
        getLog().info("=== Verification result ===");
        getLog().info("  OK:               " + ok + " artifact(s)");
        if (checkHashes) {
            getLog().info("  hash sidecars OK: " + (ok - hashOnly) + " (missing " + hashOnly + ")");
        }
        getLog().info("  bytes on remote:  " + bytesFound + " (" + formatMb(bytesFound) + " MB)");
        if (missing > 0) {
            getLog().error("  MISSING:          " + missing + " artifact(s):");
            for (String path : missingPaths) {
                getLog().error("    " + path);
            }
        } else {
            getLog().info("  MISSING:          0");
        }

        if (missing > 0 && failOnMissing) {
            throw new MojoFailureException(
                    "Deploy verification failed: " + missing + " expected artifact(s) "
                            + "not found on " + effectiveUrl
                            + " (set jaiclaw.verify.failOnMissing=false to downgrade to WARN)");
        }
    }

    // --- Repository / auth resolution ---

    String resolveRepositoryUrl() {
        if (repositoryUrl != null && !repositoryUrl.isBlank()) {
            return repositoryUrl;
        }
        String v = session.getTopLevelProject().getVersion();
        boolean isSnapshot = v != null && v.endsWith("-SNAPSHOT");
        if (project.getDistributionManagement() != null) {
            if (isSnapshot && project.getDistributionManagement().getSnapshotRepository() != null) {
                return project.getDistributionManagement().getSnapshotRepository().getUrl();
            }
            if (!isSnapshot && project.getDistributionManagement().getRepository() != null) {
                return project.getDistributionManagement().getRepository().getUrl();
            }
        }
        return null;
    }

    String resolveAuthHeader() {
        String id = serverId;
        if ((id == null || id.isBlank()) && project.getDistributionManagement() != null) {
            if (project.getDistributionManagement().getSnapshotRepository() != null) {
                id = project.getDistributionManagement().getSnapshotRepository().getId();
            } else if (project.getDistributionManagement().getRepository() != null) {
                id = project.getDistributionManagement().getRepository().getId();
            }
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        Server server = settings.getServer(id);
        if (server == null || server.getUsername() == null) {
            getLog().warn("No <server> entry for id=" + id
                    + " in ~/.m2/settings.xml — verification will run anonymously");
            return null;
        }
        String user = server.getUsername();
        String pass = server.getPassword() == null ? "" : server.getPassword();
        String token = Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    // --- Expected-artifact collection ---

    List<ExpectedArtifact> collectExpectedArtifacts(String groupFilter, String versionFilter) {
        List<ExpectedArtifact> out = new ArrayList<>();
        for (MavenProject p : session.getProjects()) {
            if (isDeploySkipped(p)) {
                continue;
            }
            if (groupFilter != null && !p.getGroupId().equals(groupFilter)) {
                continue;
            }
            if (versionFilter != null && !p.getVersion().equals(versionFilter)) {
                continue;
            }

            String packaging = p.getPackaging() == null ? "jar" : p.getPackaging();
            // Every deploy publishes the .pom.
            out.add(ExpectedArtifact.pom(p.getGroupId(), p.getArtifactId(), p.getVersion()));

            // pom-packaged modules publish only the pom.
            if ("pom".equalsIgnoreCase(packaging)) {
                continue;
            }

            // Everything else publishes a main artifact of the packaging extension.
            String ext = packagingToExtension(packaging);
            out.add(ExpectedArtifact.mainJar(
                    p.getGroupId(), p.getArtifactId(), p.getVersion(), ext));

            // Attached artifacts (classifiers like -exec, -sources, -javadoc).
            // When this mojo runs as an aggregator, individual reactor
            // projects may not have populated getAttachedArtifacts() yet.
            // Union with a filesystem scan of the module's target/ dir to
            // catch classifiers Maven's programmatic API hasn't surfaced.
            java.util.Set<String> seenClassifiers = new java.util.LinkedHashSet<>();
            if (p.getAttachedArtifacts() != null) {
                for (Artifact a : p.getAttachedArtifacts()) {
                    if (a.getClassifier() == null || a.getClassifier().isBlank()) {
                        continue;
                    }
                    out.add(ExpectedArtifact.classifier(
                            p.getGroupId(), p.getArtifactId(), p.getVersion(),
                            a.getClassifier(), a.getType() == null ? ext : a.getType()));
                    seenClassifiers.add(a.getClassifier() + "." + (a.getType() == null ? ext : a.getType()));
                }
            }
            // Filesystem sweep: look for target/<artifactId>-<version>-<cls>.<ext>
            // files that weren't already recorded above. Catches Spring Boot
            // Maven Plugin's -exec repackage classifier during aggregator runs
            // where the reactor's programmatic attached-artifacts list is empty.
            java.io.File targetDir = p.getBuild() == null ? null
                    : new java.io.File(p.getBuild().getDirectory());
            if (targetDir != null && targetDir.isDirectory()) {
                String prefix = p.getArtifactId() + "-" + p.getVersion() + "-";
                java.io.File[] files = targetDir.listFiles((dir, name) ->
                        name.startsWith(prefix) && !name.endsWith("-original")
                                && !name.contains("-sources.")
                                && !name.contains("-javadoc."));
                if (files != null) {
                    for (java.io.File f : files) {
                        String name = f.getName();
                        // name = <artifactId>-<version>-<classifier>.<ext>
                        String tail = name.substring(prefix.length());
                        int dot = tail.lastIndexOf('.');
                        if (dot <= 0) continue;
                        String classifier = tail.substring(0, dot);
                        String fileExt = tail.substring(dot + 1);
                        // Only track jar/war/ear classifiers (skip .original,
                        // .pom.md5, etc.)
                        if (!"jar".equals(fileExt) && !"war".equals(fileExt) && !"ear".equals(fileExt)) {
                            continue;
                        }
                        String key = classifier + "." + fileExt;
                        if (seenClassifiers.contains(key)) continue;
                        out.add(ExpectedArtifact.classifier(
                                p.getGroupId(), p.getArtifactId(), p.getVersion(),
                                classifier, fileExt));
                        seenClassifiers.add(key);
                    }
                }
            }
        }
        return out;
    }

    boolean isDeploySkipped(MavenProject p) {
        String v = p.getProperties().getProperty("maven.deploy.skip");
        if (v == null) {
            return false;
        }
        return "true".equalsIgnoreCase(v.trim());
    }

    String packagingToExtension(String packaging) {
        // Handle a few common non-default packagings that map to a different
        // file extension.
        switch (packaging.toLowerCase(Locale.ROOT)) {
            case "maven-plugin":
            case "ejb":
            case "bundle":
                return "jar";
            case "war":
                return "war";
            case "ear":
                return "ear";
            case "rar":
                return "rar";
            default:
                return "jar";
        }
    }

    // --- HTTP verification ---

    CheckResult verifyOne(HttpClient http, String repoBaseUrl, String authHeader,
                          ExpectedArtifact a) throws IOException, InterruptedException {
        String versionFolder = a.version();
        String filename = a.filename();
        boolean isSnapshot = a.version().endsWith("-SNAPSHOT");

        if (isSnapshot) {
            // Fetch maven-metadata.xml for the version folder and resolve the
            // timestamped filename. Non-snapshot deploys skip this dance and
            // use the version verbatim.
            String metaUrl = repoBaseUrl + a.folderPath() + "/maven-metadata.xml";
            String meta = httpGet(http, metaUrl, authHeader);
            if (meta == null) {
                return CheckResult.missing(a, "maven-metadata.xml not found at " + metaUrl);
            }
            String resolved = resolveSnapshotVersion(meta, a.version(), a.classifier(), a.extension());
            if (resolved == null) {
                // Fall back to the -SNAPSHOT literal (some Nexus policies
                // preserve non-timestamped snapshot filenames).
                resolved = a.version();
            }
            filename = a.filenameFor(resolved);
        }

        String url = repoBaseUrl + a.folderPath() + "/" + filename;
        long length = httpHead(http, url, authHeader);
        if (length < 0) {
            return CheckResult.missing(a, url);
        }
        boolean hashOk = true;
        if (checkHashes) {
            hashOk = httpHead(http, url + ".sha1", authHeader) >= 0
                    && httpHead(http, url + ".md5", authHeader) >= 0;
        }
        return CheckResult.ok(a, length, hashOk);
    }

    String resolveSnapshotVersion(String metadataXml, String baseVersion,
                                  String classifier, String extension) {
        // The metadata block has one <snapshotVersion> per (extension, classifier)
        // pair. We pick the one matching our target — cheap textual parse
        // rather than pulling in a full XML dependency.
        //
        // Fallback: use the first <snapshotVersion><value>...</value>.
        String targetSuffix = classifier == null || classifier.isBlank()
                ? "." + extension
                : "-" + classifier + "." + extension;
        // Split into <snapshotVersion> blocks and find one whose <value> ends
        // with our expected suffix (implicitly via matching extension/classifier).
        String[] blocks = metadataXml.split("<snapshotVersion>");
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            String ext = extract(block, "<extension>", "</extension>");
            String cls = extract(block, "<classifier>", "</classifier>");
            if (!extension.equals(ext)) continue;
            if (classifier == null || classifier.isBlank()) {
                if (cls != null && !cls.isBlank()) continue;
            } else {
                if (!classifier.equals(cls)) continue;
            }
            String value = extract(block, "<value>", "</value>");
            if (value != null) return value;
        }
        // Fallback — any snapshotVersion value at all
        Matcher m = SNAP_VERSION.matcher(metadataXml);
        if (m.find()) return m.group(1);
        // Last resort — top-level <version>
        m = META_VERSION.matcher(metadataXml);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extract(String s, String open, String close) {
        int a = s.indexOf(open);
        if (a < 0) return null;
        int b = s.indexOf(close, a + open.length());
        if (b < 0) return null;
        return s.substring(a + open.length(), b).trim();
    }

    /**
     * Returns Content-Length when the HEAD returns 2xx, or -1 on 404 /
     * network failure.
     */
    long httpHead(HttpClient http, String url, String authHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(timeoutSeconds));
        if (authHeader != null) req.header("Authorization", authHeader);
        HttpResponse<Void> resp = http.send(req.build(), HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() / 100 == 2) {
            return resp.headers().firstValueAsLong("content-length").orElse(0L);
        }
        return -1;
    }

    /**
     * Returns the response body as UTF-8, or null on 404.
     */
    String httpGet(HttpClient http, String url, String authHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(timeoutSeconds));
        if (authHeader != null) req.header("Authorization", authHeader);
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 == 2) {
            return resp.body();
        }
        return null;
    }

    // --- helpers ---

    private String formatMb(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1_048_576.0);
    }

    private int countExtension(List<ExpectedArtifact> list, String ext) {
        int n = 0;
        for (ExpectedArtifact a : list) {
            if (ext.equals(a.extension())) n++;
        }
        return n;
    }

    // --- Value types ---

    /**
     * One artifact we expect to find on the remote repository.
     */
    record ExpectedArtifact(String groupId, String artifactId, String version,
                            String classifier, String extension) {

        static ExpectedArtifact pom(String g, String a, String v) {
            return new ExpectedArtifact(g, a, v, null, "pom");
        }

        static ExpectedArtifact mainJar(String g, String a, String v, String ext) {
            return new ExpectedArtifact(g, a, v, null, ext);
        }

        static ExpectedArtifact classifier(String g, String a, String v,
                                           String classifier, String ext) {
            return new ExpectedArtifact(g, a, v, classifier, ext);
        }

        String folderPath() {
            return groupId.replace('.', '/') + "/" + artifactId + "/" + version;
        }

        String remotePath() {
            return folderPath() + "/" + filename();
        }

        /**
         * Filename for the non-snapshot case (version literal).
         */
        String filename() {
            return filenameFor(version);
        }

        /**
         * Filename with an explicit version (used to plug in the timestamped
         * snapshot version resolved from maven-metadata.xml).
         */
        String filenameFor(String versionValue) {
            StringBuilder sb = new StringBuilder(artifactId).append('-').append(versionValue);
            if (classifier != null && !classifier.isBlank()) {
                sb.append('-').append(classifier);
            }
            sb.append('.').append(extension);
            return sb.toString();
        }
    }

    enum CheckStatus { OK, MISSING, HASH_MISSING, ERROR }

    record CheckResult(ExpectedArtifact artifact, CheckStatus status,
                       long contentLength, String detail) {
        static CheckResult ok(ExpectedArtifact a, long length, boolean hashOk) {
            return new CheckResult(a,
                    hashOk ? CheckStatus.OK : CheckStatus.HASH_MISSING,
                    length, null);
        }
        static CheckResult missing(ExpectedArtifact a, String url) {
            return new CheckResult(a, CheckStatus.MISSING, 0, url);
        }
        static CheckResult error(ExpectedArtifact a, String message) {
            return new CheckResult(a, CheckStatus.ERROR, 0, message);
        }
    }
}
