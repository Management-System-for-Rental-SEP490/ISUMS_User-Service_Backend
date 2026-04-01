package com.isums.userservice.infrastructures.grpc;

import com.isums.houseservice.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HouseGrpcClient {

    private final HouseServiceGrpc.HouseServiceBlockingStub houseStub;

    public List<HouseResponse> getAllHouseByUser(UUID userId) {
        try {
            return houseStub.getAllHouseByUser(GetHouseByUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build()
            ).getHouseList();
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("[gRPC] getAllHouseByUser failed userId={}: {}", userId, e.getStatus());
            throw new RuntimeException("Fail to get house by user id: " + e.getStatus());
        }
    }
}