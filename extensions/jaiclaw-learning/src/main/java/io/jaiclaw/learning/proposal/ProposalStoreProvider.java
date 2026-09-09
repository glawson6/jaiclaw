package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

/**
 * Factory SPI so adopters can supply a JDBC- or Redis-backed
 * {@link ProposalStore} in place of the bundled JSON one without touching the
 * rest of the module.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public interface ProposalStoreProvider {

    /** The store to use. Called once at startup. */
    ProposalStore get();

    /** Short id for logs, e.g. {@code json-file}. */
    String name();
}
