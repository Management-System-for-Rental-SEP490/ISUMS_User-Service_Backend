package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;

public interface KeycloakClient {
    String createUser(KeycloakCreateUserRequest req);

    String resetPassword(String keycloakId);

    void activeUser(String keycloakId);
}
