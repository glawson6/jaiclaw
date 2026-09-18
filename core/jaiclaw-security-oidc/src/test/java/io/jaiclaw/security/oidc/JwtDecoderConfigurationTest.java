package io.jaiclaw.security.oidc;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises a real {@link NimbusJwtDecoder} against locally-signed tokens — no
 * live identity provider involved.
 *
 * <p>Written in Java rather than Spock: the decoder builder takes generic
 * functional parameters ({@code JWKSource<SecurityContext>},
 * {@code Consumer<ConfigurableJWTProcessor>}) that Groovy's closure coercion
 * does not reliably satisfy, which silently produced a differently-configured
 * decoder than the one under test.
 *
 * <p>These cover the three configuration details that each cost a day when
 * found during integration rather than before.
 */
class JwtDecoderConfigurationTest {

    private static final String ISSUER = "https://idp.example.com/oidc";
    private static final String AUDIENCE = "https://jaiclaw.example.com/api";

    @Test
    void es384SignedTokensValidate() throws Exception {
        // An EC secp384r1 keypair — the default for some providers, including Logto.
        ECKey ecKey = new ECKeyGenerator(Curve.P_384).keyID("k1").generate();
        NimbusJwtDecoder decoder = decoderFor(ecKey, SignatureAlgorithm.ES384, true);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES384)
                        .type(new JOSEObjectType("at+jwt")).keyID("k1").build(),
                claims());
        jwt.sign(new ECDSASigner(ecKey));

        // Spring defaults to RS256 and would reject this outright.
        Jwt decoded = decoder.decode(jwt.serialize());
        assertEquals("user-123", decoded.getSubject());
        assertEquals("acme", decoded.getClaimAsString("organization_id"));
    }

    @Test
    void atJwtTypeHeaderRequiresTheCustomVerifier() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("k1").generate();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("at+jwt")).keyID("k1").build(),
                claims());
        jwt.sign(new RSASSASigner(rsaKey));
        String token = jwt.serialize();

        // Without the verifier — Spring's default, accepting only `typ: JWT`.
        NimbusJwtDecoder strict = decoderFor(rsaKey, SignatureAlgorithm.RS256, false);
        assertThrows(JwtException.class, () -> strict.decode(token),
                "Spring's default type verifier must reject at+jwt — this is the "
                        + "failure that looks like a signature problem but is not");

        // With it — what JaiClawOidcAutoConfiguration builds.
        NimbusJwtDecoder lenient = decoderFor(rsaKey, SignatureAlgorithm.RS256, true);
        assertEquals("user-123", lenient.decode(token).getSubject());
    }

    @Test
    void hmacSignedTokenIsRefusedByAnAsymmetricDecoder() throws Exception {
        RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        NimbusJwtDecoder decoder = decoderFor(rsaKey, SignatureAlgorithm.RS256, true);

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims());
        jwt.sign(new MACSigner("01234567890123456789012345678901".getBytes()));

        // Algorithm confusion is the classic JWT attack; it must not be reachable.
        assertThrows(JwtException.class, () -> decoder.decode(jwt.serialize()));
    }

    @Test
    void tokenSignedByTheWrongKeyIsRefused() throws Exception {
        RSAKey realKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        NimbusJwtDecoder decoder = decoderFor(realKey, SignatureAlgorithm.RS256, true);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims());
        jwt.sign(new RSASSASigner(attackerKey));

        assertThrows(JwtException.class, () -> decoder.decode(jwt.serialize()));
    }

    /**
     * Builds a decoder the same way {@code JaiClawOidcAutoConfiguration} does.
     *
     * <p>The {@code jwtProcessorCustomizer} hook matters: {@code NimbusJwtDecoder}
     * installs its own {@code JWT}-only type verifier while building, so a
     * verifier set on the processor beforehand is silently overwritten. The
     * customizer runs afterwards and therefore wins.
     */
    private static NimbusJwtDecoder decoderFor(com.nimbusds.jose.jwk.JWK key,
                                               SignatureAlgorithm algorithm,
                                               boolean acceptAtJwt) throws Exception {
        ImmutableJWKSet<SecurityContext> source =
                new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK()));

        NimbusJwtDecoder.JwkSourceJwtDecoderBuilder builder =
                NimbusJwtDecoder.withJwkSource(source).jwsAlgorithm(algorithm);

        if (acceptAtJwt) {
            builder = builder.jwtProcessorCustomizer(processor ->
                    processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType("at+jwt"), JOSEObjectType.JWT)));
        }
        NimbusJwtDecoder decoder = builder.build();

        if (acceptAtJwt) {
            // Spring installs TWO independent type checks. The JOSE-level verifier
            // above is one; the other is a JwtTypeValidator in the default
            // validator chain, which also accepts only `JWT`. Replacing the chain
            // — exactly what JaiClawOidcAutoConfiguration does — is what actually
            // lets an RFC 9068 `at+jwt` token through. Configure only the first and
            // tokens still fail, with an error that names `typ` either way.
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new JwtIssuerValidator(ISSUER),
                    new AudienceValidator(AUDIENCE)));
        }
        return decoder;
    }

    private static JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder()
                .subject("user-123")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .claim("organization_id", "acme")
                .claim("scope", "openid jaiclaw:tools:coding")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
    }
}
