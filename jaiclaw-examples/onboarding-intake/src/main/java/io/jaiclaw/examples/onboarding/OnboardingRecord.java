package io.jaiclaw.examples.onboarding;

import java.time.Instant;
import java.util.List;

public record OnboardingRecord(
        String employeeId,
        String name,
        String email,
        String phone,
        int age,
        Instant completedAt,
        List<String> warnings
) {}
