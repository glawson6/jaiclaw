package io.jclaw.channel.email

import io.jclaw.channel.ChannelMessageHandler
import spock.lang.Specification

class EmailAdapterSpec extends Specification {

    def "channelId is email"() {
        given:
        def adapter = new EmailAdapter(EmailConfig.DISABLED)

        expect:
        adapter.channelId() == "email"
        adapter.displayName() == "Email"
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new EmailConfig(null, null, 0, null, 0, null, null, false, 0, null)

        then:
        cfg.provider() == "imap"
        cfg.host() == ""
        cfg.port() == 993
        cfg.smtpHost() == ""
        cfg.smtpPort() == 587
        cfg.username() == ""
        cfg.password() == ""
        cfg.pollingInterval() == 60
        cfg.folders() == ["INBOX"] as String[]
    }

    def "config preserves custom values"() {
        when:
        def cfg = new EmailConfig("gmail", "imap.gmail.com", 993,
                "smtp.gmail.com", 587, "user@gmail.com", "secret",
                true, 120, ["INBOX", "Recruiting"] as String[])

        then:
        cfg.provider() == "gmail"
        cfg.host() == "imap.gmail.com"
        cfg.username() == "user@gmail.com"
        cfg.enabled()
        cfg.pollingInterval() == 120
        cfg.folders().length == 2
    }

    def "start and stop manage running state"() {
        given:
        def adapter = new EmailAdapter(EmailConfig.DISABLED)
        def handler = Mock(ChannelMessageHandler)

        expect:
        !adapter.isRunning()

        when:
        adapter.start(handler)

        then:
        adapter.isRunning()

        when:
        adapter.stop()

        then:
        !adapter.isRunning()
    }

    def "DISABLED config is not enabled"() {
        expect:
        !EmailConfig.DISABLED.enabled()
    }
}
