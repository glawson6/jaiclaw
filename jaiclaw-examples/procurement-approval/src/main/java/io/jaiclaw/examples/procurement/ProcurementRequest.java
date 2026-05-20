package io.jaiclaw.examples.procurement;

import java.time.Instant;

public record ProcurementRequest(
        String requestId,
        String requester,
        String description,
        double amount,
        String vendor,
        String vendorEmail,
        String vendorPhone,
        String decision,
        String priority,
        String assignedApprover,
        Instant submittedAt,
        String status
) {}
