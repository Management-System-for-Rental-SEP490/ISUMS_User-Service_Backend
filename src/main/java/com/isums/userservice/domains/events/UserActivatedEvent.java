package com.isums.userservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserActivatedEvent(
        UUID userId,
        String email,
        String name,
        String tempPassword,
        String firstRentPaymentUrl,
        Long firstRentAmount,
        Instant firstRentDueDate
) {
}
