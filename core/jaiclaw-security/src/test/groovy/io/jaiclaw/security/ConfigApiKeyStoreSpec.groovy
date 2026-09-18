package io.jaiclaw.security

import spock.lang.Specification

class ConfigApiKeyStoreSpec extends Specification {

    static final String KEY_A = "jaiclaw_ak_aaaa1234567890abcdef12345678"
    static final String KEY_B = "jaiclaw_ak_bbbb1234567890abcdef12345678"

    def store = new ConfigApiKeyStore([
            new ConfigApiKeyStore.ResolvedApiKey(KEY_A,
                    new ApiKeyStore.ApiKeyIdentity("acme-admin", "acme", "jaiclaw.admin")),
            new ConfigApiKeyStore.ResolvedApiKey(KEY_B,
                    new ApiKeyStore.ApiKeyIdentity("globex-ro", "globex", "jaiclaw.readonly")),
    ])

    def "resolves a known key to its tenant and role"() {
        expect:
        store.findByKey(KEY_A).get().tenantId() == "acme"
        store.findByKey(KEY_A).get().role() == "jaiclaw.admin"
        store.findByKey(KEY_B).get().tenantId() == "globex"
    }

    def "returns empty for unknown, null, and blank keys"() {
        expect:
        store.findByKey("jaiclaw_ak_unknown00000000000000000000").isEmpty()
        store.findByKey(null).isEmpty()
        store.findByKey("").isEmpty()
    }

    def "a near-miss key does not match"() {
        expect: "one character different"
        store.findByKey(KEY_A.substring(0, KEY_A.length() - 1) + "X").isEmpty()

        and: "a prefix of a valid key"
        store.findByKey(KEY_A.substring(0, 20)).isEmpty()
    }

    def "duplicate (tenant, role) pairs are allowed — this is how key rotation works"() {
        when: "an old and a new key share the same tenant and role during cutover"
        def rotating = new ConfigApiKeyStore([
                new ConfigApiKeyStore.ResolvedApiKey(KEY_A,
                        new ApiKeyStore.ApiKeyIdentity("acme-admin-2025", "acme", "jaiclaw.admin")),
                new ConfigApiKeyStore.ResolvedApiKey(KEY_B,
                        new ApiKeyStore.ApiKeyIdentity("acme-admin-2026", "acme", "jaiclaw.admin")),
        ])

        then: "both authenticate, so the cutover needs no downtime"
        rotating.findByKey(KEY_A).get().keyName() == "acme-admin-2025"
        rotating.findByKey(KEY_B).get().keyName() == "acme-admin-2026"
    }

    def "duplicate key MATERIAL is rejected — one entry would silently shadow the other"() {
        when:
        new ConfigApiKeyStore([
                new ConfigApiKeyStore.ResolvedApiKey(KEY_A,
                        new ApiKeyStore.ApiKeyIdentity("first", "acme", "jaiclaw.admin")),
                new ConfigApiKeyStore.ResolvedApiKey(KEY_A,
                        new ApiKeyStore.ApiKeyIdentity("second", "globex", "jaiclaw.readonly")),
        ])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Duplicate API key material")
    }

    def "size reports the configured key count"() {
        expect:
        store.size() == 2
        new ConfigApiKeyStore([]).size() == 0
        new ConfigApiKeyStore(null).size() == 0
    }
}
