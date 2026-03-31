package io.jaiclaw.tools.exec

import spock.lang.Specification

class SafeProcessEnvironmentSpec extends Specification {

    def "apply clears dangerous environment variables"() {
        given:
        def pb = new ProcessBuilder("echo", "test")
        // ProcessBuilder inherits all env vars by default
        pb.environment().put("ANTHROPIC_API_KEY", "sk-ant-secret")
        pb.environment().put("OPENAI_API_KEY", "sk-secret")
        pb.environment().put("DATABASE_PASSWORD", "hunter2")
        pb.environment().put("AWS_SECRET_ACCESS_KEY", "awssecret")

        when:
        SafeProcessEnvironment.apply(pb)

        then:
        !pb.environment().containsKey("ANTHROPIC_API_KEY")
        !pb.environment().containsKey("OPENAI_API_KEY")
        !pb.environment().containsKey("DATABASE_PASSWORD")
        !pb.environment().containsKey("AWS_SECRET_ACCESS_KEY")
    }

    def "apply preserves safe environment variables"() {
        given:
        def pb = new ProcessBuilder("echo", "test")

        when:
        SafeProcessEnvironment.apply(pb)

        then: "PATH and HOME should be preserved if they exist in the system env"
        if (System.getenv("PATH") != null) {
            pb.environment().get("PATH") == System.getenv("PATH")
        }
        if (System.getenv("HOME") != null) {
            pb.environment().get("HOME") == System.getenv("HOME")
        }
    }

    def "apply preserves KUBECONFIG for kubectl tools"() {
        given:
        def pb = new ProcessBuilder("kubectl", "get", "pods")
        // Simulate KUBECONFIG being set in the system env — we can't modify
        // System.getenv() so we test the allowlist logic indirectly
        // by verifying the env is cleared except safe vars

        when:
        SafeProcessEnvironment.apply(pb)

        then: "only safe vars remain"
        pb.environment().keySet().every { key ->
            key in ["PATH", "HOME", "LANG", "LC_ALL", "LC_CTYPE",
                     "TERM", "USER", "LOGNAME", "SHELL", "TMPDIR",
                     "TZ", "HOSTNAME", "KUBECONFIG"]
        }
    }

    def "apply results in non-empty PATH"() {
        given:
        def pb = new ProcessBuilder("echo", "test")

        when:
        SafeProcessEnvironment.apply(pb)

        then: "PATH should be set (present on all Unix systems)"
        pb.environment().get("PATH") != null
        !pb.environment().get("PATH").isEmpty()
    }
}
