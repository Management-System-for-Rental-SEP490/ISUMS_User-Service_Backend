package com.isums.userservice.infrastructures.client;

import com.isums.userservice.configurations.KeycloakProperties;
import com.isums.userservice.domains.dtos.KeycloakCreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

@DisplayName("KeycloakClientImpl")
class KeycloakClientImplTest {

    private static final String TOKEN_RESPONSE_JSON = """
            {"access_token":"tkn-1","expires_in":300,"token_type":"Bearer"}
            """;

    private MockRestServiceServer server;
    private KeycloakClientImpl client;
    private KeycloakProperties props;

    @BeforeEach
    void setUp() {
        props = new KeycloakProperties();
        props.setBaseUrl("http://localhost:8080");
        props.setRealm("isums");
        props.setClientId("cid");
        props.setClientSecret("secret");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new KeycloakClientImpl(restClient, props);
    }

    private void expectTokenCall() {
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/realms/isums/protocol/openid-connect/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess(TOKEN_RESPONSE_JSON, MediaType.APPLICATION_JSON));
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("returns id parsed from Location header on 201")
        void createsUser() {
            String keycloakId = UUID.randomUUID().toString();
            URI location = URI.create("http://localhost:8080/admin/realms/isums/users/" + keycloakId);

            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users"))
                    .andExpect(method(POST))
                    .andExpect(header("Authorization", "Bearer tkn-1"))
                    .andRespond(withStatus(HttpStatus.CREATED).location(location));

            KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
                    null, "bob@b.com", true, true, "ID", "0900", "Bob",
                    Map.of(), List.of());

            assertThat(client.createUser(req)).isEqualTo(keycloakId);
            server.verify();
        }

        @Test
        @DisplayName("throws IllegalStateException when 201 without Location header")
        void missingLocation() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users"))
                    .andRespond(withStatus(HttpStatus.CREATED));

            KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
                    null, "bob@b.com", true, true, "ID", "0900", "Bob",
                    Map.of(), List.of());

            assertThatThrownBy(() -> client.createUser(req))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("Location");
        }

        @Test
        @DisplayName("throws IllegalStateException when response status is not 201")
        void nonCreatedStatus() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users"))
                    .andRespond(withStatus(HttpStatus.CONFLICT).body("{\"error\":\"dupe\"}"));

            KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
                    null, "bob@b.com", true, true, "ID", "0900", "Bob",
                    Map.of(), List.of());

            assertThatThrownBy(() -> client.createUser(req))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("409");
        }

        @Test
        @DisplayName("throws NullPointerException when request is null")
        void nullRequest() {
            assertThatThrownBy(() -> client.createUser(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("propagates token request failure as IllegalStateException")
        void tokenFailure() {
            server.expect(requestTo("http://localhost:8080/realms/isums/protocol/openid-connect/token"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("bad"));

            KeycloakCreateUserRequest req = new KeycloakCreateUserRequest(
                    null, "bob@b.com", true, true, "ID", "0900", "Bob",
                    Map.of(), List.of());

            assertThatThrownBy(() -> client.createUser(req))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("Keycloak token request failed");
        }
    }

    @Nested
    @DisplayName("activeUser")
    class ActiveUser {

        @Test
        @DisplayName("sends PUT with enabled=true and succeeds on 2xx")
        void activates() {
            String id = "kc-1";
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/" + id))
                    .andExpect(method(PUT))
                    .andExpect(header("Authorization", "Bearer tkn-1"))
                    .andRespond(withSuccess());

            client.activeUser(id);
            server.verify();
        }

        @Test
        @DisplayName("wraps 4xx in IllegalStateException")
        void failurePropagates() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/kc-1"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).body("missing"));

            assertThatThrownBy(() -> client.activeUser("kc-1"))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("404");
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("returns generated temp password on success")
        void resets() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/kc-1/reset-password"))
                    .andExpect(method(PUT))
                    .andRespond(withSuccess());

            String pwd = client.resetPassword("kc-1");
            assertThat(pwd).isNotBlank();
            assertThat(pwd).endsWith("@Aa1");
        }

        @Test
        @DisplayName("wraps 4xx in IllegalStateException")
        void failure() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/kc-1/reset-password"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatThrownBy(() -> client.resetPassword("kc-1"))
                    .isInstanceOf(java.lang.IllegalStateException.class)
                    .hasMessageContaining("Reset password failed");
        }
    }

    @Nested
    @DisplayName("activateAndResetPassword")
    class ActivateAndReset {

        @Test
        @DisplayName("activates user then resets password")
        void pipeline() {
            expectTokenCall();
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/kc-1"))
                    .andRespond(withSuccess());
            server.expect(requestTo("http://localhost:8080/admin/realms/isums/users/kc-1/reset-password"))
                    .andRespond(withSuccess());

            String pwd = client.activateAndResetPassword("kc-1");
            assertThat(pwd).isNotBlank();
            server.verify();
        }
    }
}
