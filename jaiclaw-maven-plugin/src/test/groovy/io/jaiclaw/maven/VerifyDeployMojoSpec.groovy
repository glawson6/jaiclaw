package io.jaiclaw.maven

import spock.lang.Specification

/**
 * Pure-logic specs for {@link VerifyDeployMojo}. The HTTP path is
 * exercised end-to-end via the plugin invocation itself; here we verify
 * the deterministic helpers.
 */
class VerifyDeployMojoSpec extends Specification {

    def mojo = new VerifyDeployMojo()

    def "ExpectedArtifact builds the correct folder + filename for a main JAR"() {
        given:
        def a = VerifyDeployMojo.ExpectedArtifact.mainJar(
                "io.jaiclaw", "jaiclaw-core", "1.0.0-SNAPSHOT", "jar")

        expect:
        a.folderPath() == "io/jaiclaw/jaiclaw-core/1.0.0-SNAPSHOT"
        a.filename() == "jaiclaw-core-1.0.0-SNAPSHOT.jar"
        a.remotePath() == "io/jaiclaw/jaiclaw-core/1.0.0-SNAPSHOT/jaiclaw-core-1.0.0-SNAPSHOT.jar"
    }

    def "ExpectedArtifact builds the correct filename for a classified artifact"() {
        given:
        def a = VerifyDeployMojo.ExpectedArtifact.classifier(
                "io.jaiclaw", "jaiclaw-cli", "1.0.0-SNAPSHOT", "exec", "jar")

        expect:
        a.filename() == "jaiclaw-cli-1.0.0-SNAPSHOT-exec.jar"
    }

    def "ExpectedArtifact builds the correct filename for a pom"() {
        given:
        def a = VerifyDeployMojo.ExpectedArtifact.pom(
                "io.jaiclaw", "jaiclaw-bom", "1.0.0-SNAPSHOT")

        expect:
        a.filename() == "jaiclaw-bom-1.0.0-SNAPSHOT.pom"
    }

    def "filenameFor substitutes the timestamped snapshot version"() {
        given:
        def a = VerifyDeployMojo.ExpectedArtifact.mainJar(
                "io.jaiclaw", "jaiclaw-core", "1.0.0-SNAPSHOT", "jar")

        expect:
        a.filenameFor("1.0.0-20260714.003211-1") ==
                "jaiclaw-core-1.0.0-20260714.003211-1.jar"
    }

    def "resolveSnapshotVersion picks the matching (extension, classifier) entry"() {
        given:
        def xml = '''
<metadata modelVersion="1.1.0">
  <groupId>io.jaiclaw</groupId>
  <artifactId>jaiclaw-cli</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <versioning>
    <snapshot>
      <timestamp>20260714.003211</timestamp>
      <buildNumber>1</buildNumber>
    </snapshot>
    <snapshotVersions>
      <snapshotVersion>
        <extension>pom</extension>
        <value>1.0.0-20260714.003211-1</value>
        <updated>20260714003211</updated>
      </snapshotVersion>
      <snapshotVersion>
        <extension>jar</extension>
        <value>1.0.0-20260714.003211-1</value>
        <updated>20260714003211</updated>
      </snapshotVersion>
      <snapshotVersion>
        <classifier>exec</classifier>
        <extension>jar</extension>
        <value>1.0.0-20260714.003211-1</value>
        <updated>20260714003211</updated>
      </snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
'''

        expect:
        mojo.resolveSnapshotVersion(xml, "1.0.0-SNAPSHOT", null, "jar") ==
                "1.0.0-20260714.003211-1"
        mojo.resolveSnapshotVersion(xml, "1.0.0-SNAPSHOT", "exec", "jar") ==
                "1.0.0-20260714.003211-1"
        mojo.resolveSnapshotVersion(xml, "1.0.0-SNAPSHOT", null, "pom") ==
                "1.0.0-20260714.003211-1"
    }

    def "resolveSnapshotVersion falls back to first value when no exact match"() {
        given:
        def xml = '''
<metadata>
  <versioning>
    <snapshotVersions>
      <snapshotVersion>
        <extension>jar</extension>
        <value>1.0.0-FALLBACK</value>
      </snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
'''

        when:
        // Ask for a classifier that doesn't exist — should fall back to the
        // top-level SNAP_VERSION match
        def resolved = mojo.resolveSnapshotVersion(xml, "1.0.0-SNAPSHOT", "sources", "jar")

        then:
        resolved == "1.0.0-FALLBACK"
    }

    def "packagingToExtension handles common cases"() {
        expect:
        mojo.packagingToExtension("jar") == "jar"
        mojo.packagingToExtension("maven-plugin") == "jar"
        mojo.packagingToExtension("bundle") == "jar"
        mojo.packagingToExtension("war") == "war"
        mojo.packagingToExtension("ear") == "ear"
        mojo.packagingToExtension("MAVEN-PLUGIN") == "jar"    // case-insensitive
    }

    def "isDeploySkipped honors maven.deploy.skip=true"() {
        given:
        def p = new org.apache.maven.project.MavenProject()
        p.getProperties().setProperty("maven.deploy.skip", value)

        expect:
        mojo.isDeploySkipped(p) == expected

        where:
        value       || expected
        "true"      || true
        "TRUE"      || true
        " true "    || true
        "false"     || false
        "no"        || false
        ""          || false
    }

    def "isDeploySkipped defaults to false when the property is absent"() {
        given:
        def p = new org.apache.maven.project.MavenProject()

        expect:
        !mojo.isDeploySkipped(p)
    }

    def "CheckResult factory methods build the expected shapes"() {
        given:
        def a = VerifyDeployMojo.ExpectedArtifact.mainJar(
                "io.jaiclaw", "x", "1.0.0-SNAPSHOT", "jar")

        expect:
        VerifyDeployMojo.CheckResult.ok(a, 12345L, true).status ==
                VerifyDeployMojo.CheckStatus.OK
        VerifyDeployMojo.CheckResult.ok(a, 12345L, false).status ==
                VerifyDeployMojo.CheckStatus.HASH_MISSING
        VerifyDeployMojo.CheckResult.missing(a, "http://x").status ==
                VerifyDeployMojo.CheckStatus.MISSING
        VerifyDeployMojo.CheckResult.error(a, "boom").status ==
                VerifyDeployMojo.CheckStatus.ERROR
    }
}
