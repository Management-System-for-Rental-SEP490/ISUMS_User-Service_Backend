package com.isums.userservice.services;

import com.isums.userservice.domains.dtos.*;
import com.isums.userservice.domains.entities.Role;
import com.isums.userservice.domains.entities.UserRole;
import com.isums.userservice.domains.entities.UserRoleId;
import com.isums.userservice.exceptions.NotFoundException;
import com.isums.userservice.infrastructures.abstracts.UserService;
import com.isums.userservice.domains.entities.User;
import com.isums.userservice.infrastructures.grpc.HouseGrpcClient;
import com.isums.userservice.infrastructures.mapper.UserMapper;
import com.isums.userservice.exceptions.ConflictException;
import com.isums.userservice.infrastructures.client.KeycloakClientImpl;
import com.isums.userservice.infrastructures.repositories.RoleRepository;
import com.isums.userservice.infrastructures.repositories.UserRepository;
import com.isums.userservice.infrastructures.repositories.UserRoleRepository;
import common.statics.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final KeycloakClientImpl keycloakClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserRoleCacheServiceImpl userRoleCacheServiceImpl;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final HouseGrpcClient houseGrpcClient;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allUsers", sync = true)
    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return userMapper.mapUsers(users);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "allUsers", allEntries = true)
    public String createUser(KeycloakCreateUserRequest req) {

        boolean isExistEmail = userRepository.existsByEmail(req.email());
        if (isExistEmail) {
            log.warn("Email already exists:  {}", req.email());
            throw new ConflictException("Email " + req.email() + " already exists");
        }

        String keycloakUserId = keycloakClient.createUser(req);
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalStateException("Keycloak did not return user id");
        }

        User user = User.builder()
                .id(req.id())
                .keycloakId(keycloakUserId)
                .email(req.email())
                .name(req.name())
                .identityNumber(req.identityNumber())
                .phoneNumber(req.phoneNumber())
                .isEnabled(req.isEnabled())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Role role = roleRepository.findByCode(Roles.TENANT)
                .orElseThrow(() -> new NotFoundException("Tenant role not found"));

        UserRoleId userRoleId = new UserRoleId(user.getId(), role.getId());

        UserRole userRole = UserRole.builder()
                .id(userRoleId)
                .user(user)
                .role(role)
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);
        log.info("User created: {}", user);
        userRoleRepository.save(userRole);
        log.info("User role created: {}", userRole);
        return keycloakUserId;
    }

    @Override
    @Transactional
    public void activeUser(UUID userId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (user.getIsEnabled()) {
            log.info("User already enabled userId={}, skip", userId);
            return;
        }

        keycloakClient.activeUser(user.getKeycloakId());

        user.setIsEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("User activated userId={}", userId);
    }

    @Override
    public void updateMainHouse(String keycloakId, UUID houseId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setMainHouseId(houseId);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    @Override
    @Cacheable(value = "userByEmail", key = "#email")
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email);

        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return userMapper.mapUser(user);
    }

    @Override
    public UserProfileDto getMe(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<String> roles = userRoleCacheServiceImpl.getRolesCached(keycloakId);

        if (user.getMainHouseId() == null) {
            var houses = houseGrpcClient.getAllHouseByUser(user.getId());
            if (houses.size() == 1) {
                user.setMainHouseId(UUID.fromString(houses.getFirst().getId()));
            }
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .identityNumber(user.getIdentityNumber())
                .mainHouseId(user.getMainHouseId())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .build();
    }
}
