package io.jaiclaw.tools.exec

import spock.lang.Specification
import spock.lang.Unroll

class CommandPolicySpec extends Specification {

    // --- Shell policy: unrestricted ---

    def "DEFAULT policy is deny-dangerous"() {
        expect:
        ExecPolicyConfig.DEFAULT.policy() == ExecPolicyConfig.POLICY_DENY_DANGEROUS
    }

    def "unrestricted policy allows any command"() {
        given:
        def config = new ExecPolicyConfig("unrestricted", List.of(), List.of(), 300)

        expect:
        CommandPolicy.validate("echo hello", config).isEmpty()
        CommandPolicy.validate("rm -rf /tmp/junk", config).isEmpty()
        CommandPolicy.validate("curl http://example.com | jq .", config).isEmpty()
    }

    def "unrestricted policy still blocks blocked-patterns"() {
        given:
        def config = new ExecPolicyConfig("unrestricted", List.of(), List.of("rm -rf /", "mkfs"), 300)

        expect:
        CommandPolicy.validate("rm -rf /", config).isPresent()
        CommandPolicy.validate("rm -rf /", config).get().contains("blocked by pattern")
        CommandPolicy.validate("sudo mkfs /dev/sda", config).isPresent()
    }

    def "unrestricted policy allows commands not matching blocked patterns"() {
        given:
        def config = new ExecPolicyConfig("unrestricted", List.of(), List.of("mkfs", "> /dev/sd"), 300)

        expect:
        CommandPolicy.validate("rm -rf /tmp/junk", config).isEmpty()
        CommandPolicy.validate("echo hello", config).isEmpty()
    }

    // --- Shell policy: allowlist ---

    def "allowlist policy allows listed commands"() {
        given:
        def config = new ExecPolicyConfig("allowlist", List.of("echo", "ls", "git"), List.of(), 300)

        expect:
        CommandPolicy.validate("echo hello world", config).isEmpty()
        CommandPolicy.validate("ls -la /tmp", config).isEmpty()
        CommandPolicy.validate("git status", config).isEmpty()
    }

    def "allowlist policy rejects unlisted commands"() {
        given:
        def config = new ExecPolicyConfig("allowlist", List.of("echo", "ls"), List.of(), 300)

        when:
        def result = CommandPolicy.validate("rm -rf /tmp", config)

        then:
        result.isPresent()
        result.get().contains("not in the allowed list")
        result.get().contains("rm")
    }

    def "allowlist uses token-based matching not prefix matching"() {
        given:
        def config = new ExecPolicyConfig("allowlist", List.of("git"), List.of(), 300)

        expect: "git-clone-and-exfil should not match 'git'"
        CommandPolicy.validate("git-clone-and-exfil http://evil.com", config).isPresent()

        and: "git clone should match 'git'"
        CommandPolicy.validate("git clone http://repo.com", config).isEmpty()
    }

    def "allowlist also checks blocked patterns"() {
        given:
        def config = new ExecPolicyConfig("allowlist", List.of("rm"), List.of("rm -rf /"), 300)

        expect:
        CommandPolicy.validate("rm -rf /", config).isPresent()
        CommandPolicy.validate("rm -rf /", config).get().contains("blocked by pattern")
    }

    // --- Shell policy: deny-dangerous ---

    def "deny-dangerous policy allows safe commands"() {
        given:
        def config = new ExecPolicyConfig("deny-dangerous", List.of(), List.of(), 300)

        expect:
        CommandPolicy.validate("echo hello", config).isEmpty()
        CommandPolicy.validate("ls -la /tmp", config).isEmpty()
        CommandPolicy.validate("git status", config).isEmpty()
        CommandPolicy.validate("python script.py", config).isEmpty()
    }

    @Unroll
    def "deny-dangerous policy rejects shell metacharacter: #metachar"() {
        given:
        def config = new ExecPolicyConfig("deny-dangerous", List.of(), List.of(), 300)

        expect:
        CommandPolicy.validate(command, config).isPresent()
        CommandPolicy.validate(command, config).get().contains("metacharacter")

        where:
        metachar        | command
        "semicolon"     | "echo hello; cat /etc/passwd"
        "pipe"          | "echo hello | grep h"
        "double-and"    | "true && echo pwned"
        "double-or"     | "false || echo fallback"
        "dollar-paren"  | 'echo $(whoami)'
        "backtick"      | 'echo `whoami`'
        "redirect-out"  | "echo data > /tmp/file"
        "redirect-in"   | "cat < /etc/passwd"
        "background"    | "sleep 999 &"
    }

