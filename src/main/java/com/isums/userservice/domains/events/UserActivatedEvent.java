package com.isums.userservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a tenant account is activated (Keycloak enabled=true) after
 * deposit payment. Carries the plaintext temp password so the activation
 * email can display it and the tenant can log in on first try — matches
 * the same pattern used for staff/manager account creation.
 */
@Builder
public record UserActivatedEvent(
        UUID userId,
        String email,
        String name,
        String password,
        String firstRentPaymentUrl,
        Long firstRentAmount,
        Instant firstRentDueDate
) {
}
