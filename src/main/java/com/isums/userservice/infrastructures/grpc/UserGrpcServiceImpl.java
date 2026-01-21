package com.isums.userservice.infrastructures.grpc;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.grpc.GetUserRequest;
import com.isums.userservice.grpc.UserGrpcServiceGrpc;
import com.isums.userservice.grpc.UserResponse;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserGrpcServiceImpl extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUserById(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("User not found: " + request.getUserId())
                            .asRuntimeException());

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId().toString())
                    .setName(user.getName())
                    .setEmail(user.getEmail())
                    .setIdentityNumber(user.getIdentityNumber())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid UUID: " + request.getUserId())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
