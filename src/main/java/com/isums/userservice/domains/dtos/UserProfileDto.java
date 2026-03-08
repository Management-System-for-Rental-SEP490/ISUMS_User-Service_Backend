package com.isums.userservice.domains.dtos;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserProfileDto(
        UUID id,
        String name,
        String email,
        String identityNumber,
        String phoneNumber,
        List<String> roles
) {}
