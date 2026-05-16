package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;

import java.util.Optional;

public interface KeycloakClient {
    String createUser(KeycloakCreateUserRequest req);

    String resetPassword(String keycloakId);

    void activeUser(String keycloakId);

    String activateAndResetPassword(String keycloakId);

    Optional<String> findUserIdByEmail(String email);

    boolean isUserEnabled(String keycloakId);
}
