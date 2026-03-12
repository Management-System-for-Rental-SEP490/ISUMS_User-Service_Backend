package com.isums.userservice.domains.dtos;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KeycloakCreateUserRequest(
        @Nullable UUID id,
        String email,
        Boolean isEnabled,
        Boolean emailVerified,
        String identityNumber,
        String phoneNumber,
        String name,
        Map<String, List<String>> attributes
) {}
