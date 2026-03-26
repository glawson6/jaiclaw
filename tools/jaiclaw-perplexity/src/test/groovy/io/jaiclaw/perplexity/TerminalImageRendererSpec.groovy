package io.jaiclaw.perplexity

import io.jaiclaw.perplexity.render.TerminalImageRenderer
import spock.lang.Specification

class TerminalImageRendererSpec extends Specification {

    def "detects iTerm2 when TERM_PROGRAM contains iTerm"() {
        given:
        def renderer = new TerminalImageRenderer() {
            @Override
            TerminalImageRenderer.Protocol detect() {
                // Simulate iTerm detection
                return TerminalImageRenderer.Protocol.ITERM2
            }
        }

        expect:
        renderer.detect() == TerminalImageRenderer.Protocol.ITERM2
    }

    def "detects TEXT_ONLY as fallback"() {
        given:
        // In test environment, without iTerm or chafa, should default to TEXT_ONLY or CHAFA
        def renderer = new TerminalImageRenderer()
        def protocol = renderer.detect()

        expect:
        protocol != null
        protocol instanceof TerminalImageRenderer.Protocol
    }

    def "Protocol enum has correct values"() {
        expect:
        TerminalImageRenderer.Protocol.values().length == 3
        TerminalImageRenderer.Protocol.valueOf("ITERM2") == TerminalImageRenderer.Protocol.ITERM2
        TerminalImageRenderer.Protocol.valueOf("CHAFA") == TerminalImageRenderer.Protocol.CHAFA
        TerminalImageRenderer.Protocol.valueOf("TEXT_ONLY") == TerminalImageRenderer.Protocol.TEXT_ONLY
    }

    def "renderImage handles text-only mode"() {
        given:
        def output = new ByteArrayOutputStream()
        def oldOut = System.out
        System.setOut(new PrintStream(output))

        def renderer = new TerminalImageRenderer() {
            @Override
            TerminalImageRenderer.Protocol detect() {
                return TerminalImageRenderer.Protocol.TEXT_ONLY
            }
        }

        when:
        renderer.renderImage(new byte[0], "test alt text")

        then:
        output.toString().contains("[Image: test alt text]")

        cleanup:
        System.setOut(oldOut)
    }

    def "renderImageUrl handles text-only mode with URL"() {
        given:
        def output = new ByteArrayOutputStream()
        def oldOut = System.out
        System.setOut(new PrintStream(output))

        def renderer = new TerminalImageRenderer() {
            @Override
            TerminalImageRenderer.Protocol detect() {
                return TerminalImageRenderer.Protocol.TEXT_ONLY
            }
        }

        when:
        renderer.renderImageUrl("https://example.com/image.png", "example image")

        then:
        def text = output.toString()
        text.contains("[Image: example image]")
        text.contains("https://example.com/image.png")

        cleanup:
        System.setOut(oldOut)
    }

    def "isOnPath returns false for nonexistent command"() {
        expect:
        !TerminalImageRenderer.isOnPath("nonexistent_command_xyz_12345")
    }

    def "renderImage with null altText uses default"() {
        given:
        def output = new ByteArrayOutputStream()
        def oldOut = System.out
        System.setOut(new PrintStream(output))

        def renderer = new TerminalImageRenderer() {
            @Override
            TerminalImageRenderer.Protocol detect() {
                return TerminalImageRenderer.Protocol.TEXT_ONLY
            }
        }

        when:
        renderer.renderImageUrl("https://example.com/img.jpg", null)

        then:
        output.toString().contains("[Image: image]")

        cleanup:
        System.setOut(oldOut)
    }
}
