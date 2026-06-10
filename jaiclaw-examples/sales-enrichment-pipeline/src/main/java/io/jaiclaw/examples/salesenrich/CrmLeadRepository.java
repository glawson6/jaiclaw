package io.jaiclaw.examples.salesenrich;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory stub CRM. Holds a queue of un-enriched leads and a list of
 * already-enriched leads. Pre-seeded with five sample leads so {@code run-now}
 * has something to chew through immediately.
 */
@Component
public class CrmLeadRepository {

    public record Lead(String name, String company) {}

    private final ConcurrentLinkedDeque<Lead> queue = new ConcurrentLinkedDeque<>();
    private final List<String> enriched = new ArrayList<>();

    @PostConstruct
    void seed() {
        addLead("Aisha Sharma", "Nimbus Logistics");
        addLead("Carlos Mendes", "Riverstone Bank");
        addLead("Hyojin Park", "Beacon AI");
        addLead("Marcus Reilly", "GreenLeaf Foods");
        addLead("Priya Iyer", "Helix Robotics");
    }

    public void addLead(String name, String company) {
        queue.addLast(new Lead(name, company));
    }

    public Lead popNext() {
        return queue.pollFirst();
    }

    public int queueSize() {
        return queue.size();
    }

    public synchronized void recordEnriched(String summary) {
        enriched.add(summary);
    }

    public synchronized List<String> enriched() {
        return List.copyOf(enriched);
    }
}
