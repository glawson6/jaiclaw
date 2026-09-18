package io.jaiclaw.identity.provider;

import java.util.List;
import java.util.Optional;

/**
 * Reads user and organization data from an identity provider's admin API.
 *
 * <p>Provider-neutral by design. Every IdP exposes this information, but each
 * does so differently — Logto through its Management API, Keycloak through its
 * Admin REST API, Okta through its Users/Groups API. The linking ceremony and
 * everything built on it depend on this interface, not on any one of them.
 *
 * <p>This is <strong>not</strong> needed for the linking ceremony itself, which
 * runs entirely on standard OAuth. It is needed only for the things standard
 * OAuth does not cover: enumerating a user's organizations, or reconciling
 * state after a webhook. Deployments that need neither can omit the
 * implementation entirely — {@code ChannelLinkService} takes it as optional.
 *
 * <p>1.4.0.
 */
public interface IdentityProviderClient {

    /**
     * Look up a user by their provider subject ({@code sub}).
     *
     * @return the user, or empty when no such subject exists
     */
    Optional<ExternalUser> findUser(String subject);

    /**
     * The organizations a user belongs to, with the roles they hold in each.
     *
     * <p>Returns empty when the provider has no organization concept, rather
     * than throwing — callers should treat "no organizations" and "organizations
     * not supported" the same way.
     */
    List<OrganizationMembership> memberships(String subject);

    /** Identifies this implementation in logs and diagnostics. */
    String providerName();

    /**
     * A user as the identity provider sees them.
     *
     * @param subject      the provider's stable subject identifier ({@code sub})
     * @param username     login name, may be null
     * @param email        primary email, may be null
     * @param emailVerified whether the provider considers the email proven
     * @param displayName  human-readable name, may be null
     * @param suspended    whether the account is currently disabled
     */
    record ExternalUser(
            String subject,
            String username,
            String email,
            boolean emailVerified,
            String displayName,
            boolean suspended
    ) {}

    /**
     * A user's membership of one organization.
     *
     * @param organizationId provider-side organization identifier. Maps onto a
     *                       JaiClaw {@code tenantId}.
     * @param organizationName human-readable name, may be null
     * @param roles          roles held within that organization
     */
    record OrganizationMembership(
            String organizationId,
            String organizationName,
            List<String> roles
    ) {
        public OrganizationMembership {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
