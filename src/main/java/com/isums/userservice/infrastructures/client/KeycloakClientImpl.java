package com.isums.userservice.infrastructures.client;

import com.isums.userservice.infrastructures.abstracts.KeycloakClient;
import com.isums.userservice.configurations.KeycloakProperties;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.KeycloakTokenResponse;
import com.isums.userservice.domains.dtos.KeycloakUserRepresentation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakClientImpl implements KeycloakClient {

    private final RestClient keycloakRestClient;
    private final KeycloakProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record CachedToken(String token, Instant expiresAt) {
    }

    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    private String getAccessToken() {
        CachedToken current = cached.get();
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return current.token;
        }

        synchronized (this) {
            current = cached.get();
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
                return current.token();
            }

            KeycloakTokenResponse token = requestAccessToken();
            Instant expiresAt = Instant.now().plusSeconds(token.expiresIn());
            String accessToken = token.accessToken();
            cached.set(new CachedToken(accessToken, expiresAt));
            return accessToken;
        }
    }

    private KeycloakTokenResponse requestAccessToken() {
        final String uri = "/realms/" + props.getRealm() + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());

        try {
            KeycloakTokenResponse body = keycloakRestClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);

            if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
                throw new IllegalStateException("Keycloak token response is empty");
            }
            return body;
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            throw new IllegalStateException("Keycloak token request failed: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText()
                    + (responseBody.isBlank() ? "" : "\n" + responseBody), ex);
        }
    }

    public String createUser(KeycloakCreateUserRequest req) {
                            Objects.requireNonNull(req, "KeycloakCreateUserRequest cannot be null");

                            final String uri = "/admin/realms/" + props.getRealm() + "/users";
                            String token = getAccessToken();

                            String firstName = req.name() != null && !req.name().isBlank() ? req.name().trim() : null;
                            KeycloakUserRepresentation payload = new KeycloakUserRepresentation(
                                    req.id() != null ? req.id() : null,
                                    req.email(),
                                    req.email(),
                                    firstName,
                                    req.isEnabled(),
                                    req.emailVerified() != null ? req.emailVerified() : true,
                                    mergeAttributes(req.attributes(), req.identityNumber(), req.name()),
                                    req.requiredActions()
                            );

                            try {
                                return keycloakRestClient.post()
                                        .uri(uri)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .headers(h -> h.setBearerAuth(token))
                                        .body(payload)
                                        .exchange((request, response) -> {
                                            int code = response.getStatusCode().value();

                                            if (code == 201) {
                                                URI location = response.getHeaders().getLocation();
                                                if (location == null) {
                                                    throw new IllegalStateException("Keycloak created user but missing Location header");
                                                }

                                                String path = location.getPath();
                                                return path.substring(path.lastIndexOf("/") + 1);
                                            }

                                            if (code == 409) {
                                                String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                                                log.warn("Keycloak createUser returned 409 conflict, recovering by lookup email={} body={}", req.email(), body);
                                                return findUserIdByEmail(req.email())
                                                        .orElseThrow(() -> new IllegalStateException(
                                                                "Keycloak 409 conflict but user not found by email=" + req.email()));
                                            }

                                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                                            throw new IllegalStateException("Keycloak create user failed: HTTP " + code
                                                    + (body.isBlank() ? "" : "\n" + body));
                    });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                log.warn("Keycloak createUser conflict (RestClientResponseException) email={}, recovering by lookup", req.email());
                return findUserIdByEmail(req.email())
                        .orElseThrow(() -> new IllegalStateException(
                                "Keycloak 409 conflict but user not found by email=" + req.email(), ex));
            }
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException(
                    "Keycloak admin call failed: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText()
                            + (body.isBlank() ? "" : "\n" + body), ex);
        }
    }

    @Override
    public Optional<String> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        String uri = UriComponentsBuilder
                .fromPath("/admin/realms/" + props.getRealm() + "/users")
                .queryParam("email", email)
                .queryParam("exact", "true")
                .build(true)
                .toUriString();
        String token = getAccessToken();
        try {
            String json = keycloakRestClient.get()
                    .uri(uri)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) return Optional.empty();
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray() || arr.isEmpty()) return Optional.empty();
            JsonNode id = arr.get(0).get("id");
            return id != null && id.isTextual() ? Optional.of(id.asText()) : Optional.empty();
        } catch (RestClientResponseException ex) {
            log.error("Keycloak findUserIdByEmail failed email={} status={} body={}",
                    email, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Keycloak findUserIdByEmail unexpected error email={}", email, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean isUserEnabled(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) return false;
        final String uri = "/admin/realms/" + props.getRealm() + "/users/" + keycloakId;
        String token = getAccessToken();
        try {
            String json = keycloakRestClient.get()
                    .uri(uri)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) return false;
            JsonNode node = objectMapper.readTree(json);
            JsonNode enabled = node.get("enabled");
            return enabled != null && enabled.asBoolean(false);
        } catch (RestClientResponseException ex) {
            log.error("Keycloak isUserEnabled failed keycloakId={} status={}",
                    keycloakId, ex.getStatusCode().value());
            return false;
        } catch (Exception ex) {
            log.error("Keycloak isUserEnabled unexpected error keycloakId={}", keycloakId, ex);
            return false;
        }
    }

    public void activeUser(String keycloakId) {
        final String uri = "/admin/realms/" + props.getRealm() + "/users/" + keycloakId;
        String token = getAccessToken();

        Map<String, Object> body = Map.of("enabled", true);

        try {
            keycloakRestClient.put()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(token))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak user activated keycloakId={}", keycloakId);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            throw new IllegalStateException(
                    "Keycloak activate user failed: HTTP " + ex.getStatusCode().value()
                            + (responseBody.isBlank() ? "" : "\n" + responseBody), ex);
        }
    }

    @Override
    public String activateAndResetPassword(String keycloakId) {
        activeUser(keycloakId);
        return resetPassword(keycloakId);
    }

    private static Map<String, List<String>> mergeAttributes(
            Map<String, List<String>> attrs,
            String identityNumber,
            String name
    ) {
        Map<String, List<String>> merged = new HashMap<>();
        if (attrs != null) merged.putAll(attrs);

        if (identityNumber != null && !identityNumber.isBlank()) {
            merged.put("identityNumber", List.of(identityNumber));
            merged.put("name", List.of(name));
        }

        return merged.isEmpty() ? null : merged;
    }

    public String resetPassword(String keycloakId) {
        String tempPassword = UUID.randomUUID().toString().substring(0, 8) + "@Aa1";
        final String uri = "/admin/realms/" + props.getRealm()
                + "/users/" + keycloakId + "/reset-password";
        String token = getAccessToken();

//        Map<String, Object> body = Map.of(
//                "type", "password",
//                "value", tempPassword,
//                "temporary", true
//        );

        Map<String, Object> body = Map.of(
                "type", "password",
                "value", tempPassword,
                "temporary", false
        );

        try {
            keycloakRestClient.put()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(token))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Keycloak password reset keycloakId={}", keycloakId);
            return tempPassword;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Reset password failed: HTTP "
                    + ex.getStatusCode().value(), ex);
        }
    }
}
