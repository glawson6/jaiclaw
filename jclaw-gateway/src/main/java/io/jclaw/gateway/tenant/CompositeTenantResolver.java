package io.jclaw.gateway.tenant;

import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tries multiple {@link TenantResolver} implementations in order until one succeeds.
 * If none resolve, returns empty — the caller should reject the request.
 */
public class CompositeTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(CompositeTenantResolver.class);

    private final List<TenantResolver> resolvers;

    public CompositeTenantResolver(List<TenantResolver> resolvers) {
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(TenantResolver::order))
                .toList();
        log.info("Composite tenant resolver initialized with {} resolvers", this.resolvers.size());
    }

    @Override
    public Optional<TenantContext> resolve(Map<String, String> attributes) {
        for (TenantResolver resolver : resolvers) {
            Optional<TenantContext> result = resolver.resolve(attributes);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
