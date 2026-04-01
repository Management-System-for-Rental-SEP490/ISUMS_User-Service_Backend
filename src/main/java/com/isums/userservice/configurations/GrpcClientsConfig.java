package com.isums.userservice.configurations;

import com.isums.houseservice.grpc.HouseServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientsConfig {

    @Bean
    HouseServiceGrpc.HouseServiceBlockingStub houseStub(GrpcChannelFactory channels, GrpcTokenInterceptor tokenInterceptor) {
        return HouseServiceGrpc.newBlockingStub(channels.createChannel("house"))
                .withInterceptors(tokenInterceptor);
    }
}
