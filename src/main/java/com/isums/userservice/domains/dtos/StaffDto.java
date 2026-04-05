package com.isums.userservice.domains.dtos;

import java.util.UUID;

public record StaffDto(
         UUID id,
         String name,
         String email,
         String phoneNumber
) {
}
