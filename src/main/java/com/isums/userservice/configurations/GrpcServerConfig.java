package com.isums.userservice.configurations;

import io.grpc.ServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

import java.util.List;

@Configuration
public class GrpcServerConfig {

    @Bean
    public List<ServerInterceptor> globalInterceptors(GrpcJwtServerInterceptor jwt) {
        return List.of(jwt);
    }

    @Bean
    public AuthenticationProcessInterceptor grpcAuthenticationProcessInterceptor(GrpcSecurity grpc) throws Exception {
        return grpc
                .authorizeRequests(auth -> auth.allRequests().permitAll())
                .build();
    }
}
