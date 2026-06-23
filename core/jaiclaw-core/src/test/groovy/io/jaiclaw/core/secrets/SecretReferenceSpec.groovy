package io.jaiclaw.core.secrets

import spock.lang.Specification
import spock.lang.Unroll

class SecretReferenceSpec extends Specification {

    @Unroll
    def "parses full form: #ref"() {
        when:
        SecretReference parsed = SecretReference.parse(ref)

        then:
        parsed.provider() == provider
        parsed.vault() == vault
        parsed.item() == item
        parsed.field() == field

        where:
        ref                                       | provider      | vault    | item     | field
        "onepassword://JaiClaw/anthropic/api-key" | "onepassword" | "JaiClaw"| "anthropic" | "api-key"
        "env://ANTHROPIC_API_KEY"                 | "env"         | null     | "ANTHROPIC_API_KEY" | null
        "file://anthropic/api-key"                | "file"        | null     | "anthropic" | "api-key"
        "file://prod/anthropic/api-key"           | "file"        | "prod"   | "anthropic" | "api-key"
    }

    def "round-trips through asString"() {
        given:
        SecretReference original = SecretReference.parse(ref)

        expect:
        SecretReference.parse(original.asString()) == original

        where:
        ref << [
                "onepassword://JaiClaw/anthropic/api-key",
                "env://ANTHROPIC_API_KEY",
                "file://anthropic/api-key",
        ]
    }

    @Unroll
    def "rejects malformed input: '#bad'"() {
        when:
        SecretReference.parse(bad)

        then:
        thrown(IllegalArgumentException)

        where:
        bad << ["", "no-scheme", "scheme://", "provider://a/b/c/d/e"]
    }

    def "constructor rejects blank provider or item"() {
        when:
        new SecretReference(prov, null, item, null)

        then:
        thrown(IllegalArgumentException)

        where:
        prov | item
        ""   | "foo"
        " "  | "foo"
        "p"  | ""
        "p"  | " "
    }
}
