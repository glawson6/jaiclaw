package io.jaiclaw.tools.exec

import spock.lang.Specification
import spock.lang.Unroll

class SsrfGuardSpec extends Specification {

    def "allows normal public URLs"() {
        expect:
        SsrfGuard.validate("https://example.com/path").isEmpty()
        SsrfGuard.validate("http://api.github.com/repos").isEmpty()
    }

    @Unroll
    def "blocks localhost variants: #url"() {
        expect:
        SsrfGuard.validate(url).isPresent()

        where:
        url << [
            "http://localhost/admin",
            "http://127.0.0.1/admin",
            "http://0.0.0.0/admin",
            "http://[::1]/admin",
        ]
    }

    @Unroll
    def "blocks private IP ranges: #url"() {
        expect:
        SsrfGuard.validate(url).isPresent()

        where:
        url << [
            "http://10.0.0.1/internal",
            "http://172.16.0.1/internal",
            "http://192.168.1.1/internal",
            "http://169.254.169.254/latest/meta-data/",
        ]
    }

    def "blocks non-HTTP schemes"() {
        expect:
        SsrfGuard.validate("file:///etc/passwd").isPresent()
        SsrfGuard.validate("ftp://example.com/file").isPresent()
        SsrfGuard.validate("gopher://evil.com").isPresent()
    }

    def "blocks missing scheme"() {
        expect:
        SsrfGuard.validate("example.com").isPresent()
    }

    def "blocks invalid URLs"() {
        expect:
        SsrfGuard.validate("not a url at all").isPresent()
    }

    def "isPrivateOrReserved detects loopback"() {
        expect:
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("127.0.0.1"))
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("::1"))
    }

    def "isPrivateOrReserved detects site-local"() {
        expect:
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("10.0.0.1"))
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("172.16.0.1"))
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("192.168.1.1"))
    }

    def "isPrivateOrReserved detects link-local"() {
        expect:
        SsrfGuard.isPrivateOrReserved(InetAddress.getByName("169.254.1.1"))
    }

    def "isPrivateOrReserved allows public IP"() {
        expect:
        !SsrfGuard.isPrivateOrReserved(InetAddress.getByName("8.8.8.8"))
        !SsrfGuard.isPrivateOrReserved(InetAddress.getByName("93.184.216.34"))
    }
}
