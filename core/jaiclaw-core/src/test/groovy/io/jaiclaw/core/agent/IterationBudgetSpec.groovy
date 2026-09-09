package io.jaiclaw.core.agent

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IterationBudgetSpec extends Specification {

    def "counts down and refuses once exhausted"() {
        given:
        def budget = IterationBudget.of(3)

        expect:
        budget.remaining() == 3
        budget.consumed() == 0

        when:
        def results = (1..4).collect { budget.tryConsume() }

        then: "the fourth consume is refused"
        results == [true, true, true, false]
        budget.remaining() == 0
        budget.consumed() == 3
    }

    def "refund returns an iteration but never exceeds the original size"() {
        given:
        def budget = IterationBudget.of(2)

        when:
        budget.tryConsume()
        budget.refund()

        then:
        budget.remaining() == 2

        when: "refunding beyond the original size"
        budget.refund()
        budget.refund()

        then: "the budget is capped at its size"
        budget.remaining() == 2
    }

    def "unlimited budgets never run out and never warn"() {
        given:
        def budget = IterationBudget.unlimited()

        when:
        1000.times { budget.tryConsume() }

        then:
        budget.isUnlimited()
        budget.tryConsume()
        budget.ratioConsumed() == 0.0d
        !budget.warnAtRatio(0.5d)
    }

    def "non-positive sizes yield an unlimited budget rather than a deadlocked run"() {
        expect:
        IterationBudget.of(size).isUnlimited()

        where:
        size << [0, -1, -100]
    }

    def "ratioConsumed tracks consumption and warnAtRatio trips at the threshold"() {
        given:
        def budget = IterationBudget.of(10)

        when:
        9.times { budget.tryConsume() }

        then:
        budget.ratioConsumed() == 0.9d
        budget.warnAtRatio(0.9d)
        !budget.warnAtRatio(1.0d)
    }

    def "warnAtRatio ignores out-of-range thresholds"() {
        given:
        def budget = IterationBudget.of(4)
        4.times { budget.tryConsume() }

        expect:
        !budget.warnAtRatio(ratio)

        where:
        ratio << [0.0d, -0.5d, 1.5d]
    }

    def "forRun yields an independent counter so parent and child never share"() {
        given:
        def template = IterationBudget.of(5)

        when:
        def parent = template.forRun()
        def child = template.forRun()
        3.times { parent.tryConsume() }

        then: "consuming the parent's budget leaves the child's untouched"
        parent.remaining() == 2
        child.remaining() == 5
        template.remaining() == 5
    }

    def "concurrent consumers never over-issue iterations"() {
        given: "20 threads racing for 50 iterations"
        def budget = IterationBudget.of(50)
        def granted = new AtomicInteger()
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(20)

        when:
        20.times {
            pool.submit {
                start.await()
                10.times { if (budget.tryConsume()) granted.incrementAndGet() }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        then: "exactly the budget was handed out — no more, no less"
        granted.get() == 50
        budget.remaining() == 0
    }
}
