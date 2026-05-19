package io.jaiclaw.channel.telegram

import spock.lang.Specification

class TelegramPollingStrategyTypeSpec extends Specification {

    def "fromString parses valid values"() {
        expect:
        TelegramPollingStrategyType.fromString(input) == expected

        where:
        input     | expected
        "camel"   | TelegramPollingStrategyType.CAMEL
        "CAMEL"   | TelegramPollingStrategyType.CAMEL
        "Camel"   | TelegramPollingStrategyType.CAMEL
        "native"  | TelegramPollingStrategyType.NATIVE
        "NATIVE"  | TelegramPollingStrategyType.NATIVE
        "Native"  | TelegramPollingStrategyType.NATIVE
        null      | TelegramPollingStrategyType.NATIVE
        ""        | TelegramPollingStrategyType.NATIVE
        "  "      | TelegramPollingStrategyType.NATIVE
        "unknown" | TelegramPollingStrategyType.NATIVE
    }
}
