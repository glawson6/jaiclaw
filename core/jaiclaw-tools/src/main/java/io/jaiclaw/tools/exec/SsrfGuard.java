package io.jaiclaw.tools.exec;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * SSRF (Server-Side Request Forgery) protection utility.
 * Validates URLs to block requests to private/internal network ranges.
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    /**
     * Validate a URL and return an error message if the URL targets a blocked address.
     *
     * @param url the URL to validate
     * @return empty if safe, error message if blocked
     */
    public static Optional<String> validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.of("Invalid URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.of("Missing URL scheme");
        }

        // Block non-HTTP schemes
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            return Optional.of("Blocked scheme: " + scheme + " (only http/https allowed)");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.of("Missing host in URL");
        }

        // Block localhost variants
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)
                || "[::1]".equals(host) || "0.0.0.0".equals(host)) {
            return Optional.of("Blocked: request to localhost/loopback address");
        }

        // Resolve hostname and check all IPs
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrReserved(addr)) {
                    return Optional.of("Blocked: " + host + " resolves to private/reserved address " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            return Optional.of("Cannot resolve host: " + host);
        }

        return Optional.empty();
    }

    /**
     * Check if an IP address is in a private or reserved range.
     */
    static boolean isPrivateOrReserved(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || isCloudMetadata(addr);
    }

    /**
     * Check for cloud metadata service addresses (169.254.169.254 and fd00::).
     */
    private static boolean isCloudMetadata(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        // IPv4: 169.254.169.254
        if (bytes.length == 4) {
            return (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254
                    && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254;
        }
        // IPv6: fc00::/7 (unique local addresses)
        if (bytes.length == 16) {
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
