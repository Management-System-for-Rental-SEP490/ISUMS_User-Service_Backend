package com.isums.userservice.infrastructures.grpc;

import com.isums.userservice.domains.entities.User;
import com.isums.userservice.grpc.*;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSerivceGrpcImpl extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUserById(GetUserByIdRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());

            User user = userRepository.findById(userId)
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

        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);

        } catch (Exception e) {
            log.error("getUserById failed, userId={}", request.getUserId(), e);

            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            String email = request.getEmail();
            User user = userRepository.findByEmail(email);

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId().toString())
                    .setName(user.getName())
                    .setEmail(user.getEmail())
                    .setIdentityNumber(user.getIdentityNumber())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("getUserByEmail failed, email={}", request.getEmail(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
