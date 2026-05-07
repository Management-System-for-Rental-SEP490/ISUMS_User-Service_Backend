package com.isums.userservice.exceptions;

import com.isums.userservice.domains.dtos.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import javax.naming.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleNotFoundException returns 404 with message")
    void notFound() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleNotFoundException(new NotFoundException("no user"));

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody().getSuccess()).isFalse();
        assertThat(res.getBody().getMessage()).isEqualTo("no user");
    }

    @Test
    @DisplayName("handleIllegalStateException returns 500")
    void illegalState() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleIllegalStateException(new IllegalStateException("boom"));

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("handleBadRequest returns 400 with BAD_REQUEST error code")
    void badRequest() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadRequest(new IllegalArgumentException("bad arg"));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getErrors()).hasSize(1);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("handleConflict returns 409 with CONFLICT error code")
    void conflict() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleConflict(new ConflictException("dupe"));

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("handleRestClient mirrors upstream status when resolvable")
    void restClientResolvable() {
        RestClientResponseException ex =
                new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable");

        ResponseEntity<ApiResponse<Void>> res = handler.handleRestClient(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(422);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("UPSTREAM_ERROR");
    }

    @Test
    @DisplayName("handleRestClient falls back to 502 for non-standard status codes")
    void restClientFallbackBadGateway() {
        RestClientResponseException ex = new RestClientResponseException(
                "weird", HttpStatusCode.valueOf(599), "Server error", null, null, null);

        ResponseEntity<ApiResponse<Void>> res = handler.handleRestClient(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("handleDb returns 500 with DB_ERROR code and detail message")
    void db() {
        DataAccessException ex = new DataAccessException("top", new RuntimeException("root cause")) {};

        ResponseEntity<ApiResponse<Void>> res = handler.handleDb(ex);

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("DB_ERROR");
        assertThat(res.getBody().getErrors().get(0).getMessage()).isEqualTo("root cause");
    }

    @Test
    @DisplayName("handleGeneric returns 500 with sanitized message")
    void generic() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneric(new Exception("raw detail"));

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo("Unexpected error");
        assertThat(res.getBody().getErrors().get(0).getMessage())
                .isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("handleAccessDenied returns body with 403 status code")
    void accessDenied() {
        ApiResponse<Void> res = handler.handleAccessDenied(
                new AuthorizationDeniedException("denied"));

        assertThat(res.getStatusCode()).isEqualTo(403);
        assertThat(res.getSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("Access Denied");
    }

    @Test
    @DisplayName("handleUnauthorized returns body with 401 status code")
    void unauthorized() {
        ApiResponse<Void> res = handler.handleUnauthorized(new AuthenticationException("nope"));

        assertThat(res.getStatusCode()).isEqualTo(401);
        assertThat(res.getMessage()).isEqualTo("Unauthorized");
    }
}
