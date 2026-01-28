package com.isums.userservice.abstracts;

import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;

public interface KeycloakClient {
    String createUser(KeycloakCreateUserRequest req);
}
