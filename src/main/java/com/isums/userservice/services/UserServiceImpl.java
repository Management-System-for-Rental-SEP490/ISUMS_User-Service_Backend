package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.*;
import com.isums.userservice.abstracts.UserService;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.infrastructures.client.KeycloakClientImpl;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserQuery userQuery;
    private final KeycloakClientImpl keycloakClient;
    private final UserRepository userRepository;

    @Override
    public ApiResponse<List<UserDto>> getAllUsers() {
        List<UserDto> mapUsers = userQuery.getAllUsersCached();
        return ApiResponses.ok(mapUsers, "Fetched users successfully");
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
    public ApiResponse<String> createUser(KeycloakCreateUserRequest req) {
        if (req == null)
            throw new IllegalArgumentException("Request is required");
        if (req.email() == null || req.email().isBlank())
            throw new IllegalArgumentException("Email is required");
        if (req.identityNumber() == null || req.identityNumber().isBlank())
            throw new IllegalArgumentException("Identity number is required");

        boolean isExistEmail = userQuery.isEmailExists(req.email());
        if (isExistEmail) {
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        String keycloakUserId = keycloakClient.createUser(req);
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id");
        }

        User user = User.builder()
                .keycloakId(keycloakUserId)
                .email(req.email())
                .name(req.name())
                .identityNumber(req.identityNumber())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);

        return ApiResponses.created(keycloakUserId, "Created user successfully");
    }

    @Override
    public ApiResponse<UserDto> ensureUserExistsFromToken(Jwt jwt) {
        return null;
    }
}
