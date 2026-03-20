package com.isums.userservice.infrastructures.grpc;

import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.grpc.*;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.services.UserRoleCacheServiceImpl;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSerivceGrpcImpl extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;
    private final UserRoleCacheServiceImpl userRoleCacheServiceImpl;
    private final UserService userService;

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
                    .setIsEnabled(user.getIsEnabled())
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

            if (user == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User not found with email: " + email)
                        .asRuntimeException());
                return;
            }

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId().toString())
                    .setName(user.getName())
                    .setEmail(user.getEmail())
                    .setIdentityNumber(user.getIdentityNumber())
                    .setIsEnabled(user.getIsEnabled())
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

    @Override
    public void getUserRoles(GetUserRolesRequest request, StreamObserver<GetUserRolesResponse> responseObserver) {
        try {
            String keycloakId = request.getKeycloakId();
            List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);
            GetUserRolesResponse response = GetUserRolesResponse.newBuilder()
                    .addAllRoles(roles)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("getUserRoles failed, keycloakId={}", request.getKeycloakId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
    @Override
    public void getUserIdAndRoleByKeyCloakId(GetUserIdAndRoleByKeyCloakIdRequest request, StreamObserver<UserResponse> responseObserver){
        try{
            String keycloakId = request.getKeycloakId();
            var profile = userService.getMe(keycloakId);

            UserResponse response = UserResponse.newBuilder()
                    .setId(profile.id().toString())
                    .setName(profile.name())
                    .setEmail(profile.email())
                    .setIdentityNumber(profile.identityNumber())
                    .setKeycloakId(keycloakId)
                    .addAllRoles(profile.roles())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("getUserRoles failed, keycloakId={}", request.getKeycloakId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getClass().getSimpleName())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
