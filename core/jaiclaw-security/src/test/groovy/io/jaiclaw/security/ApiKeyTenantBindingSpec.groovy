package io.jaiclaw.security

import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.core.tool.ToolProfileHolder
import io.jaiclaw.security.authn.JaiClawAuthentication
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

/**
 * The one-key/one-tenant/one-role contract, and the 401-vs-403 distinction.
 *
 * <p>Before 1.2.0 none of this existed: the filter was constructed with a null
 * TenantGuard, so its multi-tenant branch was dead code and nothing established
 * a tenant in api-key mode.
 */
class ApiKeyTenantBindingSpec extends Specification {

    static final String ACME_KEY = "jaiclaw_ak_acme1234567890abcdef1234567"
    static final String NOTENANT_KEY = "jaiclaw_ak_notenant7890abcdef12345678"

    ApiKeyStore store = new ConfigApiKeyStore([
            new ConfigApiKeyStore.ResolvedApiKey(ACME_KEY,
                    new ApiKeyStore.ApiKeyIdentity("acme-admin", "acme", "jaiclaw.admin")),
            new ConfigApiKeyStore.ResolvedApiKey(NOTENANT_KEY,
                    new ApiKeyStore.ApiKeyIdentity("no-tenant", null, "jaiclaw.user")),
    ])

    TenantGuard multiTenant = new TenantGuard(
            new TenantProperties(TenantMode.MULTI, "acme", false))

    ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
            null, store, multiTenant, true, null, "X-Tenant-Id")

    FilterChain chain = Mock(FilterChain)

    def cleanup() {
        SecurityContextHolder.clearContext()
        TenantContextHolder.clear()
        ToolProfileHolder.clear()
    }

    def "matching tenant authenticates and grants the key's single role"() {
        given:
        def request = mockRequest("/api/chat", ACME_KEY, "acme")
        def response = Mock(HttpServletResponse)
        JaiClawAuthentication authed = null
        chain.doFilter(_, _) >> {
            authed = SecurityContextHolder.getContext().getAuthentication() as JaiClawAuthentication
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        authed != null
        authed.principal == "acme-admin"
        authed.source() == JaiClawAuthentication.AuthSource.API_KEY
        authed.tenantContext().get().getTenantId() == "acme"
        authed.authorities*.authority == ["jaiclaw.admin"]
    }

    def "mismatched tenant is 403, not 401 — the key authenticated, it is just not authorised there"() {
        given:
        def request = mockRequest("/api/chat", ACME_KEY, "globex")
        def writer = new PrintWriter(new StringWriter())
        def response = Mock(HttpServletResponse) { getWriter() >> writer }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * chain.doFilter(_, _)
        SecurityContextHolder.getContext().getAuthentication() == null
    }

    def "missing tenant header is 401 with a challenge — the credential is incomplete"() {
        given:
        def request = mockRequest("/api/chat", ACME_KEY, null)
        def writer = new PrintWriter(new StringWriter())
        def response = Mock(HttpServletResponse) { getWriter() >> writer }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        1 * response.setHeader("WWW-Authenticate", _)
        0 * chain.doFilter(_, _)
    }

    def "unknown key is 401 even when the tenant header is valid"() {
        given:
        def request = mockRequest("/api/chat", "jaiclaw_ak_bogus000000000000000000000", "acme")
        def writer = new PrintWriter(new StringWriter())
        def response = Mock(HttpServletResponse) { getWriter() >> writer }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        0 * chain.doFilter(_, _)
    }

    def "key with no tenant association is 401 in multi-tenant mode"() {
        given:
        def request = mockRequest("/api/chat", NOTENANT_KEY, "acme")
        def writer = new PrintWriter(new StringWriter())
        def response = Mock(HttpServletResponse) { getWriter() >> writer }

        when:
        filter.doFilterInternal(request, response, chain)

        then:
        1 * response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        0 * chain.doFilter(_, _)
    }

    def "an Authorization header cannot influence the resolved tenant"() {
        given: "a forged unsigned JWT claiming another tenant, alongside a valid key"
        def forged = "e30." + Base64.urlEncoder.withoutPadding()
                .encodeToString('{"tenantId":"globex"}'.bytes) + ".x"
        def request = Mock(HttpServletRequest) {
            getRequestURI() >> "/api/chat"
            getHeader("X-API-Key") >> ACME_KEY
            getHeader("X-Tenant-Id") >> "acme"
            getHeader("Authorization") >> "Bearer " + forged
            getParameter("api_key") >> null
        }
        def response = Mock(HttpServletResponse)
        JaiClawAuthentication authed = null
        chain.doFilter(_, _) >> {
            authed = SecurityContextHolder.getContext().getAuthentication() as JaiClawAuthentication
        }

        when:
        filter.doFilterInternal(request, response, chain)

        then: "the key's binding wins; the forged claim is never consulted"
        authed.tenantContext().get().getTenantId() == "acme"
    }

    def "single-tenant mode needs no header and grants no authorities"() {
        given:
        def singleTenant = new ApiKeyAuthenticationFilter(
                null, store, new TenantGuard(TenantProperties.DEFAULT), true, null, "X-Tenant-Id")
        def request = mockRequest("/api/chat", ACME_KEY, null)
        def response = Mock(HttpServletResponse)
        JaiClawAuthentication authed = null
        chain.doFilter(_, _) >> {
            authed = SecurityContextHolder.getContext().getAuthentication() as JaiClawAuthentication
        }

        when:
        singleTenant.doFilterInternal(request, response, chain)

        then: "authenticated, but roles are meaningless without a tenant — as before 1.2.0"
        authed != null
        authed.authorities.isEmpty()
        authed.tenantContext().isEmpty()
    }

    private HttpServletRequest mockRequest(String uri, String apiKey, String tenantId) {
        Mock(HttpServletRequest) {
            getRequestURI() >> uri
            getHeader("X-API-Key") >> apiKey
            getHeader("X-Tenant-Id") >> tenantId
            getParameter("api_key") >> null
        }
    }
}
