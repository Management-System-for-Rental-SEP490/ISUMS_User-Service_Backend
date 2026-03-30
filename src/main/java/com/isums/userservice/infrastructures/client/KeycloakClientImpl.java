package com.isums.userservice.infrastructures.client;

import com.isums.userservice.infrastructures.abstracts.KeycloakClient;
import com.isums.userservice.configurations.KeycloakProperties;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import com.isums.userservice.domains.dtos.KeycloakTokenResponse;
import com.isums.userservice.domains.dtos.KeycloakUserRepresentation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakClientImpl implements KeycloakClient {

    private final RestClient keycloakRestClient;
    private final KeycloakProperties props;

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

        KeycloakUserRepresentation payload = new KeycloakUserRepresentation(
                req.id() != null ? req.id() : null,
                req.email(),
                req.email(),
                req.isEnabled(),
                req.emailVerified() != null ? req.emailVerified() : true,
                mergeAttributes(req.attributes(), req.identityNumber(), req.name()),
                List.of("UPDATE_PASSWORD")
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

                        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                        throw new IllegalStateException("Keycloak create user failed: HTTP " + code
                                + (body.isBlank() ? "" : "\n" + body));
                    });
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new IllegalStateException(
                    "Keycloak admin call failed: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText()
                            + (body.isBlank() ? "" : "\n" + body), ex);
        }
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
}