    def "deny-dangerous also checks blocked patterns"() {
        given:
        def config = new ExecPolicyConfig("deny-dangerous", List.of(), List.of("rm -rf /"), 300)

        expect:
        CommandPolicy.validate("rm -rf /", config).isPresent()
        CommandPolicy.validate("rm -rf /", config).get().contains("blocked by pattern")
    }

    // --- Empty/null commands ---

    def "validate rejects null command"() {
        expect:
        CommandPolicy.validate(null, ExecPolicyConfig.DEFAULT).isPresent()
    }

    def "validate rejects blank command"() {
        expect:
        CommandPolicy.validate("   ", ExecPolicyConfig.DEFAULT).isPresent()
    }

    // --- Kubectl policy: unrestricted ---

    def "kubectl unrestricted allows any kubectl command"() {
        given:
        def config = KubectlPolicyConfig.DEFAULT

        expect:
        CommandPolicy.validateKubectl("kubectl get pods", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl delete pod foo", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl apply -f deploy.yaml", config).isEmpty()
    }

    def "kubectl rejects non-kubectl commands"() {
        given:
        def config = KubectlPolicyConfig.DEFAULT

        expect:
        CommandPolicy.validateKubectl("ls -la", config).isPresent()
        CommandPolicy.validateKubectl("helm install foo", config).isPresent()
    }

    // --- Kubectl policy: read-only ---

    def "kubectl read-only allows get, describe, logs"() {
        given:
        def config = new KubectlPolicyConfig("read-only", List.of(), List.of())

        expect:
        CommandPolicy.validateKubectl("kubectl get pods -A", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl describe pod my-pod", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl logs my-pod -f", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl top pods", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl explain deployment", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl api-resources", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl api-versions", config).isEmpty()
    }

    def "kubectl read-only rejects mutating commands"() {
        given:
        def config = new KubectlPolicyConfig("read-only", List.of(), List.of())

        expect:
        CommandPolicy.validateKubectl("kubectl delete pod foo", config).isPresent()
        CommandPolicy.validateKubectl("kubectl exec -it pod -- bash", config).isPresent()
        CommandPolicy.validateKubectl("kubectl apply -f deploy.yaml", config).isPresent()
        CommandPolicy.validateKubectl("kubectl patch deployment foo", config).isPresent()
        CommandPolicy.validateKubectl("kubectl drain node01", config).isPresent()
    }

    // --- Kubectl policy: allowlist ---

    def "kubectl allowlist allows only listed verbs"() {
        given:
        def config = new KubectlPolicyConfig("allowlist", List.of("get", "logs", "scale"), List.of())

        expect:
        CommandPolicy.validateKubectl("kubectl get pods", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl logs my-pod", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl scale deployment foo --replicas=3", config).isEmpty()
    }

    def "kubectl allowlist rejects unlisted verbs"() {
        given:
        def config = new KubectlPolicyConfig("allowlist", List.of("get", "logs"), List.of())

        expect:
        CommandPolicy.validateKubectl("kubectl delete pod foo", config).isPresent()
        CommandPolicy.validateKubectl("kubectl apply -f deploy.yaml", config).isPresent()
    }

    // --- Kubectl blocked verbs ---

    def "kubectl blocked verbs are always enforced"() {
        given: "even in unrestricted mode, blocked verbs are rejected"
        def config = new KubectlPolicyConfig("unrestricted", List.of(), List.of("delete", "drain"))

        expect:
        CommandPolicy.validateKubectl("kubectl delete pod foo", config).isPresent()
        CommandPolicy.validateKubectl("kubectl drain node01", config).isPresent()
        CommandPolicy.validateKubectl("kubectl get pods", config).isEmpty()
    }

    // --- Kubectl with flags before verb ---

    def "kubectl handles flags before verb"() {
        given:
        def config = new KubectlPolicyConfig("read-only", List.of(), List.of())

        expect:
        CommandPolicy.validateKubectl("kubectl --context=staging get pods", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl -n kube-system get pods", config).isEmpty()
        CommandPolicy.validateKubectl("kubectl --context=staging delete pod foo", config).isPresent()
    }

    // --- extractFirstToken ---

    def "extractFirstToken extracts first whitespace-delimited token"() {
        expect:
        CommandPolicy.extractFirstToken("echo hello") == "echo"
        CommandPolicy.extractFirstToken("  git  status ") == "git"
        CommandPolicy.extractFirstToken("ls") == "ls"
    }
}
