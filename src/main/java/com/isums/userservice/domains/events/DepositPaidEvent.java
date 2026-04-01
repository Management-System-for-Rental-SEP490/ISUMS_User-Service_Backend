package com.isums.userservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record DepositPaidEvent(
        UUID invoiceId,
        UUID contractId,
        UUID tenantId,
        UUID houseId,
        Long amount,
        String invoiceType,
        String txnNo,
        Instant paidAt,
        Long rentAmount,
        Integer payDate,
        Instant startAt,
        String tenantEmail,
        Boolean isNewAccount,
        String firstRentPaymentUrl,
        Long firstRentAmount,
        Instant firstRentDueDate
) {
}
