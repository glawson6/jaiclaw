package io.jaiclaw.compliance.gdpr

import io.jaiclaw.audit.AuditLogger
import io.jaiclaw.audit.TranscriptStore
import io.jaiclaw.core.gdpr.DataSubjectAliasResolver
import io.jaiclaw.core.gdpr.DataSubjectErasureSpi
import spock.lang.Specification

import java.time.Clock

/**
 * Erasure must reach every identifier a data subject's data is stored under.
 *
 * <p>Before 1.4.0 a request naming one channel id deleted only that channel's
 * data, leaving the same person's data on every other channel intact — which
 * does not satisfy Article 17.
 */
class ErasureAliasFanOutSpec extends Specification {

    def "erases across every linked identifier"() {
        given:
        def transcripts = Mock(TranscriptStore)
        def aliases = { tenant, subject -> ["12345", "U999", "subject-abc"] } as DataSubjectAliasResolver
        def spi = new AggregateDataSubjectErasureSpi(
                [transcripts], [], Clock.systemUTC(), aliases)

        when:
        def result = spi.eraseForDataSubject("acme", "12345",
                DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then: "one call per identifier, not one call total"
        1 * transcripts.eraseForDataSubject("acme", "12345") >> 2
        1 * transcripts.eraseForDataSubject("acme", "U999") >> 3
        1 * transcripts.eraseForDataSubject("acme", "subject-abc") >> 0

        and: "counts aggregate across all of them"
        result.transcriptsDeleted() == 5
    }

    def "defaults to the single supplied identifier"() {
        given:
        def transcripts = Mock(TranscriptStore)
        def spi = new AggregateDataSubjectErasureSpi([transcripts], [])

        when:
        spi.eraseForDataSubject("acme", "12345",
                DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then: "deployments without identity linking behave exactly as before"
        1 * transcripts.eraseForDataSubject("acme", "12345") >> 1
        0 * transcripts.eraseForDataSubject("acme", _ as String)
    }

    def "one failing identifier does not abort the others"() {
        given:
        def transcripts = Mock(TranscriptStore)
        def aliases = { tenant, subject -> ["good-1", "bad", "good-2"] } as DataSubjectAliasResolver
        def spi = new AggregateDataSubjectErasureSpi(
                [transcripts], [], Clock.systemUTC(), aliases)

        when:
        def result = spi.eraseForDataSubject("acme", "good-1",
                DataSubjectErasureSpi.ErasureReason.ART_17_REQUEST)

        then: "a partial erasure is bad; abandoning the rest of it is worse"
        1 * transcripts.eraseForDataSubject("acme", "good-1") >> 1
        1 * transcripts.eraseForDataSubject("acme", "bad") >> { throw new RuntimeException("boom") }
        1 * transcripts.eraseForDataSubject("acme", "good-2") >> 2
        result.transcriptsDeleted() == 3
    }
}
