package io.jclaw.memory

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.LocalDate

class DailyLogAppenderSpec extends Specification {

    @TempDir
    Path tempDir

    DailyLogAppender appender

    def setup() {
        appender = new DailyLogAppender(tempDir)
    }

    def "appends note to daily log file"() {
        given:
        def today = LocalDate.now()

        when:
        appender.append(today, "First note")

        then:
        def content = appender.readLog(today)
        content.contains("First note")
        content.contains("# " + today)
    }

    def "appends multiple notes to same day"() {
        given:
        def today = LocalDate.now()

        when:
        appender.append(today, "Note one")
        appender.append(today, "Note two")

        then:
        def content = appender.readLog(today)
        content.contains("Note one")
        content.contains("Note two")
    }

    def "readLog returns empty for nonexistent day"() {
        expect:
        appender.readLog(LocalDate.of(2020, 1, 1)) == ""
    }
}
