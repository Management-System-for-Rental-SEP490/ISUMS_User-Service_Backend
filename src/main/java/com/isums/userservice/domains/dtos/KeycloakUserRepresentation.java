package com.isums.userservice.domains.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakUserRepresentation(
        @Nullable UUID id,
        String username,
        String email,
        String firstName,
        Boolean enabled,
        Boolean emailVerified,
        Map<String, List<String>> attributes,
        List<String> requiredActions
) {}
