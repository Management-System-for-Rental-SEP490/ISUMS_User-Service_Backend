package com.isums.userservice.configurations;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrpcJwtServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ACTOR_USER_ID =
            Metadata.Key.of("actor-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ACTOR_ROLE =
            Metadata.Key.of("actor-role", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACEPARENT =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtDecoder jwtDecoder;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        setupMdc(headers, call.getMethodDescriptor().getFullMethodName());

        String auth = headers.get(AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.info("No token in gRPC call - passing through");
            return clearContextOnClose(next.startCall(call, headers));
        }

        String tokenValue = auth.substring("Bearer ".length()).trim();

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);

            AbstractAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            return clearContextOnClose(next.startCall(call, headers));

        } catch (Exception ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
            clear();
            return new ServerCall.Listener<>() {};
        }
    }

    private <ReqT> ServerCall.Listener<ReqT> clearContextOnClose(ServerCall.Listener<ReqT> delegate) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    clear();
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    clear();
                }
            }
        };
    }

    private void setupMdc(Metadata headers, String grpcMethod) {
        putIfPresent("requestId", headers.get(REQUEST_ID));
        putIfPresent("correlationId", headers.get(CORRELATION_ID));
        putIfPresent("userId", headers.get(ACTOR_USER_ID));
        putIfPresent("role", headers.get(ACTOR_ROLE));
        putIfPresent("grpcMethod", grpcMethod);
        String traceparent = headers.get(TRACEPARENT);
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4) {
                putIfPresent("traceId", parts[1]);
                putIfPresent("spanId", parts[2]);
            }
        }
    }

    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }
}
