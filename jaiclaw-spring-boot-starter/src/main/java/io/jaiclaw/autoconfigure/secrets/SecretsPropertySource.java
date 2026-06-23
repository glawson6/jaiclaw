package io.jaiclaw.autoconfigure.secrets;

import io.jaiclaw.core.secrets.SecretResolution;
import io.jaiclaw.core.secrets.SecretsResolver;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Spring {@link org.springframework.core.env.PropertySource} that
 * bridges into a {@link SecretsResolver}.
 *
 * <p>Registered by {@link SecretsEnvironmentPostProcessor} early enough
 * to intercept {@code ${VAR}} placeholder resolution in
 * {@code application.yml}. Misses fall through to the next source via
 * Spring's standard PropertySources chain.
 *
 * <p>Not enumerable in the bulk sense — {@link #getPropertyNames()}
 * returns an empty array because secret-store-backed providers can't
 * efficiently enumerate. This matches Spring's expectation that
 * placeholder resolution always uses point lookups via {@link
 * #getProperty(String)}.
 *
 * <p>0.9.2 secrets baseline.
 */
final class SecretsPropertySource extends EnumerablePropertySource<SecretsResolver> {

    static final String NAME = "jaiclawSecrets";

    SecretsPropertySource(SecretsResolver resolver) {
        super(NAME, resolver);
    }

    @Override
    public String[] getPropertyNames() {
        // Returning empty here is intentional — Spring's
        // PlaceholderResolver only iterates names for relaxed binding
        // discovery, not for the placeholder lookup itself. Providers
        // backed by secret stores can't enumerate efficiently.
        return new String[0];
    }

    @Override
    @Nullable
    public Object getProperty(String name) {
        SecretResolution res = source.resolve(name);
        return res instanceof SecretResolution.Resolved r ? r.value() : null;
    }
}
