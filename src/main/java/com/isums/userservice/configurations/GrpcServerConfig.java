package com.isums.userservice.configurations;

import io.grpc.ServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GrpcServerConfig {

    @Bean
    public List<ServerInterceptor> globalInterceptors(GrpcJwtServerInterceptor jwt) {
        return List.of(jwt);
    }
}
