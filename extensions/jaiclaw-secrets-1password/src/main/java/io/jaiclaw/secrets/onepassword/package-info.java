/**
 * 1Password-backed {@link io.jaiclaw.core.secrets.SecretsProvider}
 * implementation.
 *
 * <p>Backs secret lookups via the {@code op} command-line tool. The
 * provider shells out to {@code op read op://<vault>/<item>/<field>}
 * for each lookup. Authentication is supplied via the
 * {@code OP_SERVICE_ACCOUNT_TOKEN} environment variable when running
 * non-interactively (CI, K8s nodes), or by the user's signed-in
 * {@code op} session locally.
 *
 * <p>0.9.2 secrets baseline.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.secrets.onepassword;
