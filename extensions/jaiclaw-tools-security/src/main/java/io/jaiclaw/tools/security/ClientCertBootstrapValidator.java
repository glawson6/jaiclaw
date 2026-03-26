package io.jaiclaw.tools.security;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap validator for {@link BootstrapTrust#CLIENT_CERT} level.
 * Verifies that the client has signed its nonce with a key that is
 * in the server's registry of allowed client public keys.
 */
public class ClientCertBootstrapValidator implements BootstrapValidator {

    private static final String PARAM_CLIENT_SIGNATURE = "clientSignature";
    private static final String PARAM_CLIENT_NONCE = "clientNonce";
    private static final String PARAM_CLIENT_PUBLIC_KEY = "clientPublicKey";

    private final CryptoService cryptoService;

    public ClientCertBootstrapValidator(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public boolean validate(Map<String, Object> negotiateParams, SecurityHandshakeProperties properties) {
        List<String> allowedKeys = properties.allowedClientKeys();
        if (allowedKeys == null || allowedKeys.isEmpty()) {
            return false;
        }

        String clientPublicKeyEncoded = (String) negotiateParams.get(PARAM_CLIENT_PUBLIC_KEY);
        String clientSignature = (String) negotiateParams.get(PARAM_CLIENT_SIGNATURE);
        String clientNonce = (String) negotiateParams.get(PARAM_CLIENT_NONCE);

        if (clientPublicKeyEncoded == null || clientSignature == null || clientNonce == null) {
            return false;
        }

        // Check if the client's public key is in the allowed list
        if (!allowedKeys.contains(clientPublicKeyEncoded)) {
            return false;
        }

        // Verify the signature over the client nonce using the client's public key
        try {
            PublicKey publicKey = cryptoService.decodePublicKey(clientPublicKeyEncoded, "EC");
            byte[] signatureBytes = Base64.getUrlDecoder().decode(clientSignature);
            byte[] nonceBytes = clientNonce.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(nonceBytes);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String failureReason() {
        return "Client public key not registered or signature verification failed";
    }
}
