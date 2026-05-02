package com.isums.userservice.infrastructures.abstracts;

import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;

import java.util.List;

public interface KeycloakClient {
    String createUser(KeycloakCreateUserRequest req);

    String resetPassword(String keycloakId);

    void activeUser(String keycloakId);

    String activateAndResetPassword(String keycloakId);

    /**
     * Trigger Keycloak's "execute actions" email flow — sends a one-click
     * link to the user so they can perform required actions (typically
     * UPDATE_PASSWORD + VERIFY_EMAIL) before first login. Keycloak realm
     * SMTP must be configured for this to actually deliver.
     *
     * @param keycloakId  Keycloak user UUID
     * @param actions     required actions to perform (e.g. ["UPDATE_PASSWORD"])
     * @param lifespanSec link TTL in seconds (null = Keycloak default ≈ 12h)
     */
    void sendExecuteActionsEmail(String keycloakId, List<String> actions, Integer lifespanSec);
}
